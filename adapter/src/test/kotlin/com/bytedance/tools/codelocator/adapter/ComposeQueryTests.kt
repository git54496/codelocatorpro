package com.bytedance.tools.codelocator.adapter

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ComposeQueryTests {

    @Test
    fun `service resolves exact component hit and suffix render hit`() {
        withService(sampleComposeCaptureSnapshot(uniqueGrabId("compose_query_exact"))) { service, grabId ->
            val component = service.getComposeComponent(grabId, "7f0a1000:component:component.0")
            assertTrue(component.success)
            assertEquals("7f0a1000:component:component.0", component.data?.get("component_key"))

            val render = service.getComposeRender(grabId, "render:101")
            assertTrue(render.success)
            assertEquals("7f0a1000:render:render:101", render.data?.get("render_key"))
        }
    }

    @Test
    fun `service reports ambiguous semantics lookup across compose hosts`() {
        val snapshot = sampleComposeCaptureSnapshot(
            grabId = uniqueGrabId("compose_query_ambiguous"),
            hosts = listOf(
                composeHost(memAddr = "7f0a1000"),
                composeHost(memAddr = "7f0a2000")
            )
        )

        withService(snapshot) { service, grabId ->
            val result = service.getComposeNode(grabId, "101")
            assertTrue(!result.success)
            assertEquals("COMPOSE_NODE_AMBIGUOUS", result.error?.code)
            val candidates = result.error?.details?.get("candidates") as? List<*>
            assertNotNull(candidates)
            assertEquals(2, candidates.size)
        }
    }

    @Test
    fun `service falls back to legacy compose index when semantics capture is absent`() {
        withService(sampleLegacyComposeSnapshot(uniqueGrabId("compose_query_legacy"))) { service, grabId ->
            val result = service.getComposeNode(grabId, "cta_sem")
            assertTrue(result.success)
            assertEquals("7f0a1000:cta_sem", result.data?.get("compose_key"))
            assertEquals("cta_sem", result.data?.get("node_id"))
        }
    }

    @Test
    fun `service handles partial capture and missing links cleanly`() {
        withService(samplePartialComposeCaptureSnapshot(uniqueGrabId("compose_query_partial"))) { service, grabId ->
            val semantics = service.getComposeNode(grabId, "101")
            assertTrue(semantics.success)
            assertEquals("7f0a1000:semantics:101", semantics.data?.get("semantics_key"))

            val component = service.getComposeComponent(grabId, "component.0")
            assertTrue(!component.success)
            assertEquals("COMPONENT_NOT_FOUND", component.error?.code)

            val link = service.getComposeLink(grabId, "missing-link")
            assertTrue(!link.success)
            assertEquals("LINK_NOT_FOUND", link.error?.code)
        }
    }

    @Test
    fun `cli inspect commands support new compose queries`() {
        withService(sampleComposeCaptureSnapshot(uniqueGrabId("compose_cli"))) { service, grabId ->
            val viewerManager = ViewerManager(GrabStore(), AdbGateway(GrabStore()))

            val componentRun = captureStdout {
                AdapterCli.run(
                    arrayOf("inspect", "compose-component", "--grab-id", grabId, "--component-id", "component.0"),
                    service,
                    viewerManager
                )
            }
            assertEquals(0, componentRun.first)
            assertTrue(componentRun.second.contains("\"component_key\""))

            val renderRun = captureStdout {
                AdapterCli.run(
                    arrayOf("inspect", "compose-render", "--grab-id", grabId, "--render-id", "render:101"),
                    service,
                    viewerManager
                )
            }
            assertEquals(0, renderRun.first)
            assertTrue(renderRun.second.contains("\"render_key\""))

            val linkRun = captureStdout {
                AdapterCli.run(
                    arrayOf(
                        "inspect",
                        "compose-link",
                        "--grab-id",
                        grabId,
                        "--node-key",
                        "7f0a1000:link:component:component.0->render:render:101"
                    ),
                    service,
                    viewerManager
                )
            }
            assertEquals(0, linkRun.first)
            assertTrue(linkRun.second.contains("\"link_key\""))
        }
    }

    private fun withService(snapshot: GrabSnapshot, block: (AdapterService, String) -> Unit) {
        val store = GrabStore()
        val grabId = snapshot.meta.grabId
        val grabDir = Constants.grabsRoot.resolve(grabId)
        Files.createDirectories(grabDir)
        try {
            Files.writeString(grabDir.resolve("snapshot.json"), Jsons.toJson(snapshot))
            val adb = AdbGateway(store)
            val viewerManager = ViewerManager(store, adb)
            val service = AdapterService(store, adb, viewerManager)
            block(service, grabId)
        } finally {
            grabDir.toFile().deleteRecursively()
        }
    }

    private fun captureStdout(block: () -> Int): Pair<Int, String> {
        val originalOut = System.out
        val buffer = ByteArrayOutputStream()
        val printStream = PrintStream(buffer, true, Charsets.UTF_8.name())
        return try {
            System.setOut(printStream)
            block() to buffer.toString(Charsets.UTF_8.name())
        } finally {
            System.setOut(originalOut)
            printStream.close()
        }
    }

    private fun sampleComposeCaptureSnapshot(
        grabId: String,
        hosts: List<ViewNodeDto> = listOf(composeHost(memAddr = "7f0a1000"))
    ): GrabSnapshot {
        val indexes = hosts.associate { host ->
            host.memAddr to ViewIndexItem(memAddr = host.memAddr, className = host.className)
        }
        return GrabSnapshot(
            meta = GrabMeta(
                grabId = grabId,
                source = "file",
                packageName = "com.demo.app",
                activity = "MainActivity",
                grabTime = System.currentTimeMillis()
            ),
            uiTree = hosts,
            indexes = indexes
        )
    }

    private fun samplePartialComposeCaptureSnapshot(grabId: String): GrabSnapshot {
        val host = composeHost(
            memAddr = "7f0a1000",
            includeComponent = false,
            includeLinks = false
        )
        return GrabSnapshot(
            meta = GrabMeta(
                grabId = grabId,
                source = "file",
                packageName = "com.demo.app",
                activity = "MainActivity",
                grabTime = System.currentTimeMillis()
            ),
            uiTree = listOf(host),
            indexes = mapOf(host.memAddr to ViewIndexItem(memAddr = host.memAddr, className = host.className))
        )
    }

    private fun sampleLegacyComposeSnapshot(grabId: String): GrabSnapshot {
        val rootNode = ComposeNodeDto(
            nodeId = "root_sem",
            left = 10,
            top = 20,
            right = 210,
            bottom = 320,
            text = "Home",
            testTag = "screen_home",
            clickable = true,
            actions = listOf("CLICK"),
            children = listOf(
                ComposeNodeDto(
                    nodeId = "cta_sem",
                    left = 100,
                    top = 200,
                    right = 220,
                    bottom = 260,
                    text = "Pay Now",
                    testTag = "btn_pay",
                    clickable = true,
                    actions = listOf("CLICK")
                )
            )
        )
        return GrabSnapshot(
            meta = GrabMeta(
                grabId = grabId,
                source = "file",
                packageName = "com.demo.app",
                activity = "MainActivity",
                grabTime = System.currentTimeMillis()
            ),
            uiTree = listOf(
                ViewNodeDto(
                    memAddr = "7f0a1000",
                    className = "androidx.compose.ui.platform.ComposeView",
                    composeNodes = listOf(rootNode)
                )
            ),
            indexes = mapOf("7f0a1000" to ViewIndexItem(memAddr = "7f0a1000", className = "androidx.compose.ui.platform.ComposeView"))
        )
    }

    private fun composeHost(
        memAddr: String,
        includeComponent: Boolean = true,
        includeLinks: Boolean = true
    ): ViewNodeDto {
        val component = if (includeComponent) {
            listOf(
                ComposeComponentNodeDto(
                    componentId = "component.0",
                    displayName = "HomeScreen",
                    sourcePath = "app/src/main/java/com/demo/HomeScreen.kt",
                    sourceLine = 42,
                    pathResolution = "normalized"
                )
            )
        } else {
            emptyList()
        }
        val render = listOf(
            ComposeRenderNodeDto(
                renderId = "render:101",
                left = 10,
                top = 20,
                right = 210,
                bottom = 320,
                modifierSummary = "Modifier.padding",
                styleSummary = "text=Home",
                componentId = if (includeComponent) "component.0" else null
            )
        )
        val semantics = listOf(
            ComposeSemanticsNodeDto(
                semanticsId = "101",
                renderId = "render:101",
                componentId = if (includeComponent) "component.0" else null,
                legacyNodeId = "0",
                left = 10,
                top = 20,
                right = 210,
                bottom = 320,
                text = "Home",
                testTag = "screen_home",
                clickable = true,
                actions = listOf("CLICK")
            )
        )
        val links = if (includeLinks) {
            listOf(
                ComposeLinkDto("component", "render", "component.0", "render:101", 0.95, "layoutinfo_semantics_id"),
                ComposeLinkDto("render", "semantics", "render:101", "101", 1.0, "semantics_exact")
            )
        } else {
            emptyList()
        }
        return ViewNodeDto(
            memAddr = memAddr,
            className = "androidx.compose.ui.platform.ComposeView",
            composeCapture = ComposeCaptureDto(
                composeCaptureVersion = "1",
                componentTree = component,
                renderTree = render,
                semanticsTree = semantics,
                links = links
            )
        )
    }

    private fun uniqueGrabId(prefix: String): String {
        return "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(8)}"
    }
}

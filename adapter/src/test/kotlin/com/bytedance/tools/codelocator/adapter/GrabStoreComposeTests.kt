package com.bytedance.tools.codelocator.adapter

import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrabStoreComposeTests {

    @Test
    fun `getComposeIndex rebuilds index when compose index file is missing`() {
        val store = GrabStore()
        val grabId = "grab_compose_rebuild_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val grabDir = Constants.grabsRoot.resolve(grabId)
        Files.createDirectories(grabDir)
        try {
            val snapshot = sampleSnapshot(grabId)
            val root = Jsons.parseObject(Jsons.toJson(snapshot))
            root.remove("composeIndexes") // simulate old snapshot schema
            Files.writeString(grabDir.resolve("snapshot.json"), Jsons.toJson(root))

            val composeIndex = store.getComposeIndex(grabId)
            assertEquals(2, composeIndex.size)
            assertTrue(composeIndex.containsKey("7f0a1000:root_sem"))
            assertTrue(composeIndex.containsKey("7f0a1000:cta_sem"))
        } finally {
            grabDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `getComposeIndex uses compose index file when available`() {
        val store = GrabStore()
        val grabId = "grab_compose_file_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val grabDir = Constants.grabsRoot.resolve(grabId)
        Files.createDirectories(grabDir)
        try {
            val snapshot = sampleSnapshot(grabId)
            Files.writeString(grabDir.resolve("snapshot.json"), Jsons.toJson(snapshot))

            val fileIndex = mapOf(
                "custom:key" to ComposeIndexItem(
                    composeKey = "custom:key",
                    hostMemAddr = "custom",
                    nodeId = "key",
                    text = "from_file"
                )
            )
            Files.writeString(grabDir.resolve("compose_index.json"), Jsons.toJson(fileIndex))

            val composeIndex = store.getComposeIndex(grabId)
            assertEquals(1, composeIndex.size)
            assertEquals("from_file", composeIndex["custom:key"]?.text)
        } finally {
            grabDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `new compose capture indexes rebuild when files are missing`() {
        val store = GrabStore()
        val grabId = "grab_compose_capture_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val grabDir = Constants.grabsRoot.resolve(grabId)
        Files.createDirectories(grabDir)
        try {
            val snapshot = sampleComposeCaptureSnapshot(grabId)
            val root = Jsons.parseObject(Jsons.toJson(snapshot))
            root.remove("componentIndexes")
            root.remove("renderIndexes")
            root.remove("semanticsIndexes")
            root.remove("linkIndexes")
            Files.writeString(grabDir.resolve("snapshot.json"), Jsons.toJson(root))

            val componentIndex = store.getComponentIndex(grabId)
            val renderIndex = store.getRenderIndex(grabId)
            val semanticsIndex = store.getSemanticsIndex(grabId)
            val linkIndex = store.getLinkIndex(grabId)

            assertEquals(1, componentIndex.size)
            assertEquals(1, renderIndex.size)
            assertEquals(1, semanticsIndex.size)
            assertEquals(2, linkIndex.size)
            assertTrue(componentIndex.containsKey("7f0a1000:component:component.0"))
            assertTrue(renderIndex.containsKey("7f0a1000:render:render:101"))
            assertTrue(semanticsIndex.containsKey("7f0a1000:semantics:101"))
        } finally {
            grabDir.toFile().deleteRecursively()
        }
    }

    private fun sampleSnapshot(grabId: String): GrabSnapshot {
        val rootNode = ComposeNodeDto(
            nodeId = "root_sem",
            left = 10,
            top = 20,
            right = 210,
            bottom = 320,
            text = "Home",
            testTag = "screen_home",
            clickable = true,
            actions = listOf("CLICK", "FOCUS"),
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

    private fun sampleComposeCaptureSnapshot(grabId: String): GrabSnapshot {
        val component = ComposeComponentNodeDto(
            componentId = "component.0",
            displayName = "HomeScreen",
            sourcePath = "app/src/main/java/com/demo/HomeScreen.kt",
            sourceLine = 42,
            pathResolution = "normalized"
        )
        val render = ComposeRenderNodeDto(
            renderId = "render:101",
            left = 10,
            top = 20,
            right = 210,
            bottom = 320,
            modifierSummary = "Modifier.padding",
            styleSummary = "text=Home",
            componentId = "component.0"
        )
        val semantics = ComposeSemanticsNodeDto(
            semanticsId = "101",
            renderId = "render:101",
            componentId = "component.0",
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
        val capture = ComposeCaptureDto(
            composeCaptureVersion = "1",
            componentTree = listOf(component),
            renderTree = listOf(render),
            semanticsTree = listOf(semantics),
            links = listOf(
                ComposeLinkDto("component", "render", "component.0", "render:101", 0.95, "layoutinfo_semantics_id"),
                ComposeLinkDto("render", "semantics", "render:101", "101", 1.0, "semantics_exact")
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
                    composeCapture = capture
                )
            ),
            indexes = mapOf("7f0a1000" to ViewIndexItem(memAddr = "7f0a1000", className = "androidx.compose.ui.platform.ComposeView"))
        )
    }
}

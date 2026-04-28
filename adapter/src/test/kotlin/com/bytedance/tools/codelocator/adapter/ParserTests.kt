package com.bytedance.tools.codelocator.adapter

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParserTests {

    @Test
    fun `parse codeLocator file`() {
        val appJson = """
            {"bd":"com.demo.app","b7":{"af":"7f0a0000","ag":"MainActivity","cj":[{"af":"7f0a0001","ag":"android.widget.TextView","ac":"title","aq":"hello","d":10,"f":20,"e":110,"g":60,"a":[]}]}}
        """.trimIndent()
        val img = byteArrayOf(1, 2, 3, 4, 5)

        val data = ByteArrayOutputStream()
        val out = DataOutputStream(data)
        val tag = "CodeLocator".toByteArray()
        val version = "2.0.5".toByteArray()
        val app = appJson.toByteArray()

        out.writeInt(tag.size)
        out.write(tag)
        out.writeInt(version.size)
        out.write(version)
        out.writeInt(app.size)
        out.write(app)
        out.write(img)

        val file = Files.createTempFile("sample", ".codeLocator").toFile()
        file.writeBytes(data.toByteArray())

        val parsed = CodeLocatorFileParser.parse(file)
        assertEquals("2.0.5", parsed.version)
        assertTrue(parsed.appJson.contains("com.demo.app"))
        assertEquals(5, parsed.imageBytes.size)

        file.delete()
    }

    @Test
    fun `map snapshot tree and index`() {
        val appJson = """
            {"bd":"com.demo.app","b7":{"af":"7f0a0000","ag":"MainActivity","cj":[{"af":"7f0a0001","ag":"android.widget.FrameLayout","d":0,"f":0,"e":300,"g":600,"a":[{"af":"7f0a0002","ag":"android.widget.TextView","ac":"title","aq":"hello","d":10,"f":20,"e":110,"g":60,"a":[]}]}]}}
        """.trimIndent()
        val meta = GrabMeta("grab_x", "file", null, "com.demo.app", "MainActivity", System.currentTimeMillis(), null)

        val snapshot = SnapshotMapper.map(meta, appJson, "screenshot.png")
        assertEquals(1, snapshot.uiTree.size)
        assertEquals(2, snapshot.indexes.size)
        assertTrue(snapshot.indexes.containsKey("7f0a0002"))
        assertEquals(1, snapshot.activityStack.size)
        assertTrue(snapshot.activityStack.first().current)
    }

    @Test
    fun `map snapshot view translation`() {
        val appJson = """
            {"bd":"com.demo.app","b7":{"af":"7f0a0000","ag":"MainActivity","cj":[{"af":"7f0a0001","ag":"android.widget.FrameLayout","d":0,"f":0,"e":300,"g":600,"a":[{"af":"7f0a0002","ag":"android.widget.TextView","d":10,"f":20,"e":110,"g":60,"l":12.5,"m":-4.0,"a":[]}]}]}}
        """.trimIndent()
        val meta = GrabMeta("grab_translation", "file", null, "com.demo.app", "MainActivity", System.currentTimeMillis(), null)

        val snapshot = SnapshotMapper.map(meta, appJson, "screenshot.png")
        val translated = snapshot.uiTree.single().children.single()

        assertEquals(12.5, translated.translationX)
        assertEquals(-4.0, translated.translationY)
    }

    @Test
    fun `map snapshot compose tree and compose index`() {
        val appJson = """
            {"bd":"com.demo.app","b7":{"af":"7f0a0000","ag":"MainActivity","cj":[{"af":"7f0a1000","ag":"androidx.compose.ui.platform.ComposeView","d":0,"f":0,"e":300,"g":600,"b5":[{"a":"root_sem","b":10,"c":20,"d":210,"e":320,"f":"Home","g":"home_desc","h":"screen_home","i":1,"j":true,"q":["CLICK","FOCUS"],"r":[{"nodeId":"cta_sem","left":100,"top":200,"right":220,"bottom":260,"text":"Pay Now","contentDescription":"pay now","testTag":"btn_pay","clickable":true,"enabled":true,"actions":["CLICK"]}]}],"a":[]}]}}
        """.trimIndent()
        val meta = GrabMeta("grab_compose", "file", null, "com.demo.app", "MainActivity", System.currentTimeMillis(), null)

        val snapshot = SnapshotMapper.map(meta, appJson, "screenshot.png")
        assertEquals(1, snapshot.uiTree.size)
        assertEquals(1, snapshot.uiTree[0].composeNodes.size)
        assertEquals(2, snapshot.composeIndexes.size)

        val root = snapshot.composeIndexes["7f0a1000:root_sem"]
        assertNotNull(root)
        assertEquals("Home", root.text)
        assertEquals("screen_home", root.testTag)
        assertTrue(root.clickable)

        val cta = snapshot.composeIndexes["7f0a1000:cta_sem"]
        assertNotNull(cta)
        assertEquals("Pay Now", cta.text)
        assertEquals("btn_pay", cta.testTag)
        assertTrue(cta.actions.contains("CLICK"))
    }

    @Test
    fun `map snapshot compose capture tree and normalized indexes`() {
        val appJson = """
            {
              "bd":"com.demo.app",
              "b7":{
                "ag":"MainActivity",
                "cj":[
                  {
                    "af":"7f0a2000",
                    "ag":"androidx.compose.ui.platform.ComposeView",
                    "d":0,
                    "f":0,
                    "e":300,
                    "g":600,
                    "b6":{
                      "a":"1",
                      "b":[
                        {
                          "a":"component.0",
                          "b":"HomeScreen",
                          "c":"/workspace/demo/app/src/main/java/com/demo/HomeScreen.kt",
                          "e":42,
                          "f":7,
                          "g":1.0,
                          "h":false,
                          "j":"raw"
                        }
                      ],
                      "c":[
                        {
                          "a":"render:101",
                          "c":10,
                          "d":20,
                          "e":210,
                          "f":320,
                          "g":true,
                          "h":1.0,
                          "j":"Modifier.padding",
                          "k":"text=Home",
                          "l":"component.0"
                        }
                      ],
                      "d":[
                        {
                          "a":"101",
                          "b":"render:101",
                          "c":"component.0",
                          "d":"0",
                          "e":10,
                          "f":20,
                          "g":210,
                          "h":320,
                          "i":"Home",
                          "k":"screen_home",
                          "l":true,
                          "m":true,
                          "t":["CLICK"]
                        }
                      ],
                      "e":[
                        {"a":"component","b":"render","c":"component.0","d":"render:101","e":0.95,"f":"layoutinfo_semantics_id"},
                        {"a":"render","b":"semantics","c":"render:101","d":"101","e":1.0,"f":"semantics_exact"}
                      ]
                    },
                    "b5":[{"a":"0","f":"Home","h":"screen_home"}],
                    "a":[]
                  }
                ]
              }
            }
        """.trimIndent()
        val meta = GrabMeta("grab_compose_capture", "file", null, "com.demo.app", "MainActivity", System.currentTimeMillis(), null)

        val snapshot = SnapshotMapper.map(meta, appJson, "screenshot.png", "/workspace/demo")
        val capture = snapshot.uiTree.first().composeCapture
        assertNotNull(capture)
        assertEquals("1", capture.composeCaptureVersion)
        assertEquals(1, snapshot.componentIndexes.size)
        assertEquals(1, snapshot.renderIndexes.size)
        assertEquals(1, snapshot.semanticsIndexes.size)
        assertEquals(2, snapshot.linkIndexes.size)

        val component = snapshot.componentIndexes["7f0a2000:component:component.0"]
        assertNotNull(component)
        assertEquals("app/src/main/java/com/demo/HomeScreen.kt", component.sourcePath)
        assertEquals("normalized", component.pathResolution)

        val render = snapshot.renderIndexes["7f0a2000:render:render:101"]
        assertNotNull(render)
        assertEquals("Modifier.padding", render.modifierSummary)

        val semantics = snapshot.semanticsIndexes["7f0a2000:semantics:101"]
        assertNotNull(semantics)
        assertEquals("0", semantics.legacyNodeId)
    }

    @Test
    fun `map snapshot view visibility from serialized char values`() {
        val appJson = """
            {
              "bd":"com.demo.app",
              "b7":{
                "af":"7f0a0000",
                "ag":"MainActivity",
                "cj":[
                  {
                    "af":"7f0a0001",
                    "ag":"android.widget.FrameLayout",
                    "d":0,
                    "f":0,
                    "e":300,
                    "g":600,
                    "ab":"V",
                    "ae":0.75,
                    "a":[
                      {
                        "af":"7f0a0002",
                        "ag":"android.widget.TextView",
                        "ac":"title",
                        "aq":"hello",
                        "d":10,
                        "f":20,
                        "e":110,
                        "g":60,
                        "ab":"G",
                        "ae":0.25,
                        "a":[]
                      },
                      {
                        "af":"7f0a0003",
                        "ag":"android.widget.Button",
                        "d":20,
                        "f":80,
                        "e":160,
                        "g":140,
                        "ab":"I",
                        "ae":0.5,
                        "a":[]
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()
        val meta = GrabMeta("grab_visibility", "file", null, "com.demo.app", "MainActivity", System.currentTimeMillis(), null)

        val snapshot = SnapshotMapper.map(meta, appJson, "screenshot.png")
        val root = snapshot.uiTree.single()
        val gone = root.children.first { it.memAddr == "7f0a0002" }
        val invisible = root.children.first { it.memAddr == "7f0a0003" }

        assertTrue(root.visible)
        assertEquals(0.75, root.alpha)
        assertEquals(100, gone.width)
        assertEquals(40, gone.height)
        assertEquals(0.25, gone.alpha)
        assertEquals(false, gone.visible)
        assertEquals(0.5, invisible.alpha)
        assertEquals(false, invisible.visible)
    }

    @Test
    fun `map snapshot activity stack and fragment visibility`() {
        val appJson = """
            {
              "bd":"com.demo.app",
              "b7":{
                "af":"7f0a0001",
                "ag":"MainActivity",
                "cj":[{"af":"7f0a0100","ag":"android.widget.FrameLayout","d":0,"f":0,"e":300,"g":600,"a":[]}],
                "ck":[
                  {
                    "af":"7f0f0001",
                    "ag":"com.demo.HomeFragment",
                    "cc":"home",
                    "ad":101,
                    "cb":"7f0a0100",
                    "cd":true,
                    "ce":true,
                    "cf":true,
                    "cg":true,
                    "ch":false,
                    "ci":true
                  }
                ]
              },
              "c1":[
                {
                  "af":"7f0a0001",
                  "ag":"MainActivity",
                  "cl":"HomeActivity.kt:42",
                  "cm":true,
                  "cn":false,
                  "co":false,
                  "cp":false,
                  "ck":[
                    {
                      "af":"7f0f0001",
                      "ag":"com.demo.HomeFragment",
                      "cc":"home",
                      "ad":101,
                      "cb":"7f0a0100",
                      "cd":true,
                      "ce":true,
                      "cf":true,
                      "cg":true,
                      "ch":false,
                      "ci":true,
                      "a":[
                        {
                          "af":"7f0f0002",
                          "ag":"com.demo.HomeChildFragment",
                          "cc":"child",
                          "ad":102,
                          "cb":"7f0a0101",
                          "cd":true,
                          "ce":true,
                          "cf":true,
                          "cg":true,
                          "ch":false,
                          "ci":true
                        }
                      ]
                    }
                  ]
                },
                {
                  "af":"7f0a0002",
                  "ag":"DetailActivity",
                  "cl":"MainActivity.kt:88",
                  "cm":false,
                  "cn":true,
                  "co":true,
                  "cp":true,
                  "ck":[
                    {
                      "af":"7f0f0010",
                      "ag":"com.demo.DetailFragment",
                      "cc":"detail",
                      "ad":201,
                      "cb":"7f0a0200",
                      "cd":true,
                      "ce":true,
                      "cf":true,
                      "cg":true,
                      "ch":true,
                      "ci":false
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val meta = GrabMeta("grab_stack", "file", null, "com.demo.app", "MainActivity", System.currentTimeMillis(), null)

        val snapshot = SnapshotMapper.map(meta, appJson, "screenshot.png")
        assertEquals(2, snapshot.activityStack.size)
        val orderInfo = snapshot.activityStackOrderInfo
        assertNotNull(orderInfo)
        assertEquals("current_first_system_record_order", orderInfo.orderingMode)
        assertTrue(orderInfo.topActivityStable)
        assertTrue(orderInfo.backgroundActivitiesUnstable)
        assertTrue(orderInfo.warning.contains("Only the top activity"))

        val current = snapshot.activityStack[0]
        assertEquals("MainActivity", current.className)
        assertTrue(current.current)
        assertTrue(!current.covered)
        assertEquals(true, current.orderStable)
        assertEquals("HomeActivity.kt:42", current.startInfo)
        assertEquals(1, current.fragments.size)
        assertTrue(current.fragments.first().effectiveVisible)
        assertEquals(1, current.fragments.first().children.size)

        val covered = snapshot.activityStack[1]
        assertEquals("DetailActivity", covered.className)
        assertTrue(covered.covered)
        assertTrue(covered.stopped)
        assertEquals(false, covered.orderStable)
        assertEquals(1, covered.fragments.size)
        assertTrue(covered.fragments.first().coveredByTopActivity)
        assertTrue(!covered.fragments.first().effectiveVisible)
    }
}

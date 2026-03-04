package com.bytedance.tools.codelocator.adapter

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParserTests {

    @Test
    fun `parse codeLocator file`() {
        val appJson = """
            {"bd":"com.demo.app","b7":{"ag":"MainActivity","cj":[{"af":"7f0a0001","ag":"android.widget.TextView","ac":"title","aq":"hello","d":10,"f":20,"e":110,"g":60,"a":[]}]}}
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
            {"bd":"com.demo.app","b7":{"ag":"MainActivity","cj":[{"af":"7f0a0001","ag":"android.widget.FrameLayout","d":0,"f":0,"e":300,"g":600,"a":[{"af":"7f0a0002","ag":"android.widget.TextView","ac":"title","aq":"hello","d":10,"f":20,"e":110,"g":60,"a":[]}]}]}}
        """.trimIndent()
        val meta = GrabMeta("grab_x", "file", null, "com.demo.app", "MainActivity", System.currentTimeMillis(), null)

        val snapshot = SnapshotMapper.map(meta, appJson, "screenshot.png")
        assertEquals(1, snapshot.uiTree.size)
        assertEquals(2, snapshot.indexes.size)
        assertTrue(snapshot.indexes.containsKey("7f0a0002"))
    }
}

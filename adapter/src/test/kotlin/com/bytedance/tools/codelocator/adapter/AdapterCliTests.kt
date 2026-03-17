package com.bytedance.tools.codelocator.adapter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdapterCliTests {

    @Test
    fun `normalize command args maps empty args to live`() {
        assertEquals(listOf("live"), AdapterCli.normalizeCommandArgs(emptyList()))
    }

    @Test
    fun `normalize command args maps viewer shortcut to live command`() {
        assertEquals(listOf("live", "-v"), AdapterCli.normalizeCommandArgs(listOf("-v")))
    }

    @Test
    fun `normalize command args keeps version flag intact`() {
        assertEquals(listOf("--version"), AdapterCli.normalizeCommandArgs(listOf("--version")))
    }

    @Test
    fun `parse live command supports viewer shortcut`() {
        val parsed = AdapterCli.parseGrabCommand(AdapterCli.normalizeCommandArgs(listOf("-v")))
        assertEquals("live", parsed?.mode)
        assertTrue(parsed?.autoOpenViewer == true)
    }

    @Test
    fun `parse viewer shortcut with help resolves to help`() {
        val parsed = AdapterCli.parseGrabCommand(AdapterCli.normalizeCommandArgs(listOf("-v", "--help")))
        assertEquals("help", parsed?.mode)
    }

    @Test
    fun `parse live command supports device serial and viewer`() {
        val parsed = AdapterCli.parseGrabCommand(listOf("live", "--device-serial", "emulator-5554", "--viewer"))
        assertEquals("live", parsed?.mode)
        assertEquals("emulator-5554", parsed?.deviceSerial)
        assertTrue(parsed?.autoOpenViewer == true)
    }

    @Test
    fun `parse live command supports source root`() {
        val parsed = AdapterCli.parseGrabCommand(listOf("live", "--source-root", "/workspace/demo"))
        assertEquals("live", parsed?.mode)
        assertEquals("/workspace/demo", parsed?.sourceRoot)
    }

    @Test
    fun `parse file command supports path and viewer`() {
        val parsed = AdapterCli.parseGrabCommand(listOf("file", "--path", "/tmp/sample.codeLocator", "-v"))
        assertEquals("file", parsed?.mode)
        assertEquals("/tmp/sample.codeLocator", parsed?.path)
        assertTrue(parsed?.autoOpenViewer == true)
    }

    @Test
    fun `parse file command supports source root`() {
        val parsed = AdapterCli.parseGrabCommand(listOf("file", "--path", "/tmp/sample.codeLocator", "--source-root", "/workspace/demo"))
        assertEquals("file", parsed?.mode)
        assertEquals("/workspace/demo", parsed?.sourceRoot)
    }

    @Test
    fun `parse live command without viewer keeps auto open disabled`() {
        val parsed = AdapterCli.parseGrabCommand(listOf("live"))
        assertEquals("live", parsed?.mode)
        assertFalse(parsed?.autoOpenViewer == true)
    }
}

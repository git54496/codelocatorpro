package com.bytedance.tools.codelocator.adapter

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AdbGatewayTests {

    @Test
    fun `grab live targets current foreground package`() {
        val store = GrabStore()
        val runner = FakeCommandRunner(
            dumpsysOutput = """
                mCurrentFocus=Window{3b894ed u0 com.target.app/com.target.app.MainActivity}
                mFocusedApp=ActivityRecord{6586b76 u0 com.target.app/.MainActivity t1457}
            """.trimIndent(),
            broadcastPayload = encodedPayload(
                """
                {
                  "bd":"com.target.app",
                  "b7":{"ag":"com.target.app.MainActivity"}
                }
                """.trimIndent()
            )
        )
        val gateway = AdbGateway(store, runner::run)

        val result = gateway.grabUiState(null)

        try {
            assertTrue(result.success)
            assertEquals("com.target.app", result.data?.packageName)
            val broadcastCommand = runner.commands.first { it.joinToString(" ").contains(" shell am broadcast ") }
                .joinToString(" ")
            assertTrue(broadcastCommand.contains(" --receiver-registered-only "))
            assertTrue(broadcastCommand.endsWith(" com.target.app"))
        } finally {
            result.grabId?.let { Constants.grabsRoot.resolve(it).toFile().deleteRecursively() }
        }
    }

    @Test
    fun `grab live rejects responder from wrong package`() {
        val store = GrabStore()
        val runner = FakeCommandRunner(
            dumpsysOutput = """
                mCurrentFocus=Window{3b894ed u0 com.target.app/com.target.app.MainActivity}
                mFocusedApp=ActivityRecord{6586b76 u0 com.target.app/.MainActivity t1457}
            """.trimIndent(),
            broadcastPayload = encodedPayload(
                """
                {
                  "bd":"com.other.app",
                  "b7":{"ag":"com.other.app.MainActivity"}
                }
                """.trimIndent()
            )
        )
        val gateway = AdbGateway(store, runner::run)

        val error = assertFailsWith<AdapterException> {
            gateway.grabUiState(null)
        }

        assertEquals("TARGET_MISMATCH", error.code)
        assertEquals("com.target.app", error.details.get("expected_package"))
        assertEquals("com.other.app", error.details.get("actual_package"))
    }

    @Test
    fun `parse foreground package falls back to focused app`() {
        val gateway = AdbGateway(GrabStore()) { _, _ -> "" }

        val result = gateway.parseForegroundPackage(
            "mFocusedApp=ActivityRecord{6586b76 u0 com.demo.app/.MainActivity t1457}"
        )

        assertEquals("com.demo.app", result)
    }

    private class FakeCommandRunner(
        private val dumpsysOutput: String,
        private val broadcastPayload: String
    ) {
        val commands = mutableListOf<List<String>>()

        fun run(command: List<String>, timeoutMs: Long): String {
            commands += command
            val joined = command.joinToString(" ")
            return when {
                command.size >= 2 && command[1] == "devices" -> "List of devices attached\nSERIAL123\tdevice\n"
                joined.contains(" shell dumpsys window") -> dumpsysOutput
                joined.contains(" shell am broadcast ") -> "Broadcast completed: result=0, data=\"$broadcastPayload\"\n"
                joined.contains(" shell screencap ") -> ""
                joined.contains(" pull ") -> ""
                else -> error("Unexpected command: $joined timeout=$timeoutMs")
            }
        }
    }

    private fun encodedPayload(appJson: String): String {
        val wrapper = """{"data":$appJson}"""
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).bufferedWriter(Charsets.UTF_8).use { it.write(wrapper) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray())
    }
}

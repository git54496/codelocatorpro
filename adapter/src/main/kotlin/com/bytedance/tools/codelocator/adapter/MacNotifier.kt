package com.bytedance.tools.codelocator.adapter

import java.util.concurrent.TimeUnit

object MacNotifier {
    fun notify(title: String, message: String) {
        runCatching {
            val script = "display notification \"${message.replace("\"", "\\\"")}\" with title \"${title.replace("\"", "\\\"")}\""
            val process = ProcessBuilder("osascript", "-e", script)
                .redirectErrorStream(true)
                .start()
            process.waitFor(2, TimeUnit.SECONDS)
        }
    }
}

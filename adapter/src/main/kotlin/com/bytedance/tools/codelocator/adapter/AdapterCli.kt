package com.bytedance.tools.codelocator.adapter

object AdapterCli {
    fun run(args: Array<String>, service: AdapterService, viewerManager: ViewerManager): Int {
        if (args.isEmpty()) {
            printUsage()
            return 0
        }

        return try {
            when (args[0]) {
                "mcp" -> {
                    McpStdioServer(service).run()
                    0
                }

                "grab" -> runGrab(args.drop(1), service)
                "grabs" -> runGrabs(args.drop(1), service)
                "viewer" -> runViewer(args.drop(1), service, viewerManager)
                "inspect" -> runInspect(args.drop(1), service)
                else -> {
                    printUsage()
                    1
                }
            }
        } catch (e: AdapterException) {
            println(Jsons.toJson(ToolResult<Any>(success = false, error = McpError(e.code, e.message, e.details))))
            1
        } catch (t: Throwable) {
            println(Jsons.toJson(ToolResult<Any>(success = false, error = McpError("INTERNAL_ERROR", t.message ?: "error"))))
            1
        }
    }

    private fun runGrab(args: List<String>, service: AdapterService): Int {
        if (args.isEmpty()) {
            printUsage()
            return 1
        }
        return when (args[0]) {
            "live" -> {
                val device = readOption(args, "--device-serial")
                val result = service.grabLive(device)
                println(Jsons.toJson(result))
                if (result.success) 0 else 1
            }

            "file" -> {
                val path = readOption(args, "--path")
                val result = service.grabFromFile(path)
                println(Jsons.toJson(result))
                if (result.success) 0 else 1
            }

            else -> {
                printUsage()
                1
            }
        }
    }

    private fun runGrabs(args: List<String>, service: AdapterService): Int {
        if (args.firstOrNull() != "list") {
            printUsage()
            return 1
        }
        val result = service.listGrabs()
        println(Jsons.toJson(result))
        return if (result.success) 0 else 1
    }

    private fun runViewer(args: List<String>, service: AdapterService, viewerManager: ViewerManager): Int {
        if (args.isEmpty()) {
            printUsage()
            return 1
        }
        return when (args[0]) {
            "open" -> {
                val grabId = readOption(args, "--grab-id")
                val result = service.openViewer(grabId)
                println(Jsons.toJson(result))
                if (result.success) 0 else 1
            }

            "serve" -> {
                val port = readOption(args, "--port")?.toIntOrNull() ?: 17333
                viewerManager.serve(port)
                0
            }

            else -> {
                printUsage()
                1
            }
        }
    }

    private fun runInspect(args: List<String>, service: AdapterService): Int {
        if (args.isEmpty()) {
            printUsage()
            return 1
        }
        return when (args[0]) {
            "view-data" -> {
                val grabId = requireOpt(args, "--grab-id")
                val memAddr = requireOpt(args, "--mem-addr")
                val result = service.getViewData(grabId, memAddr)
                println(Jsons.toJson(result))
                if (result.success) 0 else 1
            }

            "class-info" -> {
                val grabId = requireOpt(args, "--grab-id")
                val memAddr = requireOpt(args, "--mem-addr")
                val result = service.getViewClassInfo(grabId, memAddr)
                println(Jsons.toJson(result))
                if (result.success) 0 else 1
            }

            "touch" -> {
                val grabId = requireOpt(args, "--grab-id")
                val result = service.traceTouch(grabId)
                println(Jsons.toJson(result))
                if (result.success) 0 else 1
            }

            else -> {
                printUsage()
                1
            }
        }
    }

    private fun readOption(args: List<String>, key: String): String? {
        val idx = args.indexOf(key)
        if (idx < 0 || idx + 1 >= args.size) return null
        return args[idx + 1]
    }

    private fun requireOpt(args: List<String>, key: String): String {
        return readOption(args, key) ?: throw AdapterException("INVALID_ARGUMENT", "Missing option: $key")
    }

    private fun printUsage() {
        println(
            """
            codelocator-adapter usage:
              codelocator-adapter mcp
              codelocator-adapter grab live --device-serial <optional> --json
              codelocator-adapter grab file --path <optional> --json
              codelocator-adapter grabs list --json
              codelocator-adapter viewer open --grab-id <id> --json
              codelocator-adapter viewer serve --port <port>
              codelocator-adapter inspect view-data --grab-id <id> --mem-addr <addr> --json
              codelocator-adapter inspect class-info --grab-id <id> --mem-addr <addr> --json
              codelocator-adapter inspect touch --grab-id <id> --json
            """.trimIndent()
        )
    }
}

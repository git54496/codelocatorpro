package com.bytedance.tools.codelocator.adapter

fun main(args: Array<String>) {
    val store = GrabStore()
    val adb = AdbGateway(store)
    val viewer = ViewerManager(store, adb)
    val service = AdapterService(store, adb, viewer)
    val code = AdapterCli.run(args, service, viewer)
    if (code != 0) {
        System.exit(code)
    }
}

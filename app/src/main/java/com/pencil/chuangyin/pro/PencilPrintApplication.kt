package com.pencil.chuangyin.pro

import android.app.Application
import com.pencil.chuangyin.core.proxy.engine.ProxyEngine
import com.pencil.chuangyin.printer.PrinterAutoConnect
import dagger.hilt.android.HiltAndroidApp
import java.net.ProxySelector
import javax.inject.Inject

@HiltAndroidApp
class PencilPrintApplication : Application() {

    @Inject
    lateinit var autoConnect: PrinterAutoConnect

    @Inject
    lateinit var proxyEngine: ProxyEngine

    override fun onCreate() {
        super.onCreate()
        // 应用内代理：安装进程级 ProxySelector（只影响本应用，不影响系统与其他应用）。
        // 关键客户端（P 站 / AI）另有显式安装，不依赖这里的初始化顺序。
        ProxySelector.setDefault(proxyEngine.selector)
        proxyEngine.start()
        // 自动连接随应用启动常驻：任何页面、任何操作期间都在后台静默重连。
        autoConnect.start()
    }
}

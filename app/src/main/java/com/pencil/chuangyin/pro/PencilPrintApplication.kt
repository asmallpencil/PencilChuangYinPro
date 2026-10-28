package com.pencil.chuangyin.pro

import android.app.Application
import com.pencil.chuangyin.printer.PrinterAutoConnect
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PencilPrintApplication : Application() {

    @Inject
    lateinit var autoConnect: PrinterAutoConnect

    override fun onCreate() {
        super.onCreate()
        // 自动连接随应用启动常驻：任何页面、任何操作期间都在后台静默重连。
        autoConnect.start()
    }
}

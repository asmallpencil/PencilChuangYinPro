package com.pencil.chuangyin.printer

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * core:printer 依赖绑定：将打印机相关接口绑定到蓝牙实现。
 * 上层只依赖 PrinterController / PrinterScanner 等接口。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PrinterModule {

    @Binds
    abstract fun bindPrinterConnection(impl: BluetoothPrinterConnection): PrinterConnection

    @Binds
    abstract fun bindPrinterScanner(impl: BluetoothPrinterScanner): PrinterScanner

    @Binds
    abstract fun bindRasterEncoder(impl: DefaultRasterEncoder): RasterEncoder

    @Binds
    abstract fun bindImageBinarizer(impl: DefaultImageBinarizer): ImageBinarizer

    @Binds
    abstract fun bindImagePreparer(impl: DefaultImagePreparer): ImagePreparer

    @Binds
    abstract fun bindTextBitmapRenderer(impl: AndroidTextBitmapRenderer): TextBitmapRenderer

    @Binds
    abstract fun bindPrinterController(impl: DefaultPrinterController): PrinterController

    @Binds
    abstract fun bindPrinterSettings(impl: DataStorePrinterSettings): PrinterSettings
}
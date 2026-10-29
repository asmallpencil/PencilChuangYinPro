package com.pencil.chuangyin.core.scanner

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** core:scanner Hilt 绑定：算法实现面向 [DocumentScanner] 接口注入。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScannerModule {

    @Binds
    @Singleton
    abstract fun bindDocumentScanner(impl: OpenCvDocumentScanner): DocumentScanner

    /** 二维码解码（代理配置导入用）。 */
    @Binds
    @Singleton
    abstract fun bindQrCodeDecoder(impl: OpenCvQrCodeDecoder): QrCodeDecoder
}

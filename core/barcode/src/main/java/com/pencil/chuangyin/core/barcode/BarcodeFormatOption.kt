package com.pencil.chuangyin.core.barcode

import com.google.zxing.BarcodeFormat

/** 条码分组：用于界面上分区展示与默认高度推导。 */
enum class BarcodeGroup {
    /** 一维码（按行扫描，默认固定高度）。 */
    OneDimensional,

    /** 二维矩阵码（正方形，内容自适应）。 */
    TwoDimensional,
}

/**
 * 支持的条码格式全集：覆盖 ZXing 中全部可由 [com.google.zxing.MultiFormatWriter] 生成的格式。
 *
 * 名称与 ZXing [BarcodeFormat] 枚举严格一致（AI 工具的参数值也沿用该名称），
 * 界面展示使用中文/通用名 + 分组。
 */
enum class BarcodeFormatOption(
    val displayName: String,
    val group: BarcodeGroup,
) {
    UPC_A("UPC-A", BarcodeGroup.OneDimensional),
    UPC_E("UPC-E", BarcodeGroup.OneDimensional),
    EAN_8("EAN-8", BarcodeGroup.OneDimensional),
    EAN_13("EAN-13", BarcodeGroup.OneDimensional),
    CODE_39("Code 39", BarcodeGroup.OneDimensional),
    CODE_93("Code 93", BarcodeGroup.OneDimensional),
    CODE_128("Code 128", BarcodeGroup.OneDimensional),
    CODABAR("Codabar", BarcodeGroup.OneDimensional),
    ITF("ITF 交叉二五码", BarcodeGroup.OneDimensional),
    RSS_14("RSS-14", BarcodeGroup.OneDimensional),
    RSS_EXPANDED("RSS Expanded", BarcodeGroup.OneDimensional),
    QR_CODE("QR 码", BarcodeGroup.TwoDimensional),
    DATA_MATRIX("Data Matrix", BarcodeGroup.TwoDimensional),
    AZTEC("Aztec", BarcodeGroup.TwoDimensional),
    PDF_417("PDF417", BarcodeGroup.TwoDimensional);

    /** 对应 ZXing 格式。 */
    val zxingFormat: BarcodeFormat get() = BarcodeFormat.valueOf(name)

    /** 是否二维矩阵码。 */
    val isTwoDimensional: Boolean get() = group == BarcodeGroup.TwoDimensional

    companion object {
        /** 首页折叠态默认展示的 6 个常用格式。 */
        val COMMON: List<BarcodeFormatOption> = listOf(
            QR_CODE,
            CODE_128,
            EAN_13,
            CODE_39,
            EAN_8,
            UPC_A,
        )

        /** 按名称解析（大小写不敏感，兼容 AI 传入的小写格式名）。 */
        fun fromName(raw: String?): BarcodeFormatOption? {
            val name = raw?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == name }
        }
    }
}

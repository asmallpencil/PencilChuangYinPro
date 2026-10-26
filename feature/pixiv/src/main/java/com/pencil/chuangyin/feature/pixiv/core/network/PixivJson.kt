package com.pencil.chuangyin.feature.pixiv.core.network

import kotlinx.serialization.json.Json

/** 与 P 站接口容错对齐的 Json（同名 ServiceFactory.gson 行为）。 */
val PixivJson: Json = Json {
    encodeDefaults = true
    explicitNulls = false
    isLenient = true
    coerceInputValues = true
    ignoreUnknownKeys = true
    allowSpecialFloatingPointValues = true
}

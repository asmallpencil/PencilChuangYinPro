package com.pencil.chuangyin.feature.pixiv.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PixivOAuthClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PixivApiClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PixivImageClient

package com.pencil.chuangyin.feature.ai.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** feature:ai 的数据层绑定。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiDataModule {

    @Binds
    @Singleton
    abstract fun bindConversationStore(impl: FileConversationStore): ConversationStore
}

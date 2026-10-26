package com.pencil.chuangyin.pro

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 首次启动引导完成标记：决定 NavHost 的起始目的地。
 * 用 SharedPreferences 轻量实现，避免引入额外 DataStore 依赖（core:theme 已用 DataStore 管主题）。
 */
@Singleton
class OnboardingFlagRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasCompletedOnboarding(): Boolean = prefs.getBoolean(KEY_COMPLETED, false)

    fun markCompleted() {
        prefs.edit().putBoolean(KEY_COMPLETED, true).apply()
    }

    fun reset() {
        prefs.edit().remove(KEY_COMPLETED).apply()
    }

    private companion object {
        const val PREFS_NAME = "pencil_print_flags"
        const val KEY_COMPLETED = "onboarding_completed"
    }
}
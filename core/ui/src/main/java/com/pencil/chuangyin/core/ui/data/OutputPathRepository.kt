package com.pencil.chuangyin.core.ui.data

import android.content.Context
import android.os.Environment
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 产物输出路径（全局设置）。
 *
 * 该设置属于「软件设置」而非 P 站设置，同时影响 P 站下载、文字打印导出、图片打印导出三处输出。
 * 默认输出目录为公共图片目录下的 [OUTPUT_DIR_DEFAULT]（即 /storage/emulated/0/Pictures/PencilChuangyinPro/）。
 *
 * 由于 targetSdk=35 且未申请存储权限，Android 11+ 无法直接写公共 Pictures 目录，
 * 因此对外暴露两种目标：
 * - [relativeMediaStorePath]：MediaStore（Q+）可用的相对路径，如 Pictures/PencilChuangyinPro。
 * - [legacyPublicDir]：API 29 及以下可用的真实目录（getExternalStoragePublicDirectory(Pictures)）。
 */
interface OutputPathRepository {

    /** 输出目录名状态流（如 PencilChuangyinPro），供 UI 订阅 / MediaStore 相对路径派生。 */
    val outputDirName: StateFlow<String>

    /** 设置输出目录名。为空白时回退为默认目录名。 */
    suspend fun setOutputDirName(name: String)

    /** 同步读取当前输出目录名。 */
    fun currentOutputDirName(): String

    /**
     * 用户自定义的完整绝对输出路径（如 /storage/emulated/0/MyFolder/Sub）。
     * 未设置时为 ""，表示使用默认目录。
     */
    val outputPath: StateFlow<String>

    /** 设置完整绝对输出路径；清空（空白）表示回到默认目录。 */
    suspend fun setOutputPath(path: String)

    /**
     * 当前可用的真实文件系统输出目录：
     * 设置了完整绝对路径时返回该路径；否则返回公共 Pictures 下的默认目录。
     */
    fun activeOutputDirFile(): File

    /** MediaStore（Q+）可用的相对路径，如 Pictures/PencilChuangyinPro。 */
    fun relativeMediaStorePath(): String

    /** API 29 及以下可用的公共输出目录（与 activeOutputDirFile 在未设自定义路径时一致）。 */
    fun legacyPublicDir(): File
}

/** 默认输出目录名。 */
const val OUTPUT_DIR_DEFAULT = "PencilChuangyinPro"

@Singleton
class DataStoreOutputPathRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : OutputPathRepository {

    private val _outputDirName = MutableStateFlow(OUTPUT_DIR_DEFAULT)
    override val outputDirName: StateFlow<String> = _outputDirName.asStateFlow()

    private val _outputPath = MutableStateFlow("")
    override val outputPath: StateFlow<String> = _outputPath.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        runCatching {
            val first = runBlocking {
                context.outputPathDataStore.data
                    .map { pref ->
                        OutputPref(
                            dirName = pref[KEY_OUTPUT_DIR] ?: OUTPUT_DIR_DEFAULT,
                            path = pref[KEY_OUTPUT_PATH] ?: "",
                        )
                    }
                    .first()
            }
            _outputDirName.value = sanitize(first.dirName)
            _outputPath.value = sanitizePath(first.path)
        }
        scope.launch {
            context.outputPathDataStore.data
                .map { pref ->
                    OutputPref(
                        dirName = pref[KEY_OUTPUT_DIR] ?: OUTPUT_DIR_DEFAULT,
                        path = pref[KEY_OUTPUT_PATH] ?: "",
                    )
                }
                .collect { pref ->
                    _outputDirName.value = sanitize(pref.dirName)
                    _outputPath.value = sanitizePath(pref.path)
                }
        }
    }

    override suspend fun setOutputDirName(name: String) {
        val safe = sanitize(name)
        _outputDirName.value = safe
        context.outputPathDataStore.edit { it[KEY_OUTPUT_DIR] = safe }
    }

    override suspend fun setOutputPath(path: String) {
        val safe = sanitizePath(path)
        _outputPath.value = safe
        context.outputPathDataStore.edit { it[KEY_OUTPUT_PATH] = safe }
    }

    override fun currentOutputDirName(): String = _outputDirName.value

    override fun activeOutputDirFile(): File {
        val p = _outputPath.value
        return if (p.isNotBlank()) File(p) else legacyPublicDir()
    }

    override fun relativeMediaStorePath(): String = "${Environment.DIRECTORY_PICTURES}/${_outputDirName.value.limitLength()}"

    override fun legacyPublicDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), _outputDirName.value.limitLength())

    private fun sanitize(name: String): String {
        val trimmed = name.trim().trim('\\', '/')
        return if (trimmed.isEmpty()) OUTPUT_DIR_DEFAULT else trimmed
    }

    private fun sanitizePath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return ""
        val stripped = trimmed.trim { it == '/' || it == '\\' }
        return if (stripped.isEmpty()) "" else "/$stripped"
    }

    private data class OutputPref(val dirName: String, val path: String)

    private fun String.limitLength(): String = if (length > MAX_DIR_LENGTH) substring(0, MAX_DIR_LENGTH) else this

    private companion object {
        const val DATASTORE_NAME = "pencil_output_path"
        const val MAX_DIR_LENGTH = 80
        val KEY_OUTPUT_DIR = stringPreferencesKey("outputDirName")
        val KEY_OUTPUT_PATH = stringPreferencesKey("outputPath")
        private val Context.outputPathDataStore by preferencesDataStore(name = DATASTORE_NAME)
    }
}

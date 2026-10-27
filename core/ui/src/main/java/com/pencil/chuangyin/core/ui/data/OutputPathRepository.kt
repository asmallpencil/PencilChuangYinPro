package com.pencil.chuangyin.core.ui.data

import android.content.Context
import android.net.Uri
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
 * 该设置属于「软件设置」而非 P 站设置，同时影响 P 站下载、文字打印导出、图片打印导出，
 * 以及 AI 功能的上传附件与生成产物。
 *
 * 由于 targetSdk=35 且未申请存储权限，Android 11+ **无法**用 [File] 直接写公共目录，
 * 因此对外暴露三种目标：
 * - [outputTreeUri]：用户在设置里通过系统目录选择器（SAF）授权的树目录，任何文件类型都能写；
 * - [relativeMediaStorePath]：MediaStore（Q+）可用的相对路径，仅限图片等媒体文件；
 * - [legacyPublicDir]：API 29 及以下可用的真实目录。
 *
 * 未授权 SAF 目录时，调用方应回退到 MediaStore；**绝不能**静默落到
 * `Android/data/<包名>/files`（Android 11+ 连系统文件管理器都打不开）。
 */
interface OutputPathRepository {

    /** 输出目录名状态流（如 PencilChuangyinPro），供 UI 订阅 / MediaStore 相对路径派生。 */
    val outputDirName: StateFlow<String>

    /** 设置输出目录名。为空白时回退为默认目录名。 */
    suspend fun setOutputDirName(name: String)

    /** 同步读取当前输出目录名。 */
    fun currentOutputDirName(): String

    /**
     * 用户通过 SAF 授权的输出目录树 URI；未授权时为 null。
     *
     * 已持久化授权（takePersistableUriPermission），重启后依然可写。
     */
    val outputTreeUri: StateFlow<String?>

    /** 记录用户新授权的输出目录树（内部会尝试持久化读权限）。 */
    suspend fun setOutputTree(uri: String)

    /** 撤销输出目录授权，回到默认目录。 */
    suspend fun clearOutputTree()

    /** 授权目录的展示名（如 Documents/打印产物）；未授权时为 ""。 */
    fun outputTreeDisplayName(): String

    /**
     * 当前可用的真实文件系统输出目录。
     *
     * 仅在「设置了完整绝对路径且确实可写」或「API 29 及以下的公共 Pictures」时有意义；
     * 授权了 SAF 目录时会返回空目录占位，调用方应先判断 [outputTreeUri]。
     */
    fun activeOutputDirFile(): File

    /** MediaStore（Q+）可用的相对路径，如 Pictures/PencilChuangyinPro。 */
    fun relativeMediaStorePath(): String

    /** API 29 及以下可用的公共输出目录。 */
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

    private val _outputTreeUri = MutableStateFlow<String?>(null)
    override val outputTreeUri: StateFlow<String?> = _outputTreeUri.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        runCatching {
            val first = runBlocking {
                context.outputPathDataStore.data
                    .map { pref ->
                        OutputPref(
                            dirName = pref[KEY_OUTPUT_DIR] ?: OUTPUT_DIR_DEFAULT,
                            treeUri = pref[KEY_OUTPUT_TREE] ?: "",
                        )
                    }
                    .first()
            }
            _outputDirName.value = sanitize(first.dirName)
            _outputTreeUri.value = first.treeUri.takeIf { it.isNotBlank() }
        }
        scope.launch {
            context.outputPathDataStore.data
                .map { pref ->
                    OutputPref(
                        dirName = pref[KEY_OUTPUT_DIR] ?: OUTPUT_DIR_DEFAULT,
                        treeUri = pref[KEY_OUTPUT_TREE] ?: "",
                    )
                }
                .collect { pref ->
                    _outputDirName.value = sanitize(pref.dirName)
                    _outputTreeUri.value = pref.treeUri.takeIf { it.isNotBlank() }
                }
        }
    }

    override suspend fun setOutputDirName(name: String) {
        val safe = sanitize(name)
        _outputDirName.value = safe
        context.outputPathDataStore.edit { it[KEY_OUTPUT_DIR] = safe }
    }

    override suspend fun setOutputTree(uri: String) {
        val value = uri.trim()
        if (value.isBlank()) return clearOutputTree()
        takePersistablePermission(value)
        _outputTreeUri.value = value
        context.outputPathDataStore.edit { it[KEY_OUTPUT_TREE] = value }
    }

    override suspend fun clearOutputTree() {
        _outputTreeUri.value?.let { releasePersistablePermission(it) }
        _outputTreeUri.value = null
        context.outputPathDataStore.edit { it[KEY_OUTPUT_TREE] = "" }
    }

    override fun outputTreeDisplayName(): String {
        val value = _outputTreeUri.value ?: return ""
        return runCatching { SafOutputDir.describe(context, Uri.parse(value)) }.getOrDefault("")
    }

    override fun currentOutputDirName(): String = _outputDirName.value

    override fun activeOutputDirFile(): File {
        val tree = _outputTreeUri.value
        if (!tree.isNullOrBlank()) {
            // 授权目录不是 File，只给出占位路径供日志/提示展示。
            return File("/saf/${outputTreeDisplayName()}")
        }
        return legacyPublicDir()
    }

    override fun relativeMediaStorePath(): String = "${Environment.DIRECTORY_PICTURES}/${_outputDirName.value.limitLength()}"

    override fun legacyPublicDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), _outputDirName.value.limitLength())

    private fun takePersistablePermission(uri: String) = runCatching {
        context.contentResolver.takePersistableUriPermission(
            Uri.parse(uri),
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    private fun releasePersistablePermission(uri: String) = runCatching {
        context.contentResolver.releasePersistableUriPermission(
            Uri.parse(uri),
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }

    private fun sanitize(name: String): String {
        val trimmed = name.trim().trim('\\', '/')
        return if (trimmed.isEmpty()) OUTPUT_DIR_DEFAULT else trimmed
    }

    private data class OutputPref(val dirName: String, val treeUri: String)

    private fun String.limitLength(): String = if (length > MAX_DIR_LENGTH) substring(0, MAX_DIR_LENGTH) else this

    private companion object {
        const val DATASTORE_NAME = "pencil_output_path"
        const val MAX_DIR_LENGTH = 80
        val KEY_OUTPUT_DIR = stringPreferencesKey("outputDirName")

        /** 旧版本存的是纯文本绝对路径；该方案在 Android 11+ 不可写，已废弃。 */
        val KEY_OUTPUT_TREE = stringPreferencesKey("outputTreeUri")
        private val Context.outputPathDataStore by preferencesDataStore(name = DATASTORE_NAME)
    }
}

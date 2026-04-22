package top.elune.utils

import top.elune.utils.commons.CodeManager
import top.elune.utils.commons.SedaConfig
import top.elune.utils.commons.SedaContext
import top.elune.utils.commons.Settings
import top.elune.utils.engine.SedaEngine
import top.elune.utils.utils.LogUtils
import java.nio.charset.Charset
import java.util.*


fun main(@Suppress("unused") args: Array<String>) {
    Locale.setDefault(Locale.SIMPLIFIED_CHINESE)

    println("file.encoding: " + Charset.defaultCharset().displayName())
    println("Default Charset: " + Charset.defaultCharset())
    Settings.init()
    LogUtils.init(Settings.LOG_PATH)
    CodeManager.init(Settings.VCODE_CSV_FILE_PATH)
    val dstDicomPath2Str = Settings.DST_DICOM_PATH2.ifBlank { null }
    val config = SedaConfig(
        inputPath = Settings.SRC_DICOM_PATH,
        ntfsOutputPath = Settings.DST_DICOM_PATH,
        nfsOutputPath = dstDicomPath2Str,
        logPath = Settings.LOG_PATH
    )
    SedaContext(config).use { ctx ->
        val engine = SedaEngine(ctx)
        engine.run()
    }
    LogUtils.release()
}

package top.elune.utils

import top.elune.utils.commons.CodeManager
import top.elune.utils.commons.SedaConfig
import top.elune.utils.commons.SedaContext
import top.elune.utils.commons.Settings
import top.elune.utils.engine.SedaEngine
import top.elune.utils.utils.LogUtils
import java.util.*


fun main(@Suppress("unused") args: Array<String>) {
    Locale.setDefault(Locale.ENGLISH)
    Settings.init()
    LogUtils.init(Settings.LOG_PATH)
    CodeManager.init(Settings.VCODE_CSV_FILE_PATH)
    val config = SedaConfig(
        inputPath = Settings.SRC_DICOM_PATH,
        ntfsOutputPath = Settings.DST_DICOM_PATH,
        nfsOutputPath = Settings.DST_DICOM_PATH2,
        logPath = Settings.LOG_PATH
    )
    SedaContext(config).use { ctx ->
        val engine = SedaEngine(ctx)
        engine.run()
    }
    LogUtils.release()
}

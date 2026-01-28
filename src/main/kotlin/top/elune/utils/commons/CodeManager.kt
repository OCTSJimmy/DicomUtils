package top.elune.utils.commons

import cn.org.ncrcnd.bigdata.ct_genai.dicom.selector.utils.LogUtils
import java.io.*
import java.nio.charset.Charset
import java.nio.charset.UnsupportedCharsetException
import kotlin.collections.get
import kotlin.collections.iterator

/**
 * 原始中心编码,原始受试者编码,生成随机编码,脱敏中心编码,脱敏受试者编码
 */
enum class CodeManager {
    INSTANCE;

    private val mOriginCodeModuleMap = HashMap<String, CodeModule>()
    private val mDesensitizedCodeModuleMap = HashMap<String, CodeModule>()
    fun put(codeModule: CodeModule) {
        try {
            mOriginCodeModuleMap[codeModule.originSubjectCode] = codeModule
            mDesensitizedCodeModuleMap[codeModule.desensitizedSubjectCode] = codeModule
        } catch (ignored: Exception) {
        }
    }

    operator fun get(originSubjectCode: String?): CodeModule? {
        return mOriginCodeModuleMap[originSubjectCode]
    }

    fun getByDesensitizedCode(desensitizedCode: String?): CodeModule? {
        return mDesensitizedCodeModuleMap[desensitizedCode]
    }

    open fun getOriginCodeModuleMap(): HashMap<String, CodeModule> {
        val result = HashMap<String, CodeModule>()
        for (entry in mOriginCodeModuleMap) {
            result[entry.key] = entry.value.clone()
        }

        return result
    }

    val sizeWithOriginSubjectCode: Int
        get() = mOriginCodeModuleMap.size
    val sizeWithDesensitizedSubjectCode: Int
        get() = mDesensitizedCodeModuleMap.size

    fun getNotDone(): ArrayList<CodeModule> {
        val notDone: ArrayList<CodeModule> = ArrayList()
        for (entry in mOriginCodeModuleMap) {
            val codeModule = entry.value
            if (!codeModule.isDone()) {
                notDone.add(codeModule)
            }
        }
        return notDone
    }

    companion object {
        fun init(csvPaths: String = Settings.VCODE_CSV_FILE_PATH) {
            val csvFile = File(csvPaths)
            var br: BufferedReader? = null
            try {
                br = BufferedReader(InputStreamReader(FileInputStream(csvFile), Charset.forName("UTF-8")))
                var line: String? = null
                var isFirst = false
                while (null != br.readLine().also { line = it }) {
                    if (!isFirst) {
                        isFirst = true
                        continue
                    }
                    val contentArr = line!!.split(",").toTypedArray()
                    if (contentArr.size < 2) continue
                    val codeModule = CodeModule(
//                        subjectNumber = contentArr[1].trim { it <= ' ' },
                        mSubjectNumber = contentArr[0].trim { it <= ' ' },
//                        vSubjectNumber = contentArr[4].trim { it <= ' ' },
                        mvSubjectNumber = contentArr[1].trim { it <= ' ' }
                    )
                    INSTANCE.put(codeModule)
                }
                LogUtils.debugNoPrint("CSV加载结束")
            } catch (e: UnsupportedCharsetException) {
                LogUtils.err("不支持UTF-8编码")
                System.exit(1)
            } catch (e: Exception) {
                LogUtils.err("读取CSV文件失败")
                LogUtils.err(e)
                System.exit(1)
            } finally {
                if (br != null) {
                    try {
                        br.close()
                        LogUtils.log("成功加载编码CSV文件")
                    } catch (e: IOException) {
                        LogUtils.err("关闭CSV文件失败")
                    }
                }
            }
        }
    }
}
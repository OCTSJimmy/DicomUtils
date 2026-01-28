package top.elune.utils.commons

import cn.org.ncrcnd.bigdata.ct_genai.dicom.selector.utils.LogUtils
import java.io.*
import java.nio.charset.Charset
import java.nio.charset.UnsupportedCharsetException

enum class NeedListManager {
    INSTANCE;

    val siteCodeModuleMap = HashMap<String?, ArrayList<NeedCodeModule>>()
    fun put(codeModule: NeedCodeModule) {
        val list = siteCodeModuleMap.computeIfAbsent(codeModule.siteCode) { k: String? -> ArrayList() }
        list.add(codeModule)
    }

    companion object {
        fun init(csvPaths: String = Settings.NEED_SUBJECT_LIST_CSV_PATH) {
            val csvFile = File(csvPaths)
            var br: BufferedReader? = null
            var isFirstLine = false
            try {
                br = BufferedReader(InputStreamReader(FileInputStream(csvFile), Charset.forName("GBK")))
                var line: String? = null
                while (null != br.readLine().also { line = it }) {
                    if (!isFirstLine) {
                        isFirstLine = true
                        continue
                    }
                    val contentArr = line!!.split(",").toTypedArray()
                    if (contentArr.size < 2) continue
                    val codeModule = NeedCodeModule(contentArr[0].trim { it <= ' ' }, contentArr[1].trim { it <= ' ' })
                    INSTANCE.put(codeModule)
                }
            } catch (e: UnsupportedCharsetException) {
                LogUtils.err("不支持GBK编码")
                System.exit(1)
            } catch (e: Exception) {
                LogUtils.err("读取CSV文件失败")
                e.printStackTrace()
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
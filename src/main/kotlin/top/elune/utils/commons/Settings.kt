package top.elune.utils.commons

import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.*


class Settings {
    companion object {
        @JvmStatic
        val properties = Properties()

        @JvmStatic
        var VCODE_CSV_FILE_PATH: String = "/root/Project01/TextFiles/CodeN-Disk01.csv"

        @JvmStatic
        var SRC_DICOM_PATH: String = "/public/home/guoheming/YeWanxing/Disk01"

        @JvmStatic
        var DST_DICOM_PATH: String = "/mnt/sdc1/DstProject01/ResultDCM"

        @JvmStatic
        var DST_DICOM_PATH2: String = "/data/shar01/backup/Dst_DICOM/Project01/Disk01/ResultDCM"

        @JvmStatic
        var LOG_PATH: String = "/root/Project01/Logs/Disk01"

        @JvmStatic
        var IS_VALID_SUBJECT_NAME: Boolean = true

        @JvmStatic
        var IS_VALID_SUBJECT_NAME_SIMILARITY: Boolean =
            true

        @JvmStatic
        var NEED_SUBJECT_LIST_CSV_PATH: String = ""

        /**
         * 受试者验证正则
         * 例如：
         * R:\Images-DICOM\Origin\001\A01001
         *
         */
        @JvmStatic
        var SUBJECT_DIR_VALID_REGEX: Regex = """^.*/[0-9]{14}_[0-9]{9}_[^/]*$""".toRegex()

        /**
         * 影像文件DW层目录
         * 例如：
         * R:\Images-DICOM\Origin\001\A01001
         *
         */
        @JvmStatic
        var DICOM_DW_DIR_VALID_REGEX = """^.*/[0-9]{14}_[0-9]{9}_[^/]*/.*$""".toRegex()

        /**
         * 影像文件路径
         * 例如：
         * R:\Images-DICOM\Origin\001\A01001
         *
         */
        @JvmStatic
        var DICOM_OTHER_DIRS_VALID_REGEX = """^.*/[0-9]{14}_[0-9]{9}_.*?(/.*)+$""".toRegex()

        /**
         * 影像文件路径截取替换为受试者目录路径
         * 主要提供给原始数据留底的CSV中第一列，用于方便按受试者路径分类
         * 例如：
         * R:\Images-DICOM\Origin\001\A01001\WANG-WEN-TAO__202205191714__CT__0100__Brain-Perfusion-Jog-AW47-CTA--\01706-1346670589331637885772767923705000015696748768904362609
         * 替换为：
         * R:\Images-DICOM\Origin\001\A01001
         */
        @JvmStatic
        var ORIGIN_SUBJECT_CODE_PATH_REPLACE_REGEX = """^(.*/[0-9]{14}_[0-9]{9}_[^/]*)/.*$""".toRegex()

        /**
         * 影像文件路径截取替换为受试者目录路径
         * 主要提供给原始数据留底的CSV中第一列，用于方便按受试者路径分类
         * 例如：
         * R:\Images-DICOM\Origin\001\A01001\WANG-WEN-TAO__202205191714__CT__0100__Brain-Perfusion-Jog-AW47-CTA--\01706-1346670589331637885772767923705000015696748768904362609
         * 替换为：
         * R:\Images-DICOM\Origin\001\A01001
         */
        @JvmStatic
        var ORIGIN_SUBJECT_CODE_PATH_REPLACE_DST: String = """$1"""

        /**
         * 从受试者路径截取受试者编号替换搜索正则
         * 之后将按照替换后的这个字符串，在脱敏编码对照表中检索对应的脱敏编号信息
         * 例如：
         * R:\Images-DICOM\Origin\001\A01001\WANG-WEN-TAO__202205191714__CT__0100__Brain-Perfusion-Jog-AW47-CTA--\01706-1346670589331637885772767923705000015696748768904362609
         * 替换为：
         * 001-01001
         */
        @JvmStatic
        var ORIGIN_SUBJECT_CODE_REPLACE_REGEX = """^.*/([0-9]{14})_[0-9]{9}_[^/]*/?.*$""".toRegex()

        /**
         * 从受试者路径截取受试者编号替换目标
         * 例如：
         * R:\Images-DICOM\Origin\001\A01001\WANG-WEN-TAO__202205191714__CT__0100__Brain-Perfusion-Jog-AW47-CTA--\01706-1346670589331637885772767923705000015696748768904362609
         * 替换为：
         * 001-01001
         */
        @JvmStatic
        var ORIGIN_SUBJECT_CODE_REPLACE_DST: String = """$1"""

        /**
         * 替换原受试者路径中经验证合规的受试者这一级目录名称到目标名称，
         * 一般为脱敏后的受试者编号，
         * 其中@originSubjectCode是占位符，用于代表原受试者编号，
         * 会将该字符串直接替换为原受试者编号后将整串文本转为正则表达式
         * 例如：
         * R:\Images-DICOM\Origin\001(\A01001\)WANG-WEN-TAO__202205191714__CT__0100__Brain-Perfusion-Jog-AW47-CTA--\01706-1346670589331637885772767923705000015696748768904362609
         * 被括号括起来的部分即为将要被替换的部分
         *
         */
        @JvmStatic
        var ORIGIN_SUBJECT_DIR_REPLACE_TO_RELEASE_SUBJECT_DIR_REGEX_STR = "/[^/]*@originSubjectCode[^/]*/"

        @JvmStatic
                /**
                 * 替换原受试者路径中经验证合规的受试者这一级目录名称到目标名称，
                 * 一般为脱敏后的受试者编号，
                 * 其中@desensitizedSubjectCode是占位符，用于代表脱敏的受试者编号，
                 * 例如：
                 * R:\Images-DICOM\Origin\001(\A01001\)WANG-WEN-TAO__202205191714__CT__0100__Brain-Perfusion-Jog-AW47-CTA--\01706-1346670589331637885772767923705000015696748768904362609
                 * 被括号括起来的部分即为将要被替换的部分
                 * 整个路径将会被替换为：
                 * R:\Images-DICOM\Origin\001\P01523\WANG-WEN-TAO__202205191714__CT__0100__Brain-Perfusion-Jog-AW47-CTA--\01706-1346670589331637885772767923705000015696748768904362609
                 *
                 * 也可能：
                 * R:\Images-DICOM\Origin(\001\A01001\)WANG-WEN-TAO__202205191714__CT__0100__Brain-Perfusion-Jog-AW47-CTA--\01706-1346670589331637885772767923705000015696748768904362609
                 * 替换为：
                 * R:\Images-DICOM\Origin\C053\P01523\WANG-WEN-TAO__202205191714__CT__0100__Brain-Perfusion-Jog-AW47-CTA--\01706-1346670589331637885772767923705000015696748768904362609
                 */
        var ORIGIN_SUBJECT_DIR_REPLACE_TO_RELEASE_SUBJECT_DIR_DST_STR = "/@desensitizedSubjectCode/"

        @JvmStatic
        fun init() {
            var reader : BufferedReader? =  null
            try {
                reader = BufferedReader(InputStreamReader(FileInputStream("program_settings.properties"), Charset.forName("UTF-8")))
                properties.load(reader)
                VCODE_CSV_FILE_PATH =
                    properties.getProperty("VCODE_CSV_FILE_PATH", VCODE_CSV_FILE_PATH)
                SRC_DICOM_PATH = properties.getProperty("SRC_DICOM_PATH", SRC_DICOM_PATH)
                DST_DICOM_PATH = properties.getProperty("DST_DICOM_PATH", DST_DICOM_PATH)
                DST_DICOM_PATH2 = properties.getProperty("DST_DICOM_PATH2", DST_DICOM_PATH2)
                LOG_PATH = properties.getProperty("LOG_PATH", LOG_PATH)
                IS_VALID_SUBJECT_NAME = properties.getProperty("IS_VALID_SUBJECT_NAME", IS_VALID_SUBJECT_NAME.toString()).toBoolean()
                IS_VALID_SUBJECT_NAME_SIMILARITY =
                    properties.getProperty("IS_VALID_SUBJECT_NAME_SIMILARITY", IS_VALID_SUBJECT_NAME_SIMILARITY.toString()).toBoolean()
                NEED_SUBJECT_LIST_CSV_PATH = properties.getProperty("NEED_SUBJECT_LIST_CSV_PATH", NEED_SUBJECT_LIST_CSV_PATH)
                SUBJECT_DIR_VALID_REGEX =
                    properties.getProperty("SUBJECT_DIR_VALID_REGEX", SUBJECT_DIR_VALID_REGEX.pattern).toRegex()
                DICOM_DW_DIR_VALID_REGEX =
                    properties.getProperty("DICOM_DW_DIR_VALID_REGEX", DICOM_DW_DIR_VALID_REGEX.pattern).toRegex()
                DICOM_OTHER_DIRS_VALID_REGEX =
                    properties.getProperty("DICOM_OTHER_DIRS_VALID_REGEX", DICOM_OTHER_DIRS_VALID_REGEX.pattern).toRegex()
                ORIGIN_SUBJECT_CODE_PATH_REPLACE_REGEX = properties.getProperty(
                    "ORIGIN_SUBJECT_CODE_PATH_REPLACE_REGEX",
                    ORIGIN_SUBJECT_CODE_PATH_REPLACE_REGEX.pattern
                ).toRegex()
                ORIGIN_SUBJECT_CODE_PATH_REPLACE_DST =
                    properties.getProperty("ORIGIN_SUBJECT_CODE_PATH_REPLACE_DST", ORIGIN_SUBJECT_CODE_PATH_REPLACE_DST)
                ORIGIN_SUBJECT_CODE_REPLACE_REGEX = properties.getProperty(
                    "ORIGIN_SUBJECT_CODE_REPLACE_REGEX",
                    ORIGIN_SUBJECT_CODE_REPLACE_REGEX.pattern
                ).toRegex()

                ORIGIN_SUBJECT_CODE_REPLACE_DST = properties.getProperty("ORIGIN_SUBJECT_CODE_REPLACE_DST", ORIGIN_SUBJECT_CODE_REPLACE_DST)

                ORIGIN_SUBJECT_DIR_REPLACE_TO_RELEASE_SUBJECT_DIR_REGEX_STR = properties.getProperty(
                    "ORIGIN_SUBJECT_DIR_REPLACE_TO_RELEASE_SUBJECT_DIR_REGEX_STR",
                    ORIGIN_SUBJECT_DIR_REPLACE_TO_RELEASE_SUBJECT_DIR_REGEX_STR
                )
                ORIGIN_SUBJECT_DIR_REPLACE_TO_RELEASE_SUBJECT_DIR_DST_STR = properties.getProperty(
                    "ORIGIN_SUBJECT_DIR_REPLACE_TO_RELEASE_SUBJECT_DIR_DST_STR",
                    ORIGIN_SUBJECT_DIR_REPLACE_TO_RELEASE_SUBJECT_DIR_DST_STR
                )

            } catch (e: Exception) {
                System.err.println("Load settings.properties Error.")
                e.printStackTrace()
            } finally {
                reader?.close()
            }
        }
    }
}
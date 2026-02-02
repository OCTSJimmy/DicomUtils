package top.elune.utils.dicom

import java.io.Writer
import java.text.SimpleDateFormat
import java.util.*

data class OriginDicomData(
    val patientName: String? = null,
    val patientId: String?  = null,
    val originSiteCode: String? = null,
    val originSubjectNumber: String? = null,
    val vSiteCode: String?  = null,
    val vSubjectCode: String?  = null,
    val studyId: String?  = null,
    val patientSex: String?  = null,
    val patientAge: String?  = null,
    val patientWeight: String?  = null,
    val patientBirthDate: Date?  = null,
    val studyDesc: String?  = null,
    val deviceSerialNumber: String?  = null,
    val studyDate: Date?  = null,
    val seriesDate: Date?  = null,
    val acquisitionDate: Date?  = null,
    val contentDate: Date?  = null,
    val manufacturer: String?  = null,
    val manufacturerModelName: String?  = null,
    var srcPath: String? = null,
    var isSkipAllDW: Boolean = false
) {

    fun toCsvLine():String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val stringBuilder = StringBuilder()
        stringBuilder.append("\"$patientName\",")
        stringBuilder.append("\"$patientId\",")
        stringBuilder.append("\"$vSubjectCode\",")
        stringBuilder.append("\"$vSiteCode\",")
        stringBuilder.append("\"$studyId\",")
        stringBuilder.append("\"$patientSex\",")
        stringBuilder.append("\"$patientAge\",")
        stringBuilder.append("\"$patientWeight\",")
        if (patientBirthDate != null) {
            stringBuilder.append("\"${sdf.format(patientBirthDate)}\",")
        } else {
            stringBuilder.append("\"\",")
        }
        stringBuilder.append("\"$studyDesc\",")
        stringBuilder.append("\"$deviceSerialNumber\",")
        if (studyDate != null) {
            stringBuilder.append("\"${sdf.format(studyDate)}\",")
        } else {
            stringBuilder.append("\"\",")
        }
        if (seriesDate != null) {
            stringBuilder.append("\"${sdf.format(seriesDate)}\",")
        } else {
            stringBuilder.append("\"\",")
        }
        if (acquisitionDate != null) {
            stringBuilder.append("\"${sdf.format(acquisitionDate)}\",")
        } else {
            stringBuilder.append("\"\",")
        }
        if (contentDate != null) {
            stringBuilder.append("\"${sdf.format(contentDate)}\",")
        } else {
            stringBuilder.append("\"\",")
        }
        stringBuilder.append("\"$manufacturer\",")
        stringBuilder.append("\"$manufacturerModelName\",")

        stringBuilder.append("\"$srcPath\"")
        return stringBuilder.toString()
    }

    @Suppress("unused")
    fun writeData(writer: Writer) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        writer.append("\"$patientName\",")
        writer.append("\"$patientId\",")
        writer.append("\"$vSubjectCode\",")
        writer.append("\"$vSiteCode\",")
        writer.append("\"$studyId\",")
        writer.append("\"$patientSex\",")
        writer.append("\"$patientAge\",")
        writer.append("\"$patientWeight\",")
        if (patientBirthDate != null) {
            writer.append("\"${sdf.format(patientBirthDate)}\",")
        } else {
            writer.append("\"\",")
        }
        writer.append("\"$studyDesc\",")
        writer.append("\"$deviceSerialNumber\",")
        if (studyDate != null) {
            writer.append("\"${sdf.format(studyDate)}\",")
        } else {
            writer.append("\"\",")
        }
        if (seriesDate != null) {
            writer.append("\"${sdf.format(seriesDate)}\",")
        } else {
            writer.append("\"\",")
        }
        if (acquisitionDate != null) {
            writer.append("\"${sdf.format(acquisitionDate)}\",")
        } else {
            writer.append("\"\",")
        }
        if (contentDate != null) {
            writer.append("\"${sdf.format(contentDate)}\",")
        } else {
            writer.append("\"\",")
        }
        writer.append("\"$manufacturer\",")
        writer.append("\"$manufacturerModelName\",")

        writer.append("\"$srcPath\"")
        writer.flush()
    }
}
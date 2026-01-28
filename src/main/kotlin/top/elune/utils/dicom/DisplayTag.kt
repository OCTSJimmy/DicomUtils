package top.elune.utils.dicom

import top.elune.utils.commons.Context
import top.elune.utils.utils.LogUtils
import org.dcm4che3.data.*
import org.dcm4che3.io.DicomEncodingOptions
import org.dcm4che3.io.DicomOutputStream
import java.awt.image.Raster
import java.io.*
import java.sql.Time
import java.util.*

/**
 * @version: V1.0
 * @author: fendo
 * @className: DisplayTag
 * @packageName: com.xxxx.xxxx.common.util
 * @description: Tag解析
 * @data: 2018-03-26 10:07
 */
@Suppress("MemberVisibilityCanBePrivate")
class DisplayTag public constructor(file: File) {
    /**
     * Giving attribut of metadata
     *
     * @return
     */

    /**
     * Donne la valeur du tag rechercher/Giving a value of tag seek
     *
     * @return le String de la valeur rechercher du tag dans un item
     */
    var valeurTagItem: String? = null
        private set

    /**
     * Donne la valeur du tag rechercher/Giving the value Tag
     *
     * @return le Double de la valeur rechercher du tag dans un item
     */
    var valeurTagItemDouble: Double? = null
        private set
    private val nom: String? = null
    private val nounString: String? = null
    private var val2 = 0
    private val valeurReturn = 0

    /**
     * On obtient l'unité des items./Giving unity of items
     *
     * @return le nom de l'unité
     */
    var nounUnit: String? = null
        private set

    /**
     * Obtient la valeur de puissance/ Giving value power
     *
     * @return
     */
    var facteurPuissance = 0.0
        private set

    /**
     * Donne la valeur du Ratio/Diving value ratio Spatial
     *
     * @return
     */
    var valeurTagItemDoubleRatio: Double? = null
        private set

    /**
     * On obtient l'unite des items./Giving unity items
     *
     * @return le nom de l'unité
     */
    var nounUnitRatio: String? = null
        private set
    private val encOpts = DicomEncodingOptions.DEFAULT

    @Volatile
    private var currentFile: File? = null

    var attributes: Attributes? = null
        private set

    var fileMetaInformation: Attributes? = null
        private set

    init {
        loadDicomObject(file)
    }


    companion object {
        /**
         * Giving getFactorPower
         */
        var factorPower = 0.0
            private set
        private val HEX_DIGITS = charArrayOf(
            '0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', 'A', 'B',
            'C', 'D', 'E', 'F'
        )
        private val dict = ElementDictionary.getStandardElementDictionary()


        /**
         * Permet d'afficher l'heure d'une valeur dicom en standard international yyyy.mm.dd/ Permit display a time in metadata for yyyy.mm.dd
         *
         * @param object
         * @param Tag       : valeur du tag/ value of tag
         * @param valueBool : si true Format yyyy.mm.dd sinon format dd.mm.yyyy/ if true format yyyy.mm.dd else dd.mm.yyyy
         * @param valueNoun : "dot" mettre la date en format yyyy.mm.dd ou dd.mm.yyyy sinon en format yyyy mm dd ou dd mm yyyy/dot" put yyyy.mm.dd or dd.mm.dd or dd.mm.yyyy else yyyy mm or dd mm yyyy
         * @return afficher le string du tag selon le standard international/ return string date
         * @throws IOException
         */
        @Throws(IOException::class)
        fun dicomDate(
            `object`: Attributes,
            Tag: Int,
            valueBool: Boolean,
            valueNoun: String?,
        ): String {
            val tagValue = `object`.getString(Tag)
            return formatDate(tagValue, valueBool, valueNoun)
        }

        /**
         * Format tag
         *
         * @param Numero    : String date
         * @param valueBool : if true Format yyyy.mm.dd else format dd.mm.yyyy
         * @param valueNoun : "dot" put the date in format yyyy.mm.dd or dd.mm.yyyy else in format yyyy mm dd or dd mm yyyy
         * @return
         */
        fun formatDate(Numero: String, valueBool: Boolean, valueNoun: String?): String {
            if (Numero.matches("^[0-9]*$".toRegex())) { //If la chaine de caractère est un nombre ou un chiffre
                val r = StringBuffer()
                return if (valueBool == true) { //Format yyyy.mm.dd
                    var i = 0
                    val j = Numero.length
                    while (i < j) {
                        r.append(Numero[i])
                        if (i == 3 || i == 5) {
                            if (if (valueNoun == null) "dot" == null else valueNoun == "dot") {
                                r.append('.')
                            } else {
                                r.append(' ')
                            }
                        }
                        i++
                    }
                    r.toString()
                } else {
                    run {
                        var i = 6
                        val j = 8
                        while (i < j) {
                            //jours
                            r.append(Numero[i])
                            if (i == 7) {
                                if (if (valueNoun == null) "dot" == null else valueNoun == "dot") {
                                    r.append('.')
                                } else {
                                    r.append(' ')
                                }
                            }
                            i++
                        }
                    }
                    run {
                        var i = 4
                        val j = 6
                        while (i < j) {
                            r.append(Numero[i]) //The first char value of the sequence is at index zero, the next at index one, and so on, as for array indexing.
                            if (i == 5) {
                                if (if (valueNoun == null) "dot" == null else valueNoun == "dot") {
                                    r.append('.')
                                } else {
                                    r.append(' ')
                                }
                            }
                            i++
                        }
                    }
                    var i = 0
                    val j = 4
                    while (i < j) {
                        r.append(Numero[i]) //The first char value of the sequence is at index zero, the next at index one, and so on, as for array indexing.
                        i++
                    }
                    r.toString()
                }
            }
            return Numero
        }

        /**
         * Converts the string representation of a header number
         * e.g. 0008,0010 to the corresponding integer as 0x00080010
         * as used in the @see org.dcm4che2.data.Tag
         *
         * @param headerNr e.g. 0008,0010
         * @return 0x00080010 as int
         */
        fun toTagInt(headerNr: String): Int {
            return headerNr.replace(",".toRegex(), "").toInt(16)
        }

        /**
         * Remove string ^ in file dicom
         *
         * @param num
         * @return
         */
        fun texteDicom(num: String): String {
            var num = num
            num = num.replace("\\^+".toRegex(), " ")
            return num
        }

        /**
         * Convertor tag to String
         * Using VM !=1
         * example result [25, 25]
         *
         * @param Tag
         * @return
         */
        fun getStringTag(`object`: Attributes, Tag: Int): String {
            val tagValue2 =
                `object`.getStrings(Tag) //Conversion table in List to String
            return Arrays.asList(*tagValue2).toString()
        }

        /**
         * Convertor tag to String
         * Using VM !=1
         * example result 25/25
         *
         * @param object
         * @param Tag
         * @return
         */
        fun getStringTag2(`object`: Attributes, Tag: Int): String {
            val tagValue2 =
                `object`.getStrings(Tag) //Conversion table in List to String
            return arrayToString(tagValue2, "\\")
        }

        /**
         * Convert an array of strings to one string
         * Put the 'separator' string between each element
         *
         * @param a
         * @param separator
         * @return
         */
        fun arrayToString(a: Array<String?>, separator: String?): String {
            val result = StringBuffer()
            if (a.size > 0) {
                result.append(a[0])
                for (i in 1 until a.size) {
                    result.append(separator)
                    result.append(a[i])
                }
            }
            return result.toString()
        }

        /**
         * returns the name of the given Tag
         *
         * @param tagNr
         * @return
         */
        fun getHeaderName(tagNr: Int): String {
            return dict.keywordOf(tagNr)
        }

        /**
         * Converts the string representation of a header number
         * e.g. 0008,0010 to the corresponding integer as 0x00080010
         * as used in the @see org.dcm4che2.data.Tag
         *
         * @param headerNr e.g. 0008,0010
         * @return 0x00080010 as int
         */
        fun toTagInt2(headerNr: String): Int {
            return headerNr.replace(",".toRegex(), "").toInt(16)
        }

        /**
         * Removing comma in String
         *
         * @param num
         * @return
         */
        fun formatNotDot(num: String): String {
            var num = num
            num = num.trim { it <= ' ' }.replace("[^0-9\\+]".toRegex(), "")
            if (num.matches("^0*$".toRegex())) {
                num = ""
            }
            return num
        }

        /**
         * Format
         * hh.mm.ss
         *
         * @param Numero
         * @return
         */
        fun FormatTime(Numero: String): String {
            if (Numero.matches("^[0-9]*$".toRegex())) {
                val r = StringBuilder()
                var i = 0
                val j = 6
                while (i < j) {
                    r.append(Numero[i])
                    if (i % 2 == 1 && i < j - 1) {
                        r.append(':')
                    }
                    i++
                }
                return r.toString()
            }
            return Numero
        }

        /**
         * Format
         * hh.mm.ss.frac
         *
         * @param Numero
         * @return
         */
        fun FormatTimes(Numero: String): String {
            if (Numero.matches("^[0-9].*$".toRegex())) {
                val r = StringBuilder()
                var i = 0
                val j = Numero.length
                while (i < j) {
                    r.append(Numero[i])
                    if ((i % 2 == 1) and (i < 5)) {
                        r.append(':')
                    }
                    i++
                }
                return r.toString()
            }
            return Numero
        }

        /**
         * Giving power
         * Example:
         * setFactorPower(10,2)//10^2
         *
         * @param result3
         * @param factor
         * @return
         * @return
         */
        fun setFactorPower(result3: Double, factor: Double): Double {
            return Math.pow(result3, factor).also { factorPower = it }
        }

        /**
         * Giving pixelData
         *
         * @param dcmObj
         * @return
         */
        fun lattricePixelData(dcmObj: Attributes): IntArray {
            return dcmObj.getInts(Tag.PixelData)
        }

        /**
         * Return value table input
         *
         * @param object
         * @param PATIENT_ADDITIONAL_TAGS : Table int
         *
         *
         * example :
         * public static final int[] tag = {
         * 0x00080020,
         * 0x00080022,
         * };
         *
         *
         * FileInputStream fis = new FileInputStream(fileInput);
         * DicomInputStream dis = new DicomInputStream(fis);
         * DicomObject obj = dis.readDicomObject();
         * String nounValue[] =getValue(obj,tag);
         * @return
         */
        private fun getValue(`object`: Attributes, PATIENT_ADDITIONAL_TAGS: IntArray): Array<String?> {
            val value = arrayOfNulls<String>(PATIENT_ADDITIONAL_TAGS.size)
            var i = 0
            while (i < PATIENT_ADDITIONAL_TAGS.size) {
                for (tag in PATIENT_ADDITIONAL_TAGS) {
                    value[i] = `object`.getString(tag)
                    i++
                }
                //System.out.print(value[0]+"\n");
//System.out.print(value[1]);
            }
            return value
        }

        /**
         * converts the int representation of a header number
         * e.g. 0x00080010 to the corresponding String 0008,0010
         *
         * @return 0008, 0010 as String
         */
        fun toTagString(tagNr: Int): String {
            return shortToHex(tagNr shr 16) +
                    ',' + shortToHex(tagNr)
        }

        fun shortToHex(`val`: Int): String {
            val ch = CharArray(4)
            shortToHex(`val`, ch, 0)
            return String(ch)
        }

        fun shortToHex(`val`: Int, sb: StringBuffer): StringBuffer {
            sb.append(HEX_DIGITS[`val` shr 12 and 0xf])
            sb.append(HEX_DIGITS[`val` shr 8 and 0xf])
            sb.append(HEX_DIGITS[`val` shr 4 and 0xf])
            sb.append(HEX_DIGITS[`val` and 0xf])
            return sb
        }

        fun shortToHex(`val`: Int, ch: CharArray, off: Int) {
            ch[off] = HEX_DIGITS[`val` shr 12 and 0xf]
            ch[off + 1] = HEX_DIGITS[`val` shr 8 and 0xf]
            ch[off + 2] = HEX_DIGITS[`val` shr 4 and 0xf]
            ch[off + 3] = HEX_DIGITS[`val` and 0xf]
        }
    }

    /**
     * Read metadata of Dicom 3.0
     *
     * @param f : input file
     * @return Attributes
     * @throws IOException
     */

    // 更简洁的 Kotlin 风格实现
    fun loadDicomObject(f: File?): Attributes? {
        if (f == null) return null
        return try {
            CustomDicomInputStream(f).use { dis -> // use 会自动关闭 dis
                this.attributes = dis.readDataset(-1, -1)
                this.fileMetaInformation = dis.fileMetaInformation
                this.attributes
            }
        } catch (e: Exception) {
            val reason = e.message ?: e.javaClass.simpleName
            LogUtils.err("Read file %s failure. Reason is : %s", f.absolutePath, reason)
            Context.submitFailureFile()
            null
        }
    }

    /**
     * Display metadata
     *
     * @param file : file inout
     * @throws IOException
     */
    @Throws(IOException::class)
    fun readTagDicom(file: File?): String {
        return loadDicomObject(file).toString()
    }

    /**
     * Permet d'afficher l'heure d'une valeur dicom en standard international yyyy.mm.dd/ Permit display time in format yyyy.mm.dd
     *
     * @param Tag       : valeur du tag / int tag
     * @param valueBool : si true Format yyyy.mm.dd sinon format dd.mm.yyyy/ if true then format yyyy.mm.dd else dd.mm.yyyy
     * @param valueNoun : "dot" mettre la date en format yyyy.mm.dd ou dd.mm.yyyy sinon en format yyyy mm dd ou dd mm yyyy/ "dot" put yyyy.mm.dd or dd.mm.dd or dd.mm.yyyy else yyyy mm or dd mm yyyy
     * @return afficher le string du tag selon le standard international/ return string Date
     * @throws IOException
     */
    @Throws(IOException::class)
    fun dicomDate(Tag: Int, valueBool: Boolean, valueNoun: String?): String? {
        return if (attributes?.contains(Tag) == true) {
            val tagValue: String = attributes?.getString(Tag) ?: ""
            formatDate(tagValue, valueBool, valueNoun)
        } else {
            null
        }
    }

    /**
     * Read value tag of VR = DA
     *
     *
     * If use setDicomObject(readDicomObject(File f)), and getHeaderDateValue(getDicomObject())
     *
     * @param tagNr "0000,0010"
     * @return
     */
    fun getHeaderDateValue(tagNr: String): Date {
        return getHeaderDateValue(toTagInt(tagNr))
    }

    /**
     * Read value tag of VR = DA
     *
     * @param tagNr see dcm4che2
     * @return
     */
    fun getHeaderDateValue(tagNr: Int): Date {
        return attributes?.getDate(tagNr) ?: Date()
    }

    /**
     * Read value tag of VR = DA
     *
     * @param tagNr
     * @param dicomObj
     * @return
     */
    fun getHeaderDateValue(tagNr: Int, dicomObj: Attributes): Date {
        return dicomObj.getDate(tagNr)
    }

    /**
     * Read value tag of VR = DA
     *
     * @param tagNr    :"0000,0010"
     * @param dicomObj
     * @return
     */
    fun getHeaderDateValue(tagNr: String, dicomObj: Attributes): Date {
        return getHeaderDateValue(toTagInt(tagNr), dicomObj)
    }

    /**
     * Permit display time in hh.mm.ss
     * (0008,0030) AT S Study Time
     * (0008,0031) AT S Series Time
     * (0008,0032) AT S Acquisition Time
     * (0008,0033) AT S Image Time
     *
     * @param Tag : giving tag
     * @return
     * @throws IOException
     */
    @Throws(IOException::class)
    fun dicomTime(Tag: Int): String? {
        return if (attributes?.contains(Tag) == true) {
            val tagValue: String = attributes?.getString(Tag) ?: ""
            val tagValueNotDot = formatNotDot(tagValue)
            FormatTimes(tagValueNotDot)
        } else {
            null
        }
    }

    /**
     * Permit display time in hh.mm.ss.fac
     * (0008,0030) AT S Study Time
     * (0008,0031) AT S Series Time
     * (0008,0032) AT S Acquisition Time
     * (0008,0033) AT S Image Time
     *
     * @param Tag : giving tag
     * @return
     * @throws IOException
     */
    @Throws(IOException::class)
    fun dicomTimeTotal(Tag: Int): String? {
        return if (attributes?.contains(Tag) == true) {
            val tagValue: String = attributes?.getString(Tag) ?: ""
            FormatTimes(tagValue)
        } else {
            null
        }
    }

    /**
     * Permit display time in hh.mm.ss
     * (0008,0030) AT S Study Time
     * (0008,0031) AT S Series Time
     * (0008,0032) AT S Acquisition Time
     * (0008,0033) AT S Image Time
     *
     * @param object : Metadata
     * @param Tag    : value dicom
     * @return new value String
     * @throws IOException
     */
    @Throws(IOException::class)
    fun dicomTime2(`object`: Attributes, Tag: Int): String {
        val tagValue = `object`.getString(Tag)
        val tagValueNotDot = formatNotDot(tagValue)
        println(FormatTime(tagValueNotDot))
        return FormatTimes(tagValueNotDot)
    }

    /**
     * Permit display time in hh.mm.ss.frac
     * (0008,0030) AT S Study Time
     * (0008,0031) AT S Series Time
     * (0008,0032) AT S Acquisition Time
     * (0008,0033) AT S Image Time
     *
     * @param object : Metadata
     * @param Tag    : value dicom
     * @return new value String
     * @throws IOException
     */
    @Throws(IOException::class)
    fun dicomTime3(`object`: Attributes, Tag: Int): String {
        val tagValue = `object`.getString(Tag)
        return FormatTimes(tagValue)
    }

    /**
     * reads a int value from the Dicomheader
     *
     * @param tagNr the Tag to read
     * @return the value as int
     */
    fun getHeaderIntegerValue(tagNr: Int): Int {
        return attributes?.getInt(tagNr, 0) ?: 0
    }

    /**
     * @param tagNr e.g. "0018,0050" to get Slice Thickness<br></br>
     * or "0008,0102#0054,0220" to get the Coding Scheme Designator after View Code Sequence
     * @return int
     */
    fun getHeaderIntegerValue(tagNr: String): Int {
        return getHeaderIntegerValue(toTagInt(tagNr))
    }

    /**
     * checks if the Header contains the given tag
     *
     * @param tagNr
     * @return
     */
    fun containsHeaderTag(tagNr: String): Boolean {
        return containsHeaderTag(toTagInt(tagNr))
    }

    /**
     * checks if the Header contains the given tag
     *
     * @param tagNr
     * @return
     */
    fun containsHeaderTag(tagNr: Int): Boolean {
        return attributes?.contains(tagNr) ?: false
    }

    /**
     * returns the name of the given Header field
     *
     * @param tagNr
     * @return the name of the Field e.g. Patients Name
     */
    fun getHeaderName(tagNr: String): String {
        return try {
            getHeaderName(toTagInt(tagNr))
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * returns the String representation of the given header field
     * if it exists in the header
     *
     * @param tagNr
     * @return
     */
    fun getHeader(tagNr: Int): String {
        return try {
            val dcmele: String = attributes?.getString(tagNr) ?: ""
            toElementString(dcmele, tagNr)
        } catch (e: Exception) {
            ""
        }
    }

    private fun toElementString(dcmele: String, tag: Int): String {
        val sb = StringBuffer()
        val TAG: IntArray = attributes?.tags() ?: IntArray(1)
        val append: StringBuffer = sb.append(TAG)
            .append(" [").append(attributes?.getVR(tag)).append("] ")
            .append(attributes?.tags()).append(": ")
            .append(dcmele)
        return sb.toString()
    }

    /**
     * checks wether the header is empty or not
     *
     * @return
     */
    val isEmpty: Boolean
        get() = attributes == null || attributes?.isEmpty == true

    /**
     * Round double after dot
     *
     * @param a : value convertor
     * @param n number of decade
     * @return new value
     */
    fun floor(a: Double, n: Int): Double {
        val p = Math.pow(10.0, n.toDouble())
        return Math.floor(a * p + 0.5) / p
    }

    /**
     * Giving pixel data
     *
     * @return
     * @throws IOException
     */
    @Throws(IOException::class)
    fun lattricePixelData2(): IntArray {
        return attributes?.getInts(Tag.PixelData) ?: IntArray(1)
    }

    /**
     * Giving pixel data
     *
     * @param dcmObj
     * @return
     * @throws IOException
     */
    @Throws(IOException::class)
    fun lattricePixelDataBytes(dcmObj: Attributes): ByteArray {
        return dcmObj.getBytes(Tag.PixelData)
    }

    /**
     * Giving pixel data
     *
     * @return
     * @throws IOException
     */
    @Throws(IOException::class)
    fun lattricePixelDataBytes2(): ByteArray {
        return attributes?.getBytes(Tag.PixelData) ?: ByteArray(1)
    }

    /**
     * Extraction PixelData
     *
     * @param raster of dicom
     * @return
     */
    private fun extractData(raster: Raster): Array<IntArray> {
        val w = raster.width
        val h = raster.height
        System.out.printf("w = %d h = %d%n", w, h)
        //WritableRaster raster = (WritableRaster) getMyImage();
        val data = Array(h) { IntArray(w) }
        for (y in 0 until h) {
            for (x in 0 until w) {
                data[y][x] = raster.getSample(x, y, 0)
            }
        }
        return data
    }

    /**
     * Extraction PixelData
     *
     * @return
     */
    private fun getPixelData(data2: Array<IntArray>): IntArray {
        val h = data2.size
        val w: Int = data2[0].size
        val array = IntArray(h * w)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val index = y * w + x
                array[index] = data2[y][x] //ligne
            }
        }
        return array
    }

    /**
     * Reading VR = SQ
     *
     * @param inputFile : File
     * @param tag       : VR =SQ
     * @return
     */
    fun readItem(inputFile: File, tag: Int): Array<String?> {
        val dcm = DisplayTag(inputFile)
        val seq: Sequence = dcm.attributes?.getSequence(tag) ?: return arrayOf("")
        val valueString = arrayOfNulls<String>(seq.size)
        for (i in seq.indices) {
            val attr = seq[i]
            valueString[i] = attr.toString()
        }
        return valueString
    }

    /**
     * Value inside VR = SQ
     *
     * @param inputFile : input File
     * @param tagSQ     : tag VR = SQ
     * @param tag       : Tag inside VR= SQ
     * @return
     */
    fun tagItem(inputFile: File, tagSQ: Int, tag: Int): String? {
        var valueString: String? = null
        val dcm = DisplayTag(inputFile)
        val seq: Sequence? = dcm.attributes?.getSequence(tagSQ)
        if (seq == null) {
            return ""
        }
        val attr = seq[0]
        valueString = attr.getString(tag)
        return valueString
    }

    /**
     * Les unités spécifiques selon les tags pour vr= SQ/ Unity specical for tags VR= SQ
     *
     * @param TAG    :
     * - RegionSpatialFormat
     * - RegionDataType
     * - PhysicalUnitsXDirection
     * - PhysicalUnitsXDirection
     * - PixelComponentPhysicalUnits
     * @param result : value string
     */
    fun unit(TAG: Int, result: String) {
        if (TAG == Tag.RegionSpatialFormat) {
            val2 = Integer.valueOf(result).toInt() //convertie en int
            when (val2) {
                5 -> setNounUnit("Graphics")
                4 -> setNounUnit("Wave form(physiological traces, doppler traces,...")
                3 -> setNounUnit("Spectral(CW or PW Doppler")
                2 -> setNounUnit("M-Mode(tissue or flow)")
                1 -> setNounUnit("2D(tissue or flow")
                0 -> setNounUnit("None or not applicable")
                else -> {}
            }
        } else if (TAG == Tag.RegionDataType) {
            val2 = Integer.valueOf(result).toInt() //convertie en int
            when (val2) {
                12 -> setNounUnit("Orther Physiological(Amplitude vs. Time)")
                11 -> setNounUnit("d(area)/dt")
                10 -> setNounUnit("Area Trace")
                9 -> setNounUnit("d(Volume)/dt Trace")
                8 -> setNounUnit("Volume Trace")
                7 -> setNounUnit("Doppler Max Trace")
                6 -> setNounUnit("Doppler Mode Trace")
                5 -> setNounUnit("Doppler Mean Trace")
                4 -> setNounUnit("CW Spectral Doppler")
                3 -> setNounUnit("PW Spectral Doppler")
                2 -> setNounUnit("Color Flow")
                1 -> setNounUnit("Tissue")
                0 -> setNounUnit("None or not applicable")
                else -> {}
            }
            when (result) {
                "A" -> setNounUnit("ECG Trace")
                "B" -> setNounUnit("Pulse Trace")
                "C" -> setNounUnit("Phonocardiogram Trace")
                "D" -> setNounUnit("Gray bar")
                "E" -> setNounUnit("Color bar")
                "F" -> setNounUnit("Integrated Backscatter")
                else -> return
            }
        } else if (TAG == Tag.PhysicalUnitsXDirection || TAG == Tag.PhysicalUnitsXDirection || TAG == Tag.PixelComponentPhysicalUnits) {
            val2 = Integer.valueOf(result).toInt() //convertie en int
            when (val2) {
                9 -> setNounUnit("cm*cm.pixel/sec")
                8 -> setNounUnit("cm*cm/pixel")
                7 -> setNounUnit("cm*pixel/sec")
                6 -> setNounUnit("dB*pixel/seconds")
                5 -> setNounUnit("hertz/pixel")
                4 -> setNounUnit("seconds/pixel")
                3 -> setNounUnit("cm/pixel")
                2 -> setNounUnit("dB/pixel")
                1 -> setNounUnit("percent/pixel")
                0 -> setNounUnit("None or not applicable")
                else -> {}
            }
            when (result) {
                "A" -> setNounUnit("cm*cm*cm/pixel")
                "B" -> setNounUnit("cm*cm*cm*pixel/sec")
                "C" -> setNounUnit("degrees")
            }
        } else if (TAG == Tag.PixelComponentDataType) {
            val2 = Integer.valueOf(result).toInt() //convertie en int
            when (val2) {
                9 -> setNounUnit("Computed Border")
                8 -> setNounUnit("Integrated Backscatter")
                7 -> setNounUnit("Color bar")
                6 -> setNounUnit("Gray bar")
                5 -> setNounUnit("Color Flow Intensity")
                4 -> setNounUnit("Color Flow Variance")
                3 -> setNounUnit("Color Flow Velocity")
                2 -> setNounUnit("Spectral doppler")
                1 -> setNounUnit("Tissue")
                0 -> setNounUnit("None or not applicable")
                else -> {}
            }
            if ("A" == result) {
                setNounUnit("Tissue Classification")
            }
        } else {
            setNounUnit("None or not applicable")
        }
    }

    /**
     * Enregistre l'unité des items/ Put unity of items
     *
     * @param nounUnit
     * @return this.nounUnit = nounUnit
     */
    fun setNounUnit(nounUnit: String): String {
        return nounUnit.also { this.nounUnit = it }
    }

    /**
     * Special Ratio Spatial toutes les unites sont en mm/ Giving tag ratio Spatial of mm
     *
     * @param TAG     : entree choisi
     * - PhysicalUnitsXDirection
     * - PhysicalUnitsYDirection
     * -PixelComponentPhysicalUnits
     * @param result: prend l'unite
     */
    fun unitRatioSpatial(TAG: Int, result: String?) {
        if (TAG == Tag.PhysicalUnitsXDirection || TAG == Tag.PhysicalUnitsYDirection || TAG == Tag.PixelComponentPhysicalUnits) {
            val2 = Integer.valueOf(result).toInt() //convertie en int
            when (val2) {
                9 -> {
                    val valueSpatial1 = valeurTagItemDoubleRatio!! * setFacteurPuissance(10.0, 1.0)
                    setTagItemDoubleRatio(valueSpatial1) //prend la valeur
                    setNounUnitRatio("mm*mm.pixel/sec")
                }
                8 -> {
                    val valueSpatial2 = valeurTagItemDoubleRatio!! * setFacteurPuissance(10.0, 1.0)
                    setTagItemDoubleRatio(valueSpatial2) //prend la valeur
                    setNounUnitRatio("mm*mm/pixel")
                }
                7 -> setNounUnitRatio("mm*pixel/sec")
                6 -> setNounUnitRatio("dB*pixel/seconds")
                5 -> setNounUnitRatio("hertz/pixel")
                4 -> setNounUnitRatio("seconds/pixel")
                3 -> setNounUnitRatio("mm/pixel")
                2 -> setNounUnitRatio("dB/pixel")
                1 -> setNounUnitRatio("percent/pixel")
                0 -> setNounUnitRatio("None or not applicable")
                else -> {}
            }
            when (result) {
                "A" -> {
                    val valueSpatial3 = valeurTagItemDoubleRatio!! * setFacteurPuissance(10.0, 2.0)
                    setTagItemDoubleRatio(valueSpatial3) //prend la valeur
                    setNounUnitRatio("mm*mm*mm/pixel")
                }
                "B" -> {
                    val valueSpatial4 = valeurTagItemDoubleRatio!! * setFacteurPuissance(10.0, 2.0)
                    setTagItemDoubleRatio(valueSpatial4) //prend la valeur
                    setNounUnit("mm*mm*mm*pixel/sec")
                }
                "C" -> setNounUnit("degrees")
            }
        }
    }

    /**
     * Prend la valeur d'un Ratio Spatial/Put value Ratio Spatial
     *
     * @param valueSpatial
     * @return
     */
    fun setTagItemDoubleRatio(valueSpatial: Double): Double {
        return valueSpatial.also { valeurTagItemDoubleRatio = it }
    }

    /**
     * Donne les valeurs calculer des puissances/ Put and computing power
     *
     * @param result3
     * @param facteur
     * @return
     * @return
     */
    fun setFacteurPuissance(result3: Double, facteur: Double): Double {
        return Math.pow(result3, facteur).also { facteurPuissance = it }
    }

    /**
     * Enregistre l'unite des items /Put unity unity items
     *
     * @return this.nounUnit = nounUnit
     */
    fun setNounUnitRatio(nounUnitRatio: String): String {
        return nounUnitRatio.also { this.nounUnitRatio = it }
    }

    /**
     * Prend la valeur interne d'un tag Item/ Put tag Item
     *
     * @param result
     * @return
     */
    fun setTagItem(result: String): String {
        return result.also { valeurTagItem = it }
    }

    /**
     * Prend la valeur interne d'un tag Item/ Put the value tag iteù
     *
     * @return
     */
    fun setTagItemDouble(result2: Double): Double {
        return result2.also { valeurTagItemDouble = it }
    }

    /**
     * reads a String value from tag dicom (dcm4che2)
     *
     * @param tagNr the Tag to read
     * @return the value as String
     * Returns the Specific Character Set defined by Attribute Specific Character Set (0008,0005)
     * of this or the root Data Set, if this is a Nested Data Set containing in a Sequence Eleme
     */
    fun getHeaderStringValue(tagNr: Int): String {
        return try {
            val elem: Attributes? = attributes
            elem?.setSpecificCharacterSet("GB18030")
            var value = elem?.getString(tagNr)
            if (value == null) {
                value = ""
            }
            value
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * reads a String value from tag dicom (dcm4che2)
     *
     * @param tagNr the Tag to read
     * @return the value as String
     * Returns the Specific Character Set defined by Attribute Specific Character Set (0008,0005)
     * of this or the root Data Set, if this is a Nested Data Set containing in a Sequence Eleme
     */
    fun getHeaderStringValues(tagNr: Int): Array<String>? {
        return try {
            println(222)
            val elem: Attributes? = attributes
            elem?.setSpecificCharacterSet("GB18030")
            elem?.getStrings(tagNr)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * reads a String value from the Dicomheader
     *
     * @param tagNr      the Tag to read
     * @param dcmelement
     * @return the value as String
     */
    fun getHeaderStringValue(dcmelement: Attributes, tagNr: Int): String {
        return try {
            println(333)
            /* dcmelement.setSpecificCharacterSet("ISO_IR 100"); */dcmelement.setSpecificCharacterSet("GB18030")
            var `val` = dcmelement.getString(tagNr)
            if (`val` == null) {
                `val` = ""
            }
            `val`
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * reads the tag (group,element)
     *
     * @param headerNr e.g. "0018,0050" to get Slice Thickness<br></br>
     * @return String
     */
    fun getHeaderStringValue(headerNr: String): String {
        var headerNr = headerNr
        headerNr = headerNr.replace("xx".toRegex(), "00").replace("XX".toRegex(), "00")
        return getHeaderStringValue(toTagInt(headerNr))
    }

    /**
     * Giving time a tag ("xxxx,")
     *
     * @param tagNr
     * @return
     */
    fun getHeaderTimeValue(tagNr: String): Time? {
        return getHeaderTimeValue(toTagInt(tagNr))
    }

    /**
     * Giving time a tag
     *
     * @param tagNr
     * @return time
     */
    fun getHeaderTimeValue(tagNr: Int): Time? {
        val time = getHeaderStringValue(tagNr)
        if (time.length != 6) {
            return null
        }
        try {
            val hour = time.substring(0, 2).toInt()
            val min = time.substring(2, 4).toInt()
            val sec = time.substring(4, 6).toInt()
            return Time(hour, min, sec)
        } catch (e: Exception) {
        }
        return null
    }

    /**
     * retrieves a specific HeaderTag that is inside anotehr tag
     * or "0008,0102, 0054,0220" to get the Coding Scheme Designator after View Code Sequence
     *
     * @return String
     * * @param tagHierarchy; e.g. {Tag.UID, Tag.SOPInstanceUID, Tag.CodeMeaning}
     * @return
     */
    fun getHeaderValueInsideTag(tagHierarchy: IntArray): String? {
        try {
            for (i in 0 until tagHierarchy.size - 1) {
                return attributes?.getString(tagHierarchy[i])
            }
        } catch (e: Exception) {
            var tags = ""
            var i = 0
            while (i < tagHierarchy.size) {
                tags += toTagString(tagHierarchy[i]) + " "
                i++
            }
            return ""
        }
        return null
    }

    /**
     * Create file output dicom
     *
     * @param fileOutput : file output
     * @throws IOException Example:
     */
    @Throws(IOException::class)
    fun writeTo(fileOutput: File, `object`: Attributes?) {
        writeTo(fileOutput, fileMetaInformation, `object`)
    }

    /**
     * Create file output dicom
     *
     * @param fileOutput : file output
     * @throws IOException Example:
     */
    @Throws(IOException::class)
    fun writeTo(fileOutput: File, fmi: Attributes?, `object`: Attributes?) {
        val dos = if (!fileOutput.name.endsWith(".dcm")) {
            DicomOutputStream(File("$fileOutput.dcm"))
        } else {
            DicomOutputStream(File("$fileOutput"))
        }
        dos.encodingOptions = encOpts
        dos.writeDataset(fmi, `object`)
        dos.finish()
        dos.flush()
        dos.close()
    }

    /**
     * Writting
     *
     * @param fileOutput
     * @param h
     * @param w
     * @throws IOException
     */
    @Throws(IOException::class)
    fun writeToSegment(fileOutput: File, h: Int, w: Int) {
        val dos = if (!fileOutput.name.endsWith(".dcm")) {
            DicomOutputStream(File("$fileOutput.dcm"))
        } else {
            DicomOutputStream(File("$fileOutput"))
        }
        dos.encodingOptions = encOpts
    }

    /**
     * Create overlay in pixelData
     *
     * @param object
     */
    fun overlayCreate(`object`: Attributes) {
        val position = `object`.getInt(Tag.OverlayBitPosition, 0)
        if (position == 0) {
            return
        }
        val bit = 1 shl position
        val pixels = `object`.getInts(Tag.PixelData)
        var count = 0
        for (pix in pixels) {
            val overlay = pix and bit
            pixels[count++] = pix - overlay
        }
        `object`.setInt(Tag.PixelData, VR.OW, *pixels)
    }

    /**
     * dicom.setString(Tag.PerformingPhysicianName, VR.PN, "Jean");
     * dicom.setString(Tag.AdmittingDiagnosesDescription, VR.LO, "CHU");
     * Sequence seq= dicom.newSequence(Tag.AnatomicRegionSequence,0);
     * Attributes dicom2 = new Attributes();
     *
     * @param dicom
     */
    fun setItem(dicom: Attributes, TagSequenceName: Int) {
        val seq = dicom.newSequence(TagSequenceName, 0)
        dicom.setString(Tag.CodingSchemeDesignator, VR.SH, "SRT")
        dicom.setString(Tag.CodeValue, VR.SH, "T-AA000")
        dicom.setString(Tag.CodeMeaning, VR.LO, "Eye")
        seq.add(dicom)
    } /* public static void (String[] args) throws Exception {
        File file = new File("C:\\Users\\fendo\\Documents\\WeChat Files\\fen_do\\Files\\1234.dcm");
        DisplayTag d = new DisplayTag(file);
        @SuppressWarnings("static-access")
        Attributes attrs = d.loadDicomObject(file);
        //输出所有属性信息
        System.out.println("所有信息: " + attrs);
        //获取行
        int row = attrs.getInt(Tag.Rows, 1);
        //获取列
        int columns = attrs.getInt(Tag.Columns, 1);
        //窗宽窗位
        float win_center = attrs.getFloat(Tag.WindowCenter, 1);
        float win_width = attrs.getFloat(Tag.WindowWidth, 1);
        System.out.println("" + "row=" + row + ",columns=" + row + ",row*columns = " + row * columns);
        String patientName = attrs.getString(Tag.PatientName, "");
        System.out.println("姓名：" + patientName);
        //生日
        String patientBirthDate = attrs.getString(Tag.PatientBirthDate, "");
        System.out.println("生日：" + patientBirthDate);
        //机构
        String institution = attrs.getString(Tag.InstitutionName, "");
        System.out.println("机构：" + institution);
        //站点
        String station = attrs.getString(Tag.StationName, "");
        System.out.println("站点：" + station);
        //制造商
        String Manufacturer = attrs.getString(Tag.Manufacturer, "");
        System.out.println("制造商：" + Manufacturer);
        //制造商模型
        String ManufacturerModelName = attrs.getString(Tag.ManufacturerModelName, "");
        System.out.println("制造商模型：" + ManufacturerModelName);
        //描述--心房
        String description = attrs.getString(Tag.StudyDescription, "");
        System.out.println("描述--心房：" + description);
        //描述--具体
        String SeriesDescription = attrs.getString(Tag.SeriesDescription, "");
        System.out.println("描述--具体：" + SeriesDescription);
        //描述时间
        String studyData = attrs.getString(Tag.StudyDate, "");
        System.out.println("描述时间：" + studyData);
        byte[] bytename = attrs.getBytes(Tag.PatientName);
        System.out.println("姓名: " + new String(bytename, "gb18030"));
        byte[] bytesex = attrs.getBytes(Tag.PatientSex);
        System.out.println("性别: " + new String(bytesex, "gb18030"));
    }*/
    // 建议重构时合并 attributes，暂时保留以维持兼容性


}
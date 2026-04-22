package top.elune.utils.filter.strategy

import org.dcm4che3.data.Attributes
import top.elune.utils.commons.CodeModule
import java.io.File

/**
 * Series 过滤策略接口
 * 实现类决定某一 DICOM Series 是否应该被跳过
 */
interface SeriesFilterStrategy {
    fun shouldSkip(attributes: Attributes, codeModule: CodeModule, src: File, parentFileCount: Int): Boolean
}

/**
 * 正则匹配策略：当指定 DICOM Tag 的值匹配正则时跳过
 */
class RegexMatchFilterStrategy(
    private val tag: Int,
    private val regex: Regex
) : SeriesFilterStrategy {
    override fun shouldSkip(attributes: Attributes, codeModule: CodeModule, src: File, parentFileCount: Int): Boolean {
        val value = attributes.getString(tag, "") ?: ""
        return value.matches(regex)
    }
}

/**
 * 正则不匹配策略：当指定 DICOM Tag 的值不匹配正则时跳过
 */
class RegexNotMatchFilterStrategy(
    private val tag: Int,
    private val regex: Regex
) : SeriesFilterStrategy {
    override fun shouldSkip(attributes: Attributes, codeModule: CodeModule, src: File, parentFileCount: Int): Boolean {
        val value = attributes.getString(tag, "") ?: ""
        return !value.matches(regex)
    }
}

/**
 * 文件数小于阈值策略
 */
class FileCountLtFilterStrategy(private val threshold: Int) : SeriesFilterStrategy {
    override fun shouldSkip(attributes: Attributes, codeModule: CodeModule, src: File, parentFileCount: Int): Boolean {
        return parentFileCount < threshold
    }
}

/**
 * 文件数小于等于阈值策略
 */
class FileCountLeFilterStrategy(private val threshold: Int) : SeriesFilterStrategy {
    override fun shouldSkip(attributes: Attributes, codeModule: CodeModule, src: File, parentFileCount: Int): Boolean {
        return parentFileCount <= threshold
    }
}

/**
 * 文件数大于阈值策略
 */
class FileCountGtFilterStrategy(private val threshold: Int) : SeriesFilterStrategy {
    override fun shouldSkip(attributes: Attributes, codeModule: CodeModule, src: File, parentFileCount: Int): Boolean {
        return parentFileCount > threshold
    }
}

/**
 * 文件数大于等于阈值策略
 */
class FileCountGeFilterStrategy(private val threshold: Int) : SeriesFilterStrategy {
    override fun shouldSkip(attributes: Attributes, codeModule: CodeModule, src: File, parentFileCount: Int): Boolean {
        return parentFileCount >= threshold
    }
}

/**
 * 文件数等于阈值策略
 */
class FileCountEqFilterStrategy(private val threshold: Int) : SeriesFilterStrategy {
    override fun shouldSkip(attributes: Attributes, codeModule: CodeModule, src: File, parentFileCount: Int): Boolean {
        return parentFileCount == threshold
    }
}

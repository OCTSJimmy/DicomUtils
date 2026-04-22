package top.elune.utils.filter.strategy

import org.dcm4che3.data.Attributes
import top.elune.utils.commons.CodeModule
import java.io.File

/**
 * 复合策略：所有子策略都返回 true 时才跳过（AND 逻辑）
 */
class AndFilterStrategy(private val children: List<SeriesFilterStrategy>) : SeriesFilterStrategy {
    override fun shouldSkip(attributes: Attributes, codeModule: CodeModule, src: File, parentFileCount: Int): Boolean {
        return children.all { it.shouldSkip(attributes, codeModule, src, parentFileCount) }
    }
}

/**
 * 复合策略：任一子策略返回 true 时就跳过（OR 逻辑）
 */
class OrFilterStrategy(private val children: List<SeriesFilterStrategy>) : SeriesFilterStrategy {
    override fun shouldSkip(attributes: Attributes, codeModule: CodeModule, src: File, parentFileCount: Int): Boolean {
        return children.any { it.shouldSkip(attributes, codeModule, src, parentFileCount) }
    }
}

/**
 * 复合策略：子策略返回 false 时跳过（NOT 逻辑）
 */
class NotFilterStrategy(private val child: SeriesFilterStrategy) : SeriesFilterStrategy {
    override fun shouldSkip(attributes: Attributes, codeModule: CodeModule, src: File, parentFileCount: Int): Boolean {
        return !child.shouldSkip(attributes, codeModule, src, parentFileCount)
    }
}

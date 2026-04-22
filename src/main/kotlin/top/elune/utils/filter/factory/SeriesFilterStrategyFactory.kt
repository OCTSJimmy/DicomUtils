package top.elune.utils.filter.factory

import org.dcm4che3.data.Tag
import top.elune.utils.filter.strategy.*
import java.util.*

/**
 * 策略配置说明：
 *   - filter.strategy[N] 定义第 N 个策略，策略之间是 OR 关系
 *   - 任一策略返回 true 则跳过该 Series
 *   - type 可选值：regex_match, regex_not_match, file_count_lt, file_count_le, file_count_gt, file_count_ge, file_count_eq, and, or, not
 *   - and/or 是复合策略，需要配置 children[N] 子策略
 *   - not 是单个子策略，需要配置 child 子策略
 *
 * Series 过滤策略工厂
 * 从 Properties 中解析并创建策略对象树
 *
 * 配置格式示例：
 * ```
 * filter.strategy[0].type=regex_match
 * filter.strategy[0].tag=SeriesDescription
 * filter.strategy[0].pattern=.*3D Saved State.*
 * filter.strategy[0].ignoreCase=true
 *
 * filter.strategy[1].type=and
 * filter.strategy[1].children[0].type=regex_match
 * filter.strategy[1].children[0].tag=SeriesDescription
 * filter.strategy[1].children[0].pattern=.*Blood Flow.*
 * filter.strategy[1].children[1].type=file_count_lt
 * filter.strategy[1].children[1].threshold=100
 * ```
 */
object SeriesFilterStrategyFactory {

    fun createStrategies(properties: Properties): List<SeriesFilterStrategy> {
        val keys = properties.keys.map { it.toString() }.filter { it.startsWith("filter.strategy[") }
        if (keys.isEmpty()) {
            return emptyList()
        }

        val indices = keys.mapNotNull { key ->
            Regex("""filter\.strategy\[(\d+)]""").find(key)?.groupValues?.get(1)?.toInt()
        }.distinct().sorted()

        return indices.map { index ->
            createStrategy(properties, "filter.strategy[$index]")
        }
    }

    private fun createStrategy(properties: Properties, prefix: String): SeriesFilterStrategy {
        val type = properties.getProperty("$prefix.type")
            ?: throw IllegalArgumentException("Missing type for strategy: $prefix")

        return when (type.lowercase()) {
            "regex_match" -> createRegexMatchStrategy(properties, prefix)
            "regex_not_match" -> createRegexNotMatchStrategy(properties, prefix)
            "file_count_lt" -> createFileCountStrategy(properties, prefix) { threshold ->
                FileCountLtFilterStrategy(
                    threshold
                )
            }

            "file_count_le" -> createFileCountStrategy(properties, prefix) { threshold ->
                FileCountLeFilterStrategy(
                    threshold
                )
            }

            "file_count_gt" -> createFileCountStrategy(properties, prefix) { threshold ->
                FileCountGtFilterStrategy(
                    threshold
                )
            }

            "file_count_ge" -> createFileCountStrategy(properties, prefix) { threshold ->
                FileCountGeFilterStrategy(
                    threshold
                )
            }

            "file_count_eq" -> createFileCountStrategy(properties, prefix) { threshold ->
                FileCountEqFilterStrategy(
                    threshold
                )
            }

            "and" -> AndFilterStrategy(createChildren(properties, prefix))
            "or" -> OrFilterStrategy(createChildren(properties, prefix))
            "not" -> NotFilterStrategy(createChild(properties, prefix))
            else -> throw IllegalArgumentException("Unknown strategy type: $type")
        }
    }

    private fun createRegexMatchStrategy(properties: Properties, prefix: String): RegexMatchFilterStrategy {
        val tagName = properties.getProperty("$prefix.tag", "SeriesDescription")
        val pattern = properties.getProperty("$prefix.pattern")
            ?: throw IllegalArgumentException("Missing pattern for regex_match strategy: $prefix")
        val ignoreCase = properties.getProperty("$prefix.ignoreCase", "false").toBoolean()
        val regex = if (ignoreCase) pattern.toRegex(RegexOption.IGNORE_CASE) else pattern.toRegex()
        return RegexMatchFilterStrategy(resolveTag(tagName), regex)
    }

    private fun createRegexNotMatchStrategy(properties: Properties, prefix: String): RegexNotMatchFilterStrategy {
        val tagName = properties.getProperty("$prefix.tag", "SeriesDescription")
        val pattern = properties.getProperty("$prefix.pattern")
            ?: throw IllegalArgumentException("Missing pattern for regex_not_match strategy: $prefix")
        val ignoreCase = properties.getProperty("$prefix.ignoreCase", "false").toBoolean()
        val regex = if (ignoreCase) pattern.toRegex(RegexOption.IGNORE_CASE) else pattern.toRegex()
        return RegexNotMatchFilterStrategy(resolveTag(tagName), regex)
    }

    private fun createFileCountStrategy(
        properties: Properties,
        prefix: String,
        factory: (Int) -> SeriesFilterStrategy
    ): SeriesFilterStrategy {
        val threshold = properties.getProperty("$prefix.threshold")?.toInt()
            ?: throw IllegalArgumentException("Missing threshold for file_count strategy: $prefix")
        return factory(threshold)
    }

    private fun createChildren(properties: Properties, prefix: String): List<SeriesFilterStrategy> {
        val childKeys = properties.keys.map { it.toString() }.filter { it.startsWith("$prefix.children[") }
        val indices = childKeys.mapNotNull { key ->
            Regex("""$prefix\.children\[(\d+)]""").find(key)?.groupValues?.get(1)?.toInt()
        }.distinct().sorted()

        return indices.map { index ->
            createStrategy(properties, "$prefix.children[$index]")
        }
    }

    private fun createChild(properties: Properties, prefix: String): SeriesFilterStrategy {
        return createStrategy(properties, "$prefix.child")
    }

    private fun resolveTag(tagName: String): Int {
        return when (tagName) {
            "SeriesDescription" -> Tag.SeriesDescription
            else -> {
                try {
                    Tag::class.java.getField(tagName).getInt(null)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Unknown DICOM tag: $tagName")
                }
            }
        }
    }
}

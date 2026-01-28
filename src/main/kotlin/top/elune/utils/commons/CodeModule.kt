package top.elune.utils.commons

/**
 * 初始化CodeM对象
 *
 * @param mSubjectCode 原始受试者编号
 * @param mvSubjectCode 脱敏受试者编号
 *
 */
open class CodeModule(
    var mSubjectNumber: String,
    private var mSubjectCode: String? = String.format(mSubjectNumber),
    var mvSubjectNumber: String?,
    private var mvSubjectCode: String? = String.format("$mvSubjectNumber"),
    private var mIsDone: Boolean = false,
) : Cloneable {
    var name: String? = null

    fun done() {
        mIsDone = true
    }

    fun isDone(): Boolean {
        return mIsDone
    }

    val originSubjectCode: String
        get() = mSubjectCode ?: ""

    val desensitizedSubjectCode: String
        get() = mvSubjectCode ?: ""

    public override fun clone(): CodeModule {
        return super.clone() as CodeModule
    }
}
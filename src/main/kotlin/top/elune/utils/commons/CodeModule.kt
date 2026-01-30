package top.elune.utils.commons

/**
 * 初始化CodeM对象
 *
 * @param mSubjectNumber 原始受试者编号
 * @param mvSubjectNumber 脱敏受试者编号
 *
 */
open class CodeModule(
    private var mSiteCode: String,
    private var mSubjectCode: String,
    private var mSubjectNumber: String? = String.format("%s-%s", mSiteCode, mSubjectCode),
    private var mvSiteCode: String,
    private var mvSubjectCode: String?,
    private var mvSubjectNumber: String? = String.format("%s-%s", mvSiteCode, mvSubjectCode),
    private var mIsDone: Boolean = false,
) : Cloneable {
    var name: String? = null

    fun done() {
        mIsDone = true
    }

    fun isDone(): Boolean {
        return mIsDone
    }

    val originSubjectNumber: String
        get() = mSubjectNumber ?: ""

    val desensitizedSubjectNumber: String
        get() = mvSubjectNumber ?: ""

    val originSiteCode: String
        get() = mSiteCode

    val originSubjectCode: String
        get() = mSubjectCode

    val desensitizedSiteCode: String
        get() = mvSiteCode

    val desensitizedSubjectCode: String
        get() = mvSubjectCode ?: ""

    public override fun clone(): CodeModule {
        return super.clone() as CodeModule
    }
}
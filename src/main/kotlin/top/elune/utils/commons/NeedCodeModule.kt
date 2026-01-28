package top.elune.utils.commons

class NeedCodeModule @JvmOverloads constructor(
    private var mSiteCode: String,
    private var mCode: String,
    var codeModule: CodeModule? = CodeManager.INSTANCE["$mSiteCode-$mCode"],
) {
    var siteCode: String
        get() = mSiteCode
        set(siteCode) {
            mSiteCode = siteCode
            codeModule = CodeManager.INSTANCE["$siteCode-$mCode"]
        }
    var code: String
        get() = mCode
        set(code) {
            mCode = code
            codeModule = CodeManager.INSTANCE["$mSiteCode-$code"]
        }
    val fullCode: String
        get() = String.format("%s-%s", mSiteCode, mCode)
}
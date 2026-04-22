#!/usr/bin/env kotlin

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

// 核心逻辑：定义一个“盲目信任”的管理器
val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
})

// 劫持 JVM 全局 SSLContext
try {
    val sc = SSLContext.getInstance("SSL")
    sc.init(null, trustAllCerts, SecureRandom())

    // 1. 设置为默认 SSLContext (Gradle 内部 HttpClient 可能会用到)
    SSLContext.setDefault(sc)
    // 2. 设置 HttpsURLConnection 的默认工厂 (插件下载可能会用到)
    HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)

    // 3. 忽略域名校验 (防止自签证书域名不匹配)
    HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }

    println(">>> [WARNING] SSL Certificate verification disabled globally for this build.")
} catch (e: Exception) {
    println(">>> [ERROR] Err in custom ssl module.")
    e.printStackTrace()
}
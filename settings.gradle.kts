pluginManagement {
    repositories {
//         【关键】这里配置插件专用的仓库
//        maven {
//            url = uri("http://172.30.100.20:8081/repository/gradle-plugin/")
//            isAllowInsecureProtocol = true // 如果是HTTP
//        }
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }

//         官方源兜底（可选）
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "DicomUtils"


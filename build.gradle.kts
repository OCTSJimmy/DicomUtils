import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    application
}

group = "top.elune.utils"
version = "2.4-SNAPSHOT"

repositories {
    maven {
        url = uri("https://maven.dcm4che.org/")
        isAllowInsecureProtocol = true
    }
    maven {
        url = uri("https://repo1.maven.org/maven2/")
        isAllowInsecureProtocol = true
    }
    google()
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("commons-io:commons-io:2.14.0")
//    implementation("org.slf4j:slf4j-simple:1.7.36")
    implementation("org.dcm4che:dcm4che-core:5.28.0")
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "21"
}

application {
    mainClass.set("top.elune.utils.MainKt")
}
//tasks.jar {
//    destinationDirectory.set(file(project.findProperty("outputDir") ?: "D:\\JavaProjects\\DicomUtils\\release"))
//    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
//    manifest {
//        attributes["Main-Class"] = application.mainClass.get()
//    }
//    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
//}
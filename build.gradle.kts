import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.21"
    application
}

group = "top.elune.utils"
version = "4.7-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://www.dcm4che.org/maven2")
    }
    maven {
        url = uri("https://plugins.gradle.org/m2/")
    }
    google()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.10.1"))
    implementation("com.google.guava:guava:32.1.3-jre")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("commons-io:commons-io:2.14.0")
    implementation("org.dcm4che:dcm4che-core:5.28.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile> {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

application {
    mainClass.set("top.elune.utils.MainKt")
}
tasks.jar {
//    destinationDirectory.set(file("E:\\CT-GenAI\\Others\\CT-GenAI\\release"))
    destinationDirectory.set(
        file(
            project.findProperty("outputDir") ?: "D:\\JavaProjects-DicomUtils\\DicomUtils\\release"
        )
    )
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
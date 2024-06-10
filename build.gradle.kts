// import com.alcosi.gradle.dependency.group.JsonGroupedGenerator
// import com.alcosi.gradle.dependency.group.MDGroupedGenerator
// import com.github.jk1.license.LicenseReportExtension

/*
 * MIT License
 *
 * Copyright (c) 2024 BreninSul
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
/**
 * This plugin is only used to generate DEPENDENCIES.md file
 */
// buildscript {
//    repositories {
//        maven {
//            name = "GitHub"
//            url = uri("https://maven.pkg.github.com/alcosi/gradle-dependency-license-page-generator")
//            credentials {
//                username = "${System.getenv()["GIHUB_PACKAGE_USERNAME"] ?: System.getenv()["GITHUB_PACKAGE_USERNAME"]}"
//                password = "${System.getenv()["GIHUB_PACKAGE_TOKEN"] ?: System.getenv()["GITHUB_PACKAGE_TOKEN"]}"
//            }
//        }
//    }
//    dependencies {
//        classpath("com.alcosi:dependency-license-page-generator:1.0.0")
//    }
// }

plugins {
    id("idea")
    id("java-library")
    id("org.springframework.boot") version "3.3.0"
    id("io.spring.dependency-management") version "1.1.4"
    id("org.jetbrains.kotlin.jvm") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.spring") version "2.0.0"
    id("org.jetbrains.dokka") version "1.9.20"
    id("org.jetbrains.kotlin.kapt") version "2.0.0"
    id("com.github.jk1.dependency-license-report") version "2.8"
}

val gitUsername = "${System.getenv()["GIHUB_PACKAGE_USERNAME"] ?: System.getenv()["GITHUB_PACKAGE_USERNAME"]}"
val gitToken = "${System.getenv()["GIHUB_PACKAGE_TOKEN"] ?: System.getenv()["GITHUB_PACKAGE_TOKEN"]}"

group = "io.github.breninsul"
version = "1.1"

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}
java {
    withSourcesJar()
    withJavadocJar()
}
configurations {
    configureEach {
        exclude(module = "flyway-core")
        exclude(module = "logback-classic")
        exclude(module = "log4j-to-slf4j")
    }
}

dependencies {
    implementation("com.alcosi:commons-library-basic-dependency:3.3.0.4.0.5")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven {
        url = uri("https://repo1.maven.org/maven2")
    }
    maven {
        name = "GitHub"
        url = uri("https://maven.pkg.github.com/alcosi/alcosi_commons_library")
        credentials {
            username = gitUsername
            password = gitToken
        }
    }
    maven { url = uri("https://jitpack.io") }
    maven {
        name = "sonatype"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

tasks.named("generateLicenseReport") {
    outputs.upToDateWhen { false }
}
/*
 * This plugin is only used to generate DEPENDENCIES.md file
 */
// licenseReport {
//    unionParentPomLicenses = false
//    outputDir = "$projectDir/reports/license"
//    configurations = LicenseReportExtension.ALL
//    excludeOwnGroup = false
//    excludeBoms = false
//    renderers =
//        arrayOf(
//            JsonGroupedGenerator("group-report.json", onlyOneLicensePerModule = false),
//            MDGroupedGenerator(
//                "../../DEPENDENCIES.md",
//                onlyOneLicensePerModule = false,
//                header =
//                    """
// # Project Licensing
//
// This project is licensed under the MIT License. See the `LICENSE` file for details.
//
// ## Project Dependencies and Licenses:
//
//                    """,
//            ),
//        )
// }

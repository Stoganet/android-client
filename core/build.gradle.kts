plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.protobuf)
}

android {
    namespace = "com.stoganet.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
    }

    testFixtures {
        enable = true
    }

    testOptions {
        unitTests {
            all {
                it.useJUnitPlatform()
                it.jvmArgs("-Xmx1g")
            }
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*")
    }
}

// sourceSets["main"].kotlin.srcDirs(...) throws a ClassCastException on library modules
// under AGP 9.3.1 (AndroidLibrarySourceSet decorator bug); the variant API works around it.
androidComponents {
    onVariants { variant ->
        variant.sources.java?.addStaticSourceDirectory("build/generated/java/generateDebugProto/java")
        variant.sources.kotlin?.addStaticSourceDirectory("build/generated/openapi/src/main/kotlin")
    }
}

kotlin {
    jvmToolchain(25)
    // Keep JDK 25 toolchain but target JVM 21 to match compileOptions and stay within
    // Robolectric's ASM version support ceiling (class file major version 65).
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
}

openApiGenerate {
    generatorName.set("kotlin")
    library.set("jvm-retrofit2")
    inputSpec.set("${rootDir}/openapi/openapi.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.absolutePath)
    apiPackage.set("com.stoganet.core.api")
    modelPackage.set("com.stoganet.core.api.model")
    packageName.set("com.stoganet.core.api")
    configOptions.set(
        mapOf(
            "useCoroutines" to "true",
            "serializationLibrary" to "kotlinx_serialization",
            "enumPropertyNaming" to "UPPERCASE",
            "omitGradleWrapper" to "true",
        ),
    )
}

val openApiOutDir = layout.buildDirectory.dir("generated/openapi")

tasks.named("openApiGenerate") {
    val unusedSupportingFiles = listOf(
        "src/main/kotlin/com/stoganet/core/api/infrastructure/ApiClient.kt",
        "src/main/kotlin/com/stoganet/core/api/auth",
        "src/main/kotlin/com/stoganet/core/api/DefaultApi.kt",
        "src/main/kotlin/com/stoganet/core/api/infrastructure/ResponseExt.kt",
    )
    val outDirProvider = openApiOutDir
    doLast {
        val out = outDirProvider.get().asFile
        unusedSupportingFiles.forEach { rel ->
            val target = File(out, rel)
            if (target.exists()) {
                target.deleteRecursively()
            } else {
                logger.warn("openApiGenerate cleanup: expected path not found (generator output may have changed): $target")
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn("openApiGenerate", "generateDebugProto")
}

tasks.register("generateSources") {
    dependsOn("openApiGenerate", "generateDebugProto")
    group = "build setup"
    description = "Regenerate all generated sources after clean"
}

tasks.matching { it.name.startsWith("detekt") }.configureEach {
    dependsOn("openApiGenerate", "generateDebugProto")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.asProvider().get()}"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") { option("lite") }
            }
        }
    }
}

dependencies {
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.logging)
    implementation(libs.serialization.json)

    implementation(libs.datastore)
    implementation(libs.protobuf.javalite)
    implementation(libs.tink.android)

    testFixturesApi(libs.datastore)
    testFixturesApi(libs.protobuf.javalite)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
}

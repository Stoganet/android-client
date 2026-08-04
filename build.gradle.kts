plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.openapi.generator) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.detekt)
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    source.setFrom(files("app/src/main/kotlin", "app/src/test/kotlin"))
}

tasks.named("detekt") {
    dependsOn(":app:openApiGenerate", ":app:generateDebugProto")
}

dependencies {
    detektPlugins(libs.detekt.ktlint.wrapper)
    detektPlugins(libs.detekt.compose.rules)
}

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
}

// Bütün testler tek komutla: ./gradlew tumTestler
tasks.register("tumTestler") {
    group = "verification"
    description = "Ortak model, backend API (gerçek PostgreSQL) ve mobil ViewModel testlerini çalıştırır."
    dependsOn(":shared:jvmTest", ":server:test", ":composeApp:testAndroidHostTest")
}

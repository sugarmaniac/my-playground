import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
}

// Derlemeye giren, gizli olmayan ayarlar (gradle.properties veya -P<ad>=<değer>):
// - sosyaloyunSunucuAdresi: uygulamanın bağlanacağı server; boşsa yerel geliştirme server'ı.
// - sosyaloyunGoogleSunucuIstemciKimligi: Google ile girişin web (server) OAuth istemci kimliği; boşsa Google ile giriş gizlenir.
val derlemeAyarlari = mapOf(
    "SUNUCU_ADRESI" to providers.gradleProperty("sosyaloyunSunucuAdresi").orElse(""),
    "GOOGLE_SUNUCU_ISTEMCI_KIMLIGI" to providers.gradleProperty("sosyaloyunGoogleSunucuIstemciKimligi").orElse(""),
)
val derlemeAyarlariniUret by tasks.registering {
    val ayarlar = derlemeAyarlari
    val hedef = layout.buildDirectory.dir("generated/derlemeAyarlari/kotlin")
    ayarlar.forEach { (ad, deger) -> inputs.property(ad, deger) }
    outputs.dir(hedef)
    doLast {
        val dosya = hedef.get().file("com/sosyaloyun/app/api/DerlemeAyarlari.kt").asFile
        dosya.parentFile.mkdirs()
        val sabitler = ayarlar.entries.joinToString("\n") { (ad, deger) ->
            "    const val $ad: String = \"${deger.get()}\""
        }
        dosya.writeText(
            "package com.sosyaloyun.app.api\n\n" +
                "/** Derleme sırasında Gradle ayarlarından üretilir; elle düzenlenmez. */\n" +
                "internal object DerlemeAyarlari {\n$sabitler\n}\n",
        )
    }
}

// Ortak Compose arayüzü ve ViewModel katmanı; Android'de androidApp'e, iOS'ta ComposeApp framework'ü olarak iosApp'e girer.
kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    android {
        namespace = "com.sosyaloyun.app.compose"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {}
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.multiplatform.settings.noArg)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.playServicesAuth)
            implementation(libs.googleid)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain {
            kotlin.srcDir(derlemeAyarlariniUret)
        }
        commonMain.dependencies {
            implementation(projects.shared)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)

            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.kotlinx.coroutines.core)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.multiplatform.settings)
            implementation(libs.navigation.compose)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.sosyaloyun.app.resources"
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

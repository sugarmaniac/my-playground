# R8 kuralları (#63). kotlinx.serialization, Coil ve Compose kendi kurallarını kütüphaneyle getirir.

# Ktor istemci motoru ServiceLoader ile bulunur (HttpClient { } motorsuz kurulur).
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer { *; }
-keep class * implements io.ktor.client.HttpClientEngineContainer { *; }

# Credential Manager, Play Services sağlayıcısını yansımayla yükler.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }

# Tür güvenli gezinti rotaları serileştirici adıyla eşleşir; ad kısaltılmasın.
-keepnames @kotlinx.serialization.Serializable class com.sosyaloyun.**

# Masaüstü ve sunucu tarafı sınıflara yalnız Ktor/OkHttp içinde başvurulur.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

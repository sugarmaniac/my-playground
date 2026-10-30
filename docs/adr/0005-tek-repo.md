# Mobil, backend ve web tek repoda

Repo kökü KMP Wizard ile oluşturulan Kotlin projesi: `composeApp` (ortak arayüz), `iosApp`, `shared` (API modelleri, mobil ile backend arasındaki sözleşme) ve `server` (Ktor). Statik web sitesi Gradle projesinin dışında, `web/` klasöründe duruyor. API sözleşmesi tek bir modülde yaşadığı için backend ve mobil değişiklikleri aynı commit'te birlikte ilerliyor.

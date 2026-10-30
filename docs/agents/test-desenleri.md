# Test desenleri

Bütün testler tek komutla çalışır: `./gradlew tumTestler`. Backend testleri Docker ister (Testcontainers); cloud oturumlarında SessionStart hook'u Docker daemon'ını başlatır.

## Backend: HTTP API

- Testler `server/src/test` altında, `apiTesti { ... }` ile yazılır: backend gerçek PostgreSQL'e (tek paylaşılan Testcontainers konteyneri) karşı ayağa kalkar, her test boş tablolarla başlar.
- `apiTesti` içinde `client` gerçek HTTP istekleri atar; `saat` (`SahteSaat`) ileri sarılabilir; `ayarlar` ile veritabanındaki ayarlar değiştirilir.
- Yalnızca dışarıdan gözlenen davranış doğrulanır: yanıt kodu ve gövdesi. Tablo yapısı veya iç sınıflar test edilmez.
- Şema değişiklikleri `server/src/main/resources/db/migration/V<n>__<ad>.sql` Flyway göçleridir; uygulanmış bir göç dosyası değiştirilmez.

## Dış servis adaptörleri

- Her dış servis (IGDB, Steam, Apple/Google, push, e-posta) `server` içinde bir arayüzdür; gerçek uygulaması anahtarını ortam değişkeninden okur.
- Arayüz `Bagimliliklar`'a bir alan olarak eklenir: `Bagimliliklar.ortamdan()` gerçeğini, `apiTesti` sahtesini (`server/src/test/.../test/Sahte<Ad>.kt`) verir.
- Zaman her zaman `bagimliliklar.saat`'ten okunur; `Clock.System` doğrudan çağrılmaz.

## Ayarlar

Eşikler ve sınırlar `Ayar` alt tipleridir (anahtar + varsayılan değer) ve `ayar` tablosunda tutulur; veritabanında değer yoksa varsayılan geçerlidir.

## Mobil: ViewModel

- Testler `composeApp/src/commonTest` altında, `SahteApiIstemcisi`'ne karşı yazılır ve `./gradlew :composeApp:testAndroidHostTest` ile JVM'de çalışır.
- `Dispatchers.setMain(StandardTestDispatcher())` ve `runTest` + `advanceUntilIdle()` kullanılır; ViewModel'in yaydığı `StateFlow` durumu doğrulanır.
- Arayüz (Compose) otomatik test edilmez.

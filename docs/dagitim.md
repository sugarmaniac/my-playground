# Dağıtım

## Railway test ortamı

Backend, `main`'e her merge'de Railway'deki test ortamına kendiliğinden dağıtılır (ADR 0009). Repo bağlantısı Railway'de kurulu; derleme ve sağlık kontrolü ayarları repodaki `railway.json`'da:

- İmaj kök dizindeki `Dockerfile` ile derlenir (yalnızca `server` ve `shared`; mobil modüller imaja girmez). Statik web sitesi (`web/`, ADR 0004) imaja kopyalanır ve server tarafından kök adreste sunulur; paylaşım linkleri bu adrese gider.
- Yalnızca backend'i etkileyen yollar (`server/`, `shared/`, `web/`, Gradle dosyaları, `Dockerfile`, `railway.json`) değişince yeniden dağıtılır.
- Sağlık kontrolü `GET /saglik`; yanıt veritabanından okunan bakım modu ayarını içerdiği için veritabanı bağlantısını da doğrular.
- Şema göçleri (Flyway) server açılırken uygulanır.
- Oyun kataloğu IGDB'den server açılırken ve sonra günde bir sayfa sayfa içeri alınır; arada IGDB webhook'ları (`/igdb/webhook/{create|update|delete}`) güncel tutar. Kullanıcı istekleri IGDB'ye gitmez (ADR 0001).

### Test ortamında yalnızca sahte veri

**Railway test ortamına gerçek kişisel veri girilmez:** gerçek kullanıcı kaydı yapılmaz, gerçek rehber eşleştirmesi ve gerçek Steam hesabı bağlanmaz. Railway'in Türkiye'de bölgesi yok; kişisel veri Türkiye'deki VDS'te tutulur (ADR 0003, ADR 0009). Gerçek kullanıcılar yalnızca kapalı betada, VDS'e gelir (#26). Test ortamında Apple/Google ile giriş yalnızca bu iş için açılmış test hesaplarıyla yapılır; kişisel hesaplarla giriş yapılmaz.

### Railway'de gereken ayarlar

Değerler repoya yazılmaz; Railway'in servis ayarlarına girilir. Adımları `./scripts/insan-adimlari.sh` tek tek yürütür ve sonunda sağlık kontrolünü doğrular.

| Ayar | Nerede | Değer |
| --- | --- | --- |
| PostgreSQL servisi | Railway projesi | Projeye bir PostgreSQL veritabanı eklenir. |
| `PGHOST` | Backend servisi → Variables | `${{Postgres.PGHOST}}` (PostgreSQL servisine referans) |
| `PGPORT` | Backend servisi → Variables | `${{Postgres.PGPORT}}` |
| `PGDATABASE` | Backend servisi → Variables | `${{Postgres.PGDATABASE}}` |
| `PGUSER` | Backend servisi → Variables | `${{Postgres.PGUSER}}` |
| `PGPASSWORD` | Backend servisi → Variables | `${{Postgres.PGPASSWORD}}` |
| `PORT` | Railway kendisi verir | Girilmez. |
| `WEB_DIZINI` | İsteğe bağlı | Statik web sitesinin dizini; varsayılan `web` (imajda `/uygulama/web`). Dizin yoksa web sitesi sunulmaz. |
| `KURUCU_DAVET_KODLARI` | Backend servisi → Variables | Betanın ilk kullanıcılarının katılacağı, sahibi olmayan tek kullanımlık davet kodları (virgülle). Açılışta eklenir; var olan kod değişmez. |
| `IGDB_ISTEMCI_KIMLIGI` | Backend servisi → Variables | Twitch geliştirici uygulamasının Client ID'si. Boşsa oyun kataloğu içe aktarılmaz. |
| `IGDB_ISTEMCI_SIRRI` | Backend servisi → Variables | Twitch uygulamasının Client Secret'ı. |
| `IGDB_WEBHOOK_SIRRI` | Backend servisi → Variables | IGDB webhook'larının `X-Secret` başlığında gönderdiği değer; webhook kaydında aynısı verilir. |
| `APPLE_KIMLIK_KITLELERI` | Backend servisi → Variables | İsteğe bağlı; Apple token'larının kabul edilen kitleleri (virgülle). Varsayılan `com.sosyaloyun.app`. |
| `GOOGLE_KIMLIK_KITLELERI` | Backend servisi → Variables | Google web (server) OAuth istemci kimliği; birden fazlaysa virgülle. Boşsa Google ile giriş reddedilir. |
| `MODERASYON_SIFRESI` | Backend servisi → Variables | `/moderasyon` sayfasının şifresi (HTTP Basic). Boşsa sayfa kapalı. |
| `RESEND_API_KEY` | Backend servisi → Variables | Resend API anahtarı. Boşsa ekibe e-posta gitmez. |
| `MODERASYON_EPOSTASI` | Backend servisi → Variables | Ekibin e-posta adresi: yeni şikayetler ve pazartesi sabahı haftalık doğrulama metrikleri buraya gider. |
| `EPOSTA_GONDEREN` | Backend servisi → Variables | Resend'de doğrulanmış gönderen adresi; boşsa Resend'in deneme adresi. |
| Public domain | Backend servisi → Settings → Networking | Bir `*.up.railway.app` adresi oluşturulur. |

Backend'in okuduğu bütün ortam değişkenleri `server/src/main/kotlin/com/sosyaloyun/server/Bagimliliklar.kt` içindeki `Bagimliliklar.ortamdan()`'dadır; yeni bir dış servis eklendiğinde bu tabloya da eklenir.

## Mobil uygulamanın bağlandığı server

Uygulama, derlemede verilen `sosyaloyunSunucuAdresi` Gradle ayarındaki server'a bağlanır:

- Boşsa (varsayılan) yerel geliştirme server'ı: Android emülatörde `http://10.0.2.2:8080`, iOS simülatörde `http://localhost:8080`. Yerel server: `./gradlew :server:run` (yerelde bir PostgreSQL ve `PG*` değişkenleri gerekir).
- Google ile giriş: `sosyaloyunGoogleSunucuIstemciKimligi` ayarı (web OAuth istemci kimliği, gizli değil); boşsa Android'de Google ile giriş düğmesi gösterilmez.
- Test yapılandırması: Railway'in public adresi `gradle.properties`'teki `sosyaloyunSunucuAdresi`'ne yazılır ya da derlemede verilir: `./gradlew :androidApp:assembleDebug -PsosyaloyunSunucuAdresi=https://<servis>.up.railway.app`.

## Yerelde imaj

```bash
docker build -t sosyaloyun-server .
docker run -p 8080:8080 -e PGHOST=... -e PGUSER=... -e PGPASSWORD=... -e PGDATABASE=... sosyaloyun-server
```

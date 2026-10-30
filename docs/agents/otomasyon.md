# Otomatik ticket döngüsü

Bu repo, zamanlanmış cloud oturumları tarafından ticket ticket ve insan müdahalesi olmadan geliştiriliyor. Bir oturum başladığında şu kurallara uyar. Amaç: uygulamayı spec #1'deki MVP'ye kadar tamamlamak.

## Repolar

- **Kod, PR ve CI:** `sugarmaniac/my-playground` (public). Public repoda GitHub Actions (macOS dahil) ücretsiz olduğu için geliştirme burada yapılır. Geçmiş temiz başlatıldı.
- **Issue'lar:** `sugarmaniac/Sosyal-Oyun-Platformu` (private). Spec, ticket'lar, kararlar ve insan adımları (#27) orada kalır. Bu belgede ve ticket'larda geçen `#<n>` numaraları o repodaki issue'lardır.
- **Eşitleme:** private reponun `main`'i Railway dağıtımını besler. my-playground'da her merge'ten sonra aynı ağacı private `main`'e yeni bir commit olarak ekle (geçmişler ayrı olduğu için doğrudan push edilmez):

  ```bash
  git fetch origin +main:refs/remotes/origin/main && git fetch ozel +main:refs/remotes/ozel/main
  git push ozel "$(git commit-tree 'origin/main^{tree}' -p ozel/main -m '<merge edilen PR başlığı> (my-playground#<n>)')":refs/heads/main
  ```

  (`origin` my-playground, `ozel` private repodur: `git remote add ozel https://github.com/sugarmaniac/Sosyal-Oyun-Platformu`. Refspec'ler açık yazılır; sığ klonda `git fetch origin main` uzak dalı güncellemez.)

  Railway, private `main`'deki her commit'i CI'ın sonucunu bekleyerek dağıtır. CI işleri yalnızca my-playground'da çalışır (`if: github.repository == …`); private kopyada atlanan işler başarılı sayılır, dağıtım bloklanmaz. Eşitlemeden birkaç dakika sonra `/saglik` ve değişen uç nokta Railway adresinde kontrol edilir; dağıtım başlamadıysa ve oturumda `RAILWAY_TOKEN` (proje token'ı) varsa dağıtım Railway GraphQL API'sindeki `serviceInstanceDeployV2(environmentId, serviceId, commitSha)` ile tetiklenir.

## Ticket seçimi

1. GitHub Issues'tan açık, `ready-for-agent` etiketli ticket'ları listele (`docs/agents/issue-tracker.md`; GraphQL kapalıysa REST).
2. `claude/` ile başlayan bir daldan açılmış, merge edilmemiş bir PR varsa önce onu bitir: CI yeşilse ve kendi incelemen temizse merge et; kırmızıysa düzelt. Yeni ticket'a ancak açık PR kalmadığında geç (aynı anda tek ticket).
3. `Spec:` ile başlayan issue'lar (ör. #1) ticket değildir, seçimde atlanır; ticket'lar onun çocuklarıdır. Uygun ticket: engelleyen bütün issue'ları kapalı (GitHub'ın "blocked by" bağlantıları), kimseye atanmamış ve açık bir PR'ı olmayan ticket. Birden fazla varsa issue numarası en küçük olanı al. Uygun ticket yoksa kısa bir not bırakıp bitir.
4. Ticket'ı ilk iş olarak kendine ata (claim).

## Çakışmayı önleme

Oturumlar saatlik zamanlamayla ve birbirini tetikleyerek başladığı için aynı anda iki oturum çalışabilir. Bütün oturumlar aynı GitHub hesabını kullandığından, başka bir oturumun işini şu işaretlerden anla:

- Atanmış ama açık PR'ı olmayan bir ticket varsa ve son güncellemesi 3 saatten yeniyse, başka bir oturum onun üzerinde çalışıyor: hiçbir şeye dokunmadan bitir ve yeniden tetikleme yapma. 3 saatten eskiyse oturum yarıda kalmış say ve ticket'ı devral.
- Açık bir `claude/` PR'ının son push'u 30 dakikadan yeniyse ve CI'ı hâlâ çalışıyorsa, sahibi olan oturum onu bekliyor olabilir: dokunmadan bitir ve yeniden tetikleme yapma.

## Zincirleme

Bir ticket'ın PR'ı merge edilip ticket kapandıktan sonra, aynı oturumda hemen sıradaki uygun ticket'a geç; her ticket için yeni oturum açılmaz, çünkü yeni oturum repoyu ve bağlamı baştan okur. Uygun ticket yoksa, işin başka bir oturumda sürdüğünü gördüysen veya bir engelde durduysan dur ve kısa bir not bırak.

## Kararlar: insana sorma

Bir karar gerekiyorsa (tasarım, kapsam, kütüphane seçimi, belirsiz bir kabul kriteri) **insana sorma**. En makul seçeneği, `CONTEXT.md`, `docs/adr/`, spec #1 ve Ekran Kapsamı kararlarıyla tutarlı olanı seç ve devam et:

- Kararı ticket'a `Karar: ... Neden: ...` biçiminde yorum olarak yaz.
- Karar ADR ölçütlerini karşılıyorsa (geri dönüşü zor, şaşırtıcı, gerçek bir ödünleşim) `docs/adr/`'ye yeni bir ADR ekle; yeni bir alan terimi netleştiyse `CONTEXT.md`'yi güncelle.
- Mevcut bir ADR'ye aykırı davranman gerekiyorsa ADR'yi değiştiren yeni bir ADR yaz ve gerekçesini açıkça belirt.

## Uygulama

- Ticket'ı `/implement` akışıyla uygula: `/tdd` ile testten başlayarak, sonra `/code-review` ile kendi diff'ini gözden geçirerek.
- Testler test sınırlarından yazılır: backend HTTP API'si (gerçek PostgreSQL, Testcontainers) ve mobil ViewModel katmanı (sahte API istemcisi). Desenler ve yardımcılar `docs/agents/test-desenleri.md`'de; bütün testler `./gradlew tumTestler` ile çalışır.
- Dış servisler (IGDB, Steam, Apple/Google kimlik doğrulama, push, e-posta) her zaman bir adaptörün arkasındadır ve testler sahteleriyle çalışır. **Gerçek anahtar yoksa durma:** sahte adaptörle bitir; gerçek adaptör anahtarı ortam değişkeninden okuyacak şekilde yazılır.
- Commit mesajları, PR başlığı ve açıklaması Türkçe.

## PR ve merge

- `claude/` ile başlayan bir dala push et ve my-playground'da `main`'e PR aç; açıklamada `Closes sugarmaniac/Sosyal-Oyun-Platformu#<n>` ve verdiğin kararların listesi olsun.
- CI'ın bitmesini bekle. Kırmızıysa düzelt ve tekrar push et.
- CI yeşil ve `/code-review` incelemen temizse PR'ı **kendin merge et** (squash). Ardından private `main`'i eşitle (Repolar). Ticket'ın kapandığını doğrula; repolar arası anahtar kelime kapatmadıysa ticket'ı PR bağlantısıyla bir yorum bırakıp kapat.

## İnsan gereken adımlar

Yalnızca insanın yapabileceği işler: hesap açmak, para harcamak, API anahtarı veya sertifika almak (IGDB/Twitch, Apple, Google, Steam, FCM/APNs), Railway'de veritabanı ve ortam değişkeni ayarlamak, mağazalara yükleme. Bunlarda:

1. Durma; işi sahte adaptörle ve yapılandırmayla tamamla.
2. Gereken adımı "Gerçek servis hesapları ve anahtarları" ticket'ına (ready-for-human) madde olarak ekle: ne gerekiyor, hangi ortam değişkenine girecek.
3. Bu adımları yürüten tek bir etkileşimli script'i (`wizard` skill'i) güncel tut ve o ticket'ta yolunu belirt.

Asla: anahtarları repoya yazma, hesap açmaya veya ödeme yapmaya çalışma, ADR 0009'a aykırı olarak Railway test ortamına gerçek kişisel veri koyma, `main` dışındaki dalları veya başkalarının PR'larını değiştirme.

## Ortamlar

- **Cloud oturumu:** Linux; Android SDK repo'daki SessionStart hook'u ile kurulur. iOS derlemesi GitHub Actions'taki macOS işinde doğrulanır.
- **Test:** Railway, `main`'den otomatik dağıtım, yalnızca sahte veri (ADR 0009).
- **Kapalı beta:** Türkiye'deki VDS (ADR 0003), insan işi (#26).

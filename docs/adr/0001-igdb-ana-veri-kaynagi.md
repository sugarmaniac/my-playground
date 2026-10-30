# Oyun verisinin ana kaynağı IGDB, kendi veritabanımızda önbellekleniyor

Oyun verisini IGDB'den alıp kendi veritabanımızda saklıyoruz ve webhook'larla güncel tutuyoruz. Mobil uygulama ve web sitesi IGDB'yi hiç çağırmıyor. Oyunlar kendi ID'mizle tutuluyor, kaynak ID'si ayrı bir eşleme olarak saklanıyor; kaynak değişirse yalnızca içe aktarma katmanı etkileniyor.

## Considered Options

- **RAWG**: API şartlarında "klonlama yok" maddesi var ve puanlama/inceleme/liste sunan bir ürün bu çizgiye çok yakın. Verinin saklanmasına dair hiçbir şey söylemiyor. Yedek kaynak olarak kalıyor.
- **IGDB**: Veriyi kendi tarafımızda saklamamızı açıkça tercih ediyor; Steam ID eşlemesi (`external_games`), webhook ve PopScore sunuyor.
- **IGDB ve RAWG birlikte (çoklu kaynak birleştirme)**: reddedildi. İki katalog arasında ortak ID yok; güvenilir tek eşleştirme anahtarı Steam app ID'si ve konsola özel oyunlarda işe yaramıyor, bu da çift ve yanlış eşleşmiş oyunlar demek. RAWG verisi kullanıldığı anda "klonlama yok" maddesi, 100.000 aylık aktif kullanıcı sınırı ve her sayfada atıf linki zorunluluğu geri geliyor. RAWG'nin ek alanları (Metacritic, Steam ortalama süresi, başarımlar) MVP'de kullanılmıyor.

Sistem kaynak değiştirmeye hazır tutuluyor (katalog kaynağı bir arayüzün arkasında) ama aynı anda tek aktif kaynak var. RAWG ancak IGDB partnerliği reddedilirse veya IGDB'de kritik bir veri eksiği bulunursa devreye girer. Tek bir alan için zenginleştirme gerekirse (ör. Metacritic), RAWG'den yalnızca o alan, yalnızca Steam app ID'siyle eşleşen oyunlar için alınır ve ayrı bir ticket olarak ele alınır; ana katalog yine IGDB'dir.

## Consequences

- IGDB ticari olmayan kullanım için ücretsiz. Para kazanmaya başlamadan önce partnerlik (partner@igdb.com) onaylanmış olmalı ve kullanıcıya görünür, sabit bir yerde IGDB atfı gösterilmeli.
- IGDB tarayıcıdan çağrılamıyor (CORS yok) ve saniyede 4 istekle sınırlı; bu yüzden tek giriş noktası backend.
- Kaydedilebilir oyun türlerinin tamamı ilk kurulumda API ile sayfa sayfa (istek başına 500 kayıt) içeri alınıyor, sonra webhook'larla güncel tutuluyor. Günlük CSV dump'lar yalnızca partnerlere açık olduğu için bu yol seçildi.
- Oyun araması tamamen kendi PostgreSQL'imizde, Türkçe karakter eşleştirmesiyle yapılıyor; kullanıcı istekleri IGDB'ye hiç gitmiyor.

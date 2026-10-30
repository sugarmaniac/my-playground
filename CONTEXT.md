# Sosyal Oyun Platformu

Oyuncuların oynadıkları oyunları kaydettiği, puanladığı ve takip ettikleri kişilerin oyun aktivitesini gördüğü bir oyun günlüğü ve sosyal ağı. Önce kişisel günlük olarak tek başına değer verir; sosyal katman insanları uygulamada tutan farktır.

## Language

### Oyunlar

**Oyun**:
Kullanıcının kaydedebildiği tek bir yapım: ana oyun, remake/remaster, genişleme veya DLC. DLC ve genişlemeler bir ana oyuna bağlıdır; port'lar ve sürümler (edition) ayrı oyun değildir, ana oyunda birleşir.
_Avoid_: Başlık, ürün, title

### Günlük

**Kütüphane girişi**:
Bir kullanıcının bir oyunla ilişkisinin tek ve güncel kaydı; kullanıcı × oyun başına bir tane olur ve oyunun şu anki durumunu taşır.
_Avoid_: Kayıt, koleksiyon öğesi

**Günlük kaydı**:
Bir kullanıcının bir oyunu belirli bir zamanda oynadığını anlatan tarihli olay. Aynı oyun için birden fazla olabilir; tekrar oynamalar ayrı günlük kayıtlarıdır.
_Avoid_: Kayıt, log, oturum

**Durum**:
Kütüphane girişinin şu anki hali: Oynuyorum, Bitirdim, Bıraktım veya Beklemede. Bir kütüphane girişinin durumu olmayabilir (bkz. Durumsuz kütüphane girişi).
_Avoid_: Statü, aşama

**Durumsuz kütüphane girişi**:
Kullanıcının sahip olduğu ve oynadığı bilinen ama durumu henüz seçilmemiş kütüphane girişi; Steam içe aktarmasıyla oluşur. Akışa düşmez.
_Avoid_: Sahip olunan, içe aktarılmış (durum olarak)

**Puan**:
Kullanıcının bir oyuna yarım yıldızlı 5 üzerinden verdiği değer. Güncel puan kütüphane girişinde durur; her günlük kaydı, girildiği andaki puanın bir kopyasını saklar.
_Avoid_: Not, skor, rating

**Topluluk puanı**:
Bir oyunun, kullanıcıların kütüphane girişlerindeki güncel puanlarının ortalaması. Her kullanıcı tek oy verir; günlük kayıtlarındaki eski puanlar sayılmaz.
_Avoid_: Ortalama puan, genel puan

**İnceleme**:
Bir günlük kaydına yazılan metin. Kendi başına var olmaz; her oynayışın ayrı incelemesi olabilir.
_Avoid_: Yorum (yorum, bir içeriğin altına yazılan yanıttır), review

**Oyun süresi**:
Bir günlük kaydına isteğe bağlı girilen saat. Kütüphane girişinin toplam oyun süresi, Steam'den gelen süre ile Steam dışı günlük kayıtlarındaki sürelerin toplamıdır.
_Avoid_: Playtime, saat (tek başına)

**Görünürlük**:
Bir günlük kaydını kimin görebileceği: Herkes, Takipçiler, Arkadaşlar veya Ben. Yalnızca günlük kayıtlarında seçilir; kütüphane girişleri ve istek listesi profil düzeyinde görünür ve puanlar görünürlükten bağımsız olarak topluluk puanına sayılır.
_Avoid_: Gizlilik (gizlilik profil düzeyindeki ayardır)

**%100**:
Bitirdim durumundaki bir kütüphane girişine eklenen, oyunun tamamen bitirildiğini gösteren rozet. Ayrı bir durum değildir.
_Avoid_: Tamamlandı (durum olarak)

**İstek listesi**:
Kullanıcının ileride oynamak istediği oyunları işaretlediği, durumdan bağımsız işaret. Bir oyun hem Bıraktım durumunda hem istek listesinde olabilir.
_Avoid_: Wishlist, istenenler (durum olarak)

### Sosyal grafik

**Takip**:
Bir kullanıcının, onay gerektirmeden, başka bir kullanıcının herkese açık aktivitesini akışında görmeyi seçmesi. Tek yönlüdür.
_Avoid_: Abonelik, izleme

**Arkadaşlık**:
İki kullanıcı arasında, istek gönderilip kabul edilerek kurulan karşılıklı ilişki. Takibi kapsar; arkadaşlık sürerken takipten çıkılamaz, bittiğinde iki tarafın takibi olduğu gibi kalır.
_Avoid_: Bağlantı, karşılıklı takip

**Akış**:
Bir kullanıcının takip ettiği kişilerin aktivitelerinin kronolojik listesi. Yalnızca şu olaylar akışa düşer: günlük kaydı girilmesi, durumun Oynuyorum olması ve arkadaşlık kurulması. Takipler, puan değişiklikleri, istek listesi eklemeleri ve içe aktarmalar düşmez.
_Avoid_: Feed, zaman tüneli

**Sessize alma**:
Bir kullanıcının aktivitesini, takibi veya arkadaşlığı bozmadan akıştan gizleme.
_Avoid_: Gizleme, susturma

**Beğeni**:
Bir kullanıcının bir günlük kaydına verdiği tek dokunuşluk onay. Yalnızca günlük kayıtları beğenilebilir.
_Avoid_: Kalp, like

### Moderasyon

**Şikayet**:
Bir kullanıcının bir içeriği veya hesabı sabit bir sebep listesinden biriyle moderasyona bildirmesi.
_Avoid_: Rapor, ihbar

**Engelleme**:
Bir kullanıcının başka bir kullanıcıyla tüm bağını kesmesi: iki yöndeki takip ve arkadaşlık silinir, engellenenin beğenileri kaldırılır ve iki taraf birbirini hiçbir yerde göremez. Engellenene bildirilmez.
_Avoid_: Sessize alma (sessize alma bağı korur)

**Yaptırım**:
Moderasyonun bir şikayet sonucunda uyguladığı işlem: içeriği gizleme, hesabı askıya alma veya kalıcı ban. Kullanıcıya her yaptırım için kalıp bir mesaj gider.
_Avoid_: Ceza, ban (genel anlamda)

### Büyüme

**Davet kodu**:
Kapalı betada yeni bir kullanıcının katılmasını sağlayan tek kullanımlık kod. Kodu kullanan kişi, kodu veren kişiyi otomatik olarak takip eder.
_Avoid_: Referans kodu, davetiye linki

# Web sitesi

Statik HTML + JavaScript sitesi (ADR 0004). Gradle projesinin dışındadır (ADR 0005). İçerik tarayıcıda backend'in oturumsuz paylaşım uç noktalarından yüklenir; bu uç noktalar yalnızca Herkes görünürlüğündeki içeriği döner.

- `index.html`: tanıtım sayfası. Mağaza linkleri ve davet kodu girişi burada; `?kod=` ile gelen davet kodu kutuyu doldurur.
- `kayit.html?id=<günlük kaydı>`: günlük kaydı paylaşım sayfası (`GET /paylasim/gunluk/{id}`).
- `profil.html?ad=<kullanıcı adı>`: profil paylaşım sayfası (`GET /paylasim/profil/{kullaniciAdi}`).
- `liste.html?id=<liste>`: liste paylaşım sayfası (`GET /paylasim/liste/{id}`, Faz 2).
- `ayarlar.js`: API adresi ve mağaza linkleri. Mağaza linkleri boşken düğmelerde "Yakında" yazar.

Server bu dizini kök adreste sunar (imajda `/uygulama/web`, `WEB_DIZINI`). Böylece sayfalar API'ye aynı adresten ulaşır. Uygulamadaki paylaş butonu linkleri `PaylasimLinkleri` ile üretir.

Paylaşım sayfaları statik halleriyle `noindex`'tir. Faz 2 açıkken server bu üç sayfayı içeriğe özel `<title>`, açıklama ve Open Graph/Twitter etiketleriyle (oyun kapağı görseli) sunar; `noindex` kalkar. Yalnızca paylaşım uç noktalarının döndürdüğü içerik etiketlenir; ayrıca `robots.txt` ve `sitemap.xml` sunulur (#54). Faz 1'de `robots.txt` her şeyi kapatır, sitemap yoktur.

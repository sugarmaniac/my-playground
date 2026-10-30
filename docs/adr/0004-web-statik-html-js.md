# Web sitesi statik HTML + JavaScript

Web sitesi sunucu tarafında sayfa üretmeyen statik bir HTML + JavaScript sitesi. MVP'deki görevi tanıtım sayfası, davet kodu girişi ve paylaşılan inceleme/profil linklerinin açıldığı herkese açık sayfalar; içerik tarayıcıda API'den yükleniyor. Webde giriş yapma ve kayıt girme Faz 3'te.

## Consequences

- İçerik JavaScript ile sonradan yüklendiği için arama motorları paylaşım sayfalarını iyi indeksleyemez ve link önizlemeleri (Open Graph) sayfaya özel olmaz. Bu Faz 2'de ele alınacak (ör. paylaşım sayfaları için önceden üretilmiş HTML veya küçük bir önizleme servisi).
- Compose Multiplatform'un web hedefi bilerek kullanılmıyor.

# Mobil uygulama Kotlin Multiplatform + Compose Multiplatform, backend Ktor

iOS ve Android tek kod tabanıyla, arayüz dahil Compose Multiplatform ile yazılıyor; backend Ktor + PostgreSQL. Ekip iki kişi ve AI destekli, bu yüzden iki platformu ayrı ayrı native yazmak yerine tek dil ve ortak bir model modülü seçildi: API modelleri mobil ile backend arasında paylaşılıyor.

## Considered Options

- **Ortak mantık + platforma özel arayüz (SwiftUI / Android Compose)**: daha "native" his verir ama arayüzü iki kez yazmak gerekir. iOS'a özgü küçük farklar sonradan platforma özel kodla ekleniyor.

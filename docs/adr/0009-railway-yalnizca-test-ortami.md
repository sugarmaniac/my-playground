# Railway yalnızca test ortamı, kapalı beta Türkiye'deki VDS'te

Backend `main`'e her merge'de Railway'deki test ortamına kendiliğinden dağıtılıyor; ajanların ve ekibin işi orada doğrulanıyor. Railway'in Türkiye'de bölgesi olmadığı için (ADR 0003) test ortamında yalnızca sahte veri bulunur: gerçek kullanıcı kaydı, gerçek rehber eşleştirmesi veya gerçek Steam hesabı bağlanmaz. Gerçek kullanıcıların geleceği kapalı beta ve sonrası Türkiye'deki VDS'te çalışır.

## Consequences

- İki dağıtım hedefi var; uygulama ortam değişkenleriyle yapılandırılır ve iki ortamda aynı imaj çalışmalıdır.
- Railway test ortamına gerçek kişisel veri girilmesi bu kararın ihlalidir.

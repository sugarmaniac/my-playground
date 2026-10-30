# Üçüncü taraf analitik aracı kullanılmıyor

Firebase Analytics gibi araçlar kullanıcı verisini yurt dışına taşıdığı için ADR 0003 ile çelişiyor. Doğrulama eşiğinin metrikleri (4. hafta aktif kalma oranı, aktif kullanıcı başına haftalık günlük kaydı) zaten kendi veritabanımızda olduğundan, bunlar haftalık bir SQL sorgusuyla ölçülüyor. Ekran bazlı ürün analitiği gerekirse Faz 2'de kendi sunucumuzda çalışan bir araç kurulacak.

-- Kullanıcının uygulamayı kullandığı günler (Türkiye saati); doğrulama metrikleri için "son aktif olma" bilgisi (ADR 0007).
CREATE TABLE aktif_gun (
    kullanici_id UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    gun          DATE NOT NULL,
    PRIMARY KEY (kullanici_id, gun)
);

CREATE INDEX aktif_gun_gun ON aktif_gun (gun);

-- Haftalık metrik e-postası her hafta bir kez gönderilir; server yeniden başlasa da tekrarlanmaz.
CREATE TABLE metrik_raporu (
    hafta       DATE PRIMARY KEY,
    gonderilme  TIMESTAMPTZ NOT NULL
);

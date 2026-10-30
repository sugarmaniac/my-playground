-- Bir oyunun belirli bir zamanda oynandığını anlatan tarihli olay (CONTEXT.md: Günlük kaydı).
-- Her zaman bir kütüphane girişine bağlıdır; tekrar oynayışlar ayrı kayıtlardır.
CREATE TABLE gunluk_kaydi (
    id           UUID PRIMARY KEY,
    kullanici_id UUID NOT NULL,
    oyun_id      UUID NOT NULL,
    platform     TEXT,
    baslama      DATE,
    bitis        DATE CHECK (baslama IS NULL OR bitis IS NULL OR bitis >= baslama),
    sure_dakika  INTEGER CHECK (sure_dakika >= 0),
    -- Kaydın girildiği andaki kütüphane puanının kopyası.
    puan         SMALLINT CHECK (puan BETWEEN 1 AND 10),
    inceleme     TEXT,
    spoiler      BOOLEAN NOT NULL DEFAULT false,
    gorunurluk   TEXT NOT NULL,
    sessiz       BOOLEAN NOT NULL DEFAULT false,
    olusturulma  TIMESTAMPTZ NOT NULL,
    guncellenme  TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (kullanici_id, oyun_id) REFERENCES kutuphane_girisi (kullanici_id, oyun_id) ON DELETE CASCADE
);

CREATE INDEX gunluk_kaydi_kullanici_oyun ON gunluk_kaydi (kullanici_id, oyun_id, olusturulma DESC);
CREATE INDEX gunluk_kaydi_oyun ON gunluk_kaydi (oyun_id, olusturulma DESC);

-- Detaylı kayıt ekranının önereceği, son günlük kaydında seçilen platform.
ALTER TABLE kullanici ADD COLUMN son_platform TEXT;

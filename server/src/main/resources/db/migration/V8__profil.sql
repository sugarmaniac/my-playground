ALTER TABLE kullanici ADD COLUMN biyografi TEXT;

-- Profilin vitrini: favori dört oyun, sırasıyla.
CREATE TABLE vitrin_oyunu (
    kullanici_id UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    sira         SMALLINT NOT NULL CHECK (sira BETWEEN 1 AND 4),
    oyun_id      UUID NOT NULL REFERENCES oyun (id),
    PRIMARY KEY (kullanici_id, sira),
    UNIQUE (kullanici_id, oyun_id)
);

-- Günlüğün tarih sırası: bitirme, yoksa başlama, o da yoksa kaydın girildiği gün (Türkiye saati).
ALTER TABLE gunluk_kaydi ADD COLUMN tarih DATE;
UPDATE gunluk_kaydi SET tarih = COALESCE(bitis, baslama, (olusturulma AT TIME ZONE 'Europe/Istanbul')::date);
ALTER TABLE gunluk_kaydi ALTER COLUMN tarih SET NOT NULL;
CREATE INDEX gunluk_kaydi_kullanici_tarih ON gunluk_kaydi (kullanici_id, tarih DESC, olusturulma DESC);

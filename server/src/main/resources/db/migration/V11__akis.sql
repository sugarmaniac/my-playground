-- Akışa düşen olaylar (CONTEXT.md: Akış). Akış okuma anında bu tablodan sorgulanır; görünürlük ve
-- sessiz kayıt, günlük kaydının güncel halinden okunur.
CREATE TABLE akis_olayi (
    id                 UUID PRIMARY KEY,
    tur                TEXT NOT NULL,
    kullanici_id       UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    oyun_id            UUID REFERENCES oyun (id),
    gunluk_kaydi_id    UUID UNIQUE REFERENCES gunluk_kaydi (id) ON DELETE CASCADE,
    -- Arkadaşlık olayında diğer taraf.
    diger_kullanici_id UUID REFERENCES kullanici (id) ON DELETE CASCADE,
    olusturulma        TIMESTAMPTZ NOT NULL
);

CREATE INDEX akis_olayi_kullanici ON akis_olayi (kullanici_id, olusturulma DESC, id DESC);
CREATE INDEX akis_olayi_diger_kullanici ON akis_olayi (diger_kullanici_id, olusturulma DESC, id DESC);

-- Bu ticket'tan önceki etkinlik.
INSERT INTO akis_olayi (id, tur, kullanici_id, oyun_id, gunluk_kaydi_id, olusturulma)
SELECT gen_random_uuid(), 'GUNLUK_KAYDI', kullanici_id, oyun_id, id, olusturulma FROM gunluk_kaydi;

INSERT INTO akis_olayi (id, tur, kullanici_id, oyun_id, olusturulma)
SELECT gen_random_uuid(), 'OYNAMAYA_BASLADI', kullanici_id, oyun_id, durum_degisme
FROM kutuphane_girisi WHERE durum = 'OYNUYORUM' AND durum_degisme IS NOT NULL;

INSERT INTO akis_olayi (id, tur, kullanici_id, diger_kullanici_id, olusturulma)
SELECT gen_random_uuid(), 'ARKADAS_OLDU', kullanici_a, kullanici_b, olusturulma FROM arkadaslik;

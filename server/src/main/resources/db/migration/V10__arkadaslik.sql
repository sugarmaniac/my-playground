-- Bekleyen arkadaşlık istekleri; kabul ya da retle silinir.
CREATE TABLE arkadaslik_istegi (
    gonderen    UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    alan        UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    olusturulma TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (gonderen, alan),
    CHECK (gonderen <> alan)
);

CREATE INDEX arkadaslik_istegi_alan ON arkadaslik_istegi (alan, olusturulma DESC);

-- Karşılıklı arkadaşlık (CONTEXT.md: Arkadaşlık); her çift bir kez, küçük kimlik önce.
-- Kurulduğu an akışta "arkadaşlık kuruldu" olayıdır.
CREATE TABLE arkadaslik (
    kullanici_a UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    kullanici_b UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    olusturulma TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (kullanici_a, kullanici_b),
    CHECK (kullanici_a < kullanici_b)
);

CREATE INDEX arkadaslik_kullanici_b ON arkadaslik (kullanici_b);

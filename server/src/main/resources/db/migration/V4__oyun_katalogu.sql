-- Arama, Türkçe karakter ve aksanlardan arındırılmış ad üzerinde trigram indeksiyle yapılır (ADR 0001).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Kaydedilebilir oyunlar (CONTEXT.md: Oyun); kendi kimliğimizle.
CREATE TABLE oyun (
    id                 UUID PRIMARY KEY,
    ad                 TEXT NOT NULL,
    arama_metni        TEXT NOT NULL,
    tur                TEXT NOT NULL,
    -- DLC ve genişlemelerin ana oyununun kaynak (IGDB) kimliği; ana oyun oyun_kaynagi'ndan bulunur,
    -- böylece içe aktarma sırası önemli değildir.
    ana_oyun_kaynak_id BIGINT,
    cikis_tarihi       DATE,
    kapak_kimligi      TEXT,
    ozet               TEXT,
    populerlik         DOUBLE PRECISION NOT NULL DEFAULT 0,
    -- Kaynakta silinen oyunlar silinmez, gizlenir: kütüphane girişleri onlara bağlı kalabilir.
    kaldirildi         BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX oyun_arama_metni ON oyun USING gin (arama_metni gin_trgm_ops);

-- Bir oyunun dış kaynaklardaki kayıtları: kendi IGDB kaydı (birincil), ana oyunda birleşen
-- port ve sürüm kayıtları, Steam uygulamaları. Platformlar her IGDB kaydında ayrı tutulur ve
-- oyunun platformları bunların birleşimidir; bir port silinince platformu da düşer.
CREATE TABLE oyun_kaynagi (
    kaynak      TEXT NOT NULL,
    kaynak_id   BIGINT NOT NULL,
    oyun_id     UUID NOT NULL REFERENCES oyun (id) ON DELETE CASCADE,
    birincil    BOOLEAN NOT NULL DEFAULT false,
    platformlar TEXT[] NOT NULL DEFAULT '{}',
    PRIMARY KEY (kaynak, kaynak_id)
);

CREATE INDEX oyun_kaynagi_oyun_id ON oyun_kaynagi (oyun_id);

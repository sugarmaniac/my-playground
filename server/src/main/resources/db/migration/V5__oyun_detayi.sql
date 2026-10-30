ALTER TABLE oyun
    ADD COLUMN slug           TEXT,
    ADD COLUMN gelistiriciler TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN yayincilar     TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN turler         TEXT[] NOT NULL DEFAULT '{}';

-- Kaynağın verdiği mağaza linkleri; Steam linki eşlemedeki Steam uygulamasından da türetilir.
CREATE TABLE oyun_magaza_linki (
    oyun_id UUID NOT NULL REFERENCES oyun (id) ON DELETE CASCADE,
    magaza  TEXT NOT NULL,
    url     TEXT NOT NULL,
    PRIMARY KEY (oyun_id, magaza)
);

CREATE INDEX oyun_ana_oyun_kaynak_id ON oyun (ana_oyun_kaynak_id);

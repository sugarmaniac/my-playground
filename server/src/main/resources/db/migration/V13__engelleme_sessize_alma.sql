-- Engelleme: iki taraf birbirini hiçbir yerde göremez; takip, arkadaşlık ve beğeniler engellerken silinir.
CREATE TABLE engelleme (
    engelleyen  UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    engellenen  UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    olusturulma TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (engelleyen, engellenen),
    CHECK (engelleyen <> engellenen)
);

CREATE INDEX engelleme_engellenen ON engelleme (engellenen);

-- Sessize alma: ilişki bozulmadan, sessize alınanın aktivitesi sessize alanın akışında görünmez.
CREATE TABLE sessize_alma (
    alan        UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    alinan      UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    olusturulma TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (alan, alinan),
    CHECK (alan <> alinan)
);

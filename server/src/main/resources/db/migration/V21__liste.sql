-- Faz 2 (#49): oyun listeleri. Sahip silinince listeleri, liste silinince oyunları ve beğenileri silinir.
CREATE TABLE liste (
    id           UUID PRIMARY KEY,
    kullanici_id UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    baslik       TEXT NOT NULL,
    aciklama     TEXT,
    gorunurluk   TEXT NOT NULL,
    olusturulma  TIMESTAMPTZ NOT NULL,
    guncellenme  TIMESTAMPTZ NOT NULL
);

CREATE INDEX liste_kullanici ON liste (kullanici_id, guncellenme);

CREATE TABLE liste_oyunu (
    liste_id UUID NOT NULL REFERENCES liste (id) ON DELETE CASCADE,
    oyun_id  UUID NOT NULL REFERENCES oyun (id),
    sira     SMALLINT NOT NULL,
    PRIMARY KEY (liste_id, oyun_id)
);

CREATE INDEX liste_oyunu_oyun ON liste_oyunu (oyun_id);

CREATE TABLE liste_begenisi (
    liste_id     UUID NOT NULL REFERENCES liste (id) ON DELETE CASCADE,
    kullanici_id UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    olusturulma  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (liste_id, kullanici_id)
);

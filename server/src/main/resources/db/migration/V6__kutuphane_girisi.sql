-- Kullanıcı × oyun başına tek kütüphane girişi (CONTEXT.md: Kütüphane girişi).
CREATE TABLE kutuphane_girisi (
    kullanici_id       UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    oyun_id            UUID NOT NULL REFERENCES oyun (id),
    durum              TEXT,
    -- Yarım yıldız cinsinden: 1 = yarım yıldız, 10 = beş yıldız.
    puan               SMALLINT CHECK (puan BETWEEN 1 AND 10),
    begendim           BOOLEAN NOT NULL DEFAULT false,
    istek_listesinde   BOOLEAN NOT NULL DEFAULT false,
    yuzde_yuz          BOOLEAN NOT NULL DEFAULT false CHECK (NOT yuzde_yuz OR durum = 'BITIRDIM'),
    -- Durumun son değiştiği an; akış "durum Oynuyorum oldu" olayını buradan okur.
    durum_degisme      TIMESTAMPTZ,
    olusturulma        TIMESTAMPTZ NOT NULL DEFAULT now(),
    guncellenme        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (kullanici_id, oyun_id)
);

CREATE INDEX kutuphane_girisi_oyun_id ON kutuphane_girisi (oyun_id);

-- Faz 2 (#48): gizli profil ve takip istekleri. Faz kapalıyken gizli sütunu yok sayılır.
ALTER TABLE kullanici ADD COLUMN gizli BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE takip_istegi (
    isteyen     UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    hedef       UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    olusturulma TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (isteyen, hedef)
);

CREATE INDEX takip_istegi_hedef ON takip_istegi (hedef, olusturulma);

-- Faz 2 (#50): küratörler. Onaylanan başvuru kullanıcıyı küratör yapar.
ALTER TABLE kullanici ADD COLUMN kurator BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE kurator_basvurusu (
    id           UUID PRIMARY KEY,
    kullanici_id UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    -- BEKLIYOR, ONAYLANDI, REDDEDILDI
    durum        TEXT NOT NULL,
    olusturulma  TIMESTAMPTZ NOT NULL,
    karar_zamani TIMESTAMPTZ
);

CREATE INDEX kurator_basvurusu_bekleyen ON kurator_basvurusu (olusturulma) WHERE durum = 'BEKLIYOR';
-- Aynı anda gelen iki başvurudan yalnızca biri bekler.
CREATE UNIQUE INDEX kurator_basvurusu_tek_bekleyen ON kurator_basvurusu (kullanici_id) WHERE durum = 'BEKLIYOR';

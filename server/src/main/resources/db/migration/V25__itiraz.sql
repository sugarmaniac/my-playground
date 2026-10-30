-- Faz 2 (#53): yaptırım kayıtları, itirazlar, denetim kaydı ve yorum yasağı. Kayıtlar faz kapalıyken de tutulur.
CREATE TABLE yaptirim (
    id              UUID PRIMARY KEY,
    kullanici_id    UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    -- ICERIK_KALDIRMA, ASKI, BAN, UYARI, YORUM_YASAGI
    tur             TEXT NOT NULL,
    olusturulma     TIMESTAMPTZ NOT NULL,
    bitis           TIMESTAMPTZ,
    -- İtiraz kabul edilince yaptırım kalkar.
    kaldirildi      BOOLEAN NOT NULL DEFAULT FALSE,
    -- Askıdaki/banlı hesabın giriş ekranından itirazı için son reddedilen girişte verilen anahtarın özeti.
    itiraz_anahtari TEXT
);

CREATE INDEX yaptirim_kullanici ON yaptirim (kullanici_id, olusturulma DESC);

CREATE TABLE itiraz (
    id           UUID PRIMARY KEY,
    -- Her yaptırıma bir kez itiraz edilir.
    yaptirim_id  UUID NOT NULL UNIQUE REFERENCES yaptirim (id) ON DELETE CASCADE,
    metin        TEXT NOT NULL,
    olusturulma  TIMESTAMPTZ NOT NULL,
    -- BEKLIYOR, KABUL_EDILDI, REDDEDILDI
    durum        TEXT NOT NULL DEFAULT 'BEKLIYOR',
    karar_zamani TIMESTAMPTZ
);

CREATE INDEX itiraz_bekleyen ON itiraz (olusturulma) WHERE durum = 'BEKLIYOR';

-- Yönetim panelindeki her karar: kim (HTTP Basic kullanıcı adı), ne zaman, ne yaptı.
CREATE TABLE denetim_kaydi (
    id      UUID PRIMARY KEY,
    zaman   TIMESTAMPTZ NOT NULL,
    yapan   TEXT NOT NULL,
    eylem   TEXT NOT NULL,
    ayrinti TEXT NOT NULL
);

CREATE INDEX denetim_kaydi_zaman ON denetim_kaydi (zaman DESC);

ALTER TABLE kullanici ADD COLUMN yorum_yasagi_bitisi TIMESTAMPTZ;

-- Uygulama içi bildirimler; kaynak olayla aynı işlemde yazılır. Push, ayrı bir dağıtıcı tarafından gönderilir
-- (push_durumu: BEKLIYOR, GONDERILDI, ATLANDI).
CREATE TABLE bildirim (
    id              UUID PRIMARY KEY,
    alici           UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    tur             TEXT NOT NULL,
    aktor           UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    gunluk_kaydi_id UUID REFERENCES gunluk_kaydi (id) ON DELETE CASCADE,
    olusturulma     TIMESTAMPTZ NOT NULL,
    okundu          BOOLEAN NOT NULL DEFAULT FALSE,
    push_durumu     TEXT NOT NULL DEFAULT 'BEKLIYOR',
    push_zamani     TIMESTAMPTZ
);

CREATE INDEX bildirim_alici ON bildirim (alici, olusturulma DESC);
CREATE INDEX bildirim_bekleyen_push ON bildirim (olusturulma) WHERE push_durumu = 'BEKLIYOR';

-- Tür başına push tercihi; satır yoksa push açıktır.
CREATE TABLE bildirim_tercihi (
    kullanici_id UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    tur          TEXT NOT NULL,
    push         BOOLEAN NOT NULL,
    PRIMARY KEY (kullanici_id, tur)
);

-- Push token'ları ve sessiz saatler için cihazın saat dilimi.
CREATE TABLE push_cihazi (
    token        TEXT PRIMARY KEY,
    kullanici_id UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    platform     TEXT NOT NULL,
    saat_dilimi  TEXT NOT NULL,
    guncellenme  TIMESTAMPTZ NOT NULL
);

CREATE INDEX push_cihazi_kullanici ON push_cihazi (kullanici_id);

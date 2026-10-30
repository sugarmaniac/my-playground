-- Şikayetler: şikayet edilen içeriğin o anki hali (icerik_ozeti) saklanır; içerik kaldırılsa da karar kaydı kalır.
CREATE TABLE sikayet (
    id              UUID PRIMARY KEY,
    sikayet_eden    UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    hedef_kullanici UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    gunluk_kaydi_id UUID REFERENCES gunluk_kaydi (id) ON DELETE SET NULL,
    sebep           TEXT NOT NULL,
    aciklama        TEXT,
    icerik_ozeti    TEXT,
    olusturulma     TIMESTAMPTZ NOT NULL,
    -- ACIK, REDDEDILDI, ICERIK_KALDIRILDI, ASKIYA_ALINDI, BANLANDI
    durum           TEXT NOT NULL DEFAULT 'ACIK',
    karar_zamani    TIMESTAMPTZ
);

CREATE INDEX sikayet_acik ON sikayet (olusturulma) WHERE durum = 'ACIK';

-- Yaptırımlar: askının bittiği an ve kalıcı ban; ikisi de girişi reddeder.
ALTER TABLE kullanici ADD COLUMN askinin_bitisi TIMESTAMPTZ;
ALTER TABLE kullanici ADD COLUMN banli BOOLEAN NOT NULL DEFAULT FALSE;

-- Moderasyon kalıp mesajlarının bildirimi doğuran bir kişisi yoktur.
ALTER TABLE bildirim ALTER COLUMN aktor DROP NOT NULL;

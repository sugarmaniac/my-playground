-- Bir günlük kaydına verilen tek dokunuşluk onay (CONTEXT.md: Beğeni). Akışa düşmez.
CREATE TABLE begeni (
    kullanici_id    UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    gunluk_kaydi_id UUID NOT NULL REFERENCES gunluk_kaydi (id) ON DELETE CASCADE,
    olusturulma     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (gunluk_kaydi_id, kullanici_id)
);

CREATE INDEX begeni_kullanici ON begeni (kullanici_id);

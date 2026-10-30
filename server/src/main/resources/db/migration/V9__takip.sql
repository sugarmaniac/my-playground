-- Onay gerektirmeyen, tek yönlü takip (CONTEXT.md: Takip).
CREATE TABLE takip (
    takip_eden   UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    takip_edilen UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    olusturulma  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (takip_eden, takip_edilen),
    CHECK (takip_eden <> takip_edilen)
);

CREATE INDEX takip_takip_edilen ON takip (takip_edilen, olusturulma DESC);

-- Davet kodunu kullanan, kodu vereni takip eder; bu ticket'tan önce kullanılmış kodlar için de (#6).
INSERT INTO takip (takip_eden, takip_edilen)
SELECT kullanan_id, sahibi_id
FROM davet_kodu
WHERE kullanan_id IS NOT NULL AND sahibi_id IS NOT NULL AND kullanan_id <> sahibi_id
ON CONFLICT DO NOTHING;

-- Kullanıcı araması: kullanıcı adı ve görünen ad, Türkçe karakterlerden arındırılmış (uygulama her profil kaydında yazar).
ALTER TABLE kullanici ADD COLUMN arama_metni TEXT;
UPDATE kullanici SET arama_metni = lower(kullanici_adi || ' ' || gorunen_ad) WHERE kullanici_adi IS NOT NULL;
CREATE INDEX kullanici_arama_metni ON kullanici USING gin (arama_metni gin_trgm_ops);

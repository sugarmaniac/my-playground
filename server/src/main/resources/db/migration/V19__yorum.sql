-- Faz 2 (#47): günlük kaydı yorumları. Kayıt ya da yazar silinince yorumlar da silinir.
CREATE TABLE yorum (
    id              UUID PRIMARY KEY,
    gunluk_kaydi_id UUID NOT NULL REFERENCES gunluk_kaydi (id) ON DELETE CASCADE,
    kullanici_id    UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    metin           TEXT NOT NULL,
    olusturulma     TIMESTAMPTZ NOT NULL
);

CREATE INDEX yorum_kayit ON yorum (gunluk_kaydi_id, olusturulma);

-- Yorum bildirimi yoruma bağlıdır; yorum silinince bildirimi de silinir.
ALTER TABLE bildirim ADD COLUMN yorum_id UUID REFERENCES yorum (id) ON DELETE CASCADE;

-- Şikayetin hedefi açıkça tutulur: kayıt silinince gunluk_kaydi_id boşalsa da hedefin türü bilinir.
ALTER TABLE sikayet ADD COLUMN yorum_id UUID REFERENCES yorum (id) ON DELETE SET NULL;
ALTER TABLE sikayet ADD COLUMN hedef_turu TEXT NOT NULL DEFAULT 'PROFIL';
UPDATE sikayet SET hedef_turu = 'KAYIT' WHERE gunluk_kaydi_id IS NOT NULL;

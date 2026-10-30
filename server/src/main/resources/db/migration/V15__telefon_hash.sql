-- Rehber eşleştirmesi: kullanıcının kendi numarasının cihazda üretilmiş SHA-256'sı; açık numara hiç saklanmaz (ADR 0003).
ALTER TABLE kullanici ADD COLUMN telefon_hash TEXT;
CREATE INDEX kullanici_telefon_hash ON kullanici (telefon_hash) WHERE telefon_hash IS NOT NULL;

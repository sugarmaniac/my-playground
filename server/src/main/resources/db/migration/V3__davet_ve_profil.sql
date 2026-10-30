ALTER TABLE kullanici
    ADD COLUMN davet_kabul_zamani TIMESTAMPTZ,
    -- Küçük harfle saklanır; benzersizlik büyük/küçük harften bağımsızdır.
    ADD COLUMN kullanici_adi      TEXT UNIQUE,
    ADD COLUMN gorunen_ad         TEXT,
    ADD COLUMN avatar             TEXT;

-- Tek kullanımlık davet kodları. Kurucu kodlarının sahibi yoktur.
CREATE TABLE davet_kodu (
    kod         TEXT PRIMARY KEY,
    sahibi_id   UUID REFERENCES kullanici (id) ON DELETE CASCADE,
    -- Bir kullanıcı betaya tek bir kodla katılır.
    kullanan_id UUID UNIQUE REFERENCES kullanici (id) ON DELETE SET NULL,
    kullanilma  TIMESTAMPTZ,
    olusturulma TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX davet_kodu_sahibi_id ON davet_kodu (sahibi_id);

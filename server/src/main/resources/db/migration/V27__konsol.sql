-- Faz 3 (#57): Xbox ve PlayStation bağlantıları; hesap silinince bağlantılar da silinir.
CREATE TABLE konsol_baglantisi (
    kullanici_id   UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    -- XBOX, PLAYSTATION
    ag             TEXT NOT NULL,
    hesap_kimligi  TEXT NOT NULL,
    hesap_adi      TEXT NOT NULL,
    baglanma       TIMESTAMPTZ NOT NULL,
    son_guncelleme TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (kullanici_id, ag),
    UNIQUE (ag, hesap_kimligi)
);

-- İçe aktarılan girişin oynandığı konsol ağı; durumsuz giriş bununla "oynanmış" sayılır (Steam süresi gibi).
ALTER TABLE kutuphane_girisi ADD COLUMN konsol_agi TEXT;

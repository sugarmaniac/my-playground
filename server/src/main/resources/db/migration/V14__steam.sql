-- Steam bağlantısı: bir Steam hesabı tek kullanıcıya bağlanabilir.
CREATE TABLE steam_baglantisi (
    kullanici_id UUID PRIMARY KEY REFERENCES kullanici (id) ON DELETE CASCADE,
    steam_id     TEXT NOT NULL UNIQUE,
    baglanma     TIMESTAMPTZ NOT NULL,
    son_guncelleme TIMESTAMPTZ NOT NULL
);

-- Steam'in bildirdiği toplam oyun süresi; günde bir güncellenir, akışa düşmez.
ALTER TABLE kutuphane_girisi ADD COLUMN steam_sure_dakika INTEGER;

-- Onboarding'in Steam ve ilk durumlar adımları. Bu göçten önce profilini bitirmiş kullanıcılar tamamlamış sayılır.
ALTER TABLE kullanici ADD COLUMN onboarding_tamamlandi BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE kullanici SET onboarding_tamamlandi = TRUE WHERE kullanici_adi IS NOT NULL;

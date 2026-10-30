-- Faz 3 (#56): kullanıcının "ilgilenmiyorum" dediği oyunlar bir daha önerilmez.
CREATE TABLE oneri_gizleme (
    kullanici_id UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    oyun_id      UUID NOT NULL REFERENCES oyun (id) ON DELETE CASCADE,
    olusturulma  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (kullanici_id, oyun_id)
);

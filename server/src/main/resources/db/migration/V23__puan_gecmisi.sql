-- Faz 2 (#51): her pazartesi (Türkiye saati) her puanlı oyunun topluluk puanı. Faz kapalıyken de yazılır.
CREATE TABLE puan_anlik_goruntusu (
    oyun_id    UUID NOT NULL REFERENCES oyun (id) ON DELETE CASCADE,
    hafta      DATE NOT NULL,
    ortalama   NUMERIC(4, 2) NOT NULL,
    puan_veren INTEGER NOT NULL,
    PRIMARY KEY (oyun_id, hafta)
);

CREATE INDEX puan_anlik_goruntusu_hafta ON puan_anlik_goruntusu (hafta);

-- Haftanın görüntüsünün alındığını işaretler; puanlı oyun olmasa da hafta bir kez işlenir.
CREATE TABLE puan_anlik_goruntusu_haftasi (
    hafta DATE PRIMARY KEY
);

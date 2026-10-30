CREATE TABLE kullanici (
    id          UUID PRIMARY KEY,
    olusturulma TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bir kullanıcının Apple/Google'daki kalıcı kimliği (token'daki `sub`).
CREATE TABLE kimlik (
    saglayici    TEXT NOT NULL,
    harici_kimlik TEXT NOT NULL,
    kullanici_id UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    PRIMARY KEY (saglayici, harici_kimlik)
);

-- Oturum token'ının kendisi değil, SHA-256 özeti saklanır.
CREATE TABLE oturum (
    token_ozeti  TEXT PRIMARY KEY,
    kullanici_id UUID NOT NULL REFERENCES kullanici (id) ON DELETE CASCADE,
    olusturulma  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX oturum_kullanici_id ON oturum (kullanici_id);

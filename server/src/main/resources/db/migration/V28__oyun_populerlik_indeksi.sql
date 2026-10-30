-- #60: Trend ve tür/platform listeleri popülerliğe göre sıralanır; indeks olmadan her istekte tüm katalog sıralanıyordu.
-- Kısmi değil: koşullar sorguda parametre olarak gider, hazır plan kısmi indeksin koşulunu kanıtlayamaz.
-- Listelerdeki ad ile eşitlik bozma, indeksin öneki üzerinden artımlı sıralamayla yapılır.
CREATE INDEX oyun_populerlik ON oyun (populerlik DESC, id);

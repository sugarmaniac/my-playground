-- #60: Trend ve tür/platform listeleri popülerliğe göre sıralanır; indeks olmadan her istekte tüm katalog sıralanıyordu.
-- Yalnız listelenen satırlar: kaldırılmamış ana oyunlar.
CREATE INDEX oyun_populerlik ON oyun (populerlik DESC, id) WHERE kaldirildi = false AND tur = 'ANA_OYUN';

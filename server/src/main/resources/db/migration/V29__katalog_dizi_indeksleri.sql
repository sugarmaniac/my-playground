-- #62: Tür ve platform listeleri `@>` ile süzülür; GIN indeksi olmadan her istekte bütün kayıtlar taranıyordu.
CREATE INDEX oyun_turler ON oyun USING gin (turler);
CREATE INDEX oyun_kaynagi_platformlar ON oyun_kaynagi USING gin (platformlar);

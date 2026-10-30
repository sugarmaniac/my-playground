-- Faz 2 (#52): pencere bekleyen push'un en erken gönderim zamanı. push_durumu artık OZETLENDI de olabilir.
ALTER TABLE bildirim ADD COLUMN push_sonra TIMESTAMPTZ;

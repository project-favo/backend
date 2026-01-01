-- Fix interaction table: Remove type column (it should only be in child tables)
-- JOINED inheritance'da type kolonu sadece child tablolarda olmalı, base tabloda değil

-- Önce interaction tablosundaki type kolonunu sil
ALTER TABLE interaction DROP COLUMN IF EXISTS type;

-- product_interaction ve review_interaction tablolarında type kolonu kalmalı (doğru)
-- Bu kolonlar zaten doğru yerde, bir şey yapmaya gerek yok


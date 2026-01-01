-- Fix interaction table: Remove type column
ALTER TABLE interaction DROP COLUMN IF EXISTS type;


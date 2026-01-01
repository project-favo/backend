-- Drop interaction tables to recreate them with correct schema
-- Dikkat: Bu tüm verileri siler! Ama kullanıcı veri olmadığını söyledi.

-- Önce child tabloları sil (foreign key constraint'ler nedeniyle)
DROP TABLE IF EXISTS product_interaction;
DROP TABLE IF EXISTS review_interaction;

-- Sonra base tabloyu sil
DROP TABLE IF EXISTS interaction;

-- Backend'i yeniden başlattığınızda Hibernate tabloları doğru şekilde yeniden oluşturacak
-- type kolonu sadece product_interaction ve review_interaction tablolarında olacak
-- interaction tablosunda type kolonu OLMAYACAK (JOINED inheritance doğru çalışacak)


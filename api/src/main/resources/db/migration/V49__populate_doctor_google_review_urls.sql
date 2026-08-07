-- V49__populate_doctor_google_review_urls.sql
-- Atualiza as URLs do Google Review na tabela doctor_configurations com os links específicos de cada médico
-- Se o médico não tiver URL cadastrada, utiliza o fallback padrão da Clínica Inovare: https://share.google/OBtREC0KLjzx1YNOP

-- 1. Atualizações específicas por feegow_profissional_id e por doctor_name
UPDATE doctor_configurations SET google_review_url = 'https://share.google/KXGE8rob1gZ8WcEVp' WHERE feegow_profissional_id = 69 OR doctor_name ILIKE '%Rubens Sirtoli%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/auOiS4oCH61wDEF2P' WHERE feegow_profissional_id = 20 OR doctor_name ILIKE '%Liliana Pilatti%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/LCCGerxFhtrwBz0dy' WHERE feegow_profissional_id = 15 OR doctor_name ILIKE '%Daniel Oda%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/1ovdLLgVSztaYhWp0' WHERE feegow_profissional_id = 58 OR doctor_name ILIKE '%Victor Mauro%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/A1ghNoTMnSvnuzqG6' WHERE feegow_profissional_id = 13 OR doctor_name ILIKE '%Magno Zanellato%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/8CrZswrc0Ix1lLDOs' WHERE feegow_profissional_id = 18 OR doctor_name ILIKE '%Bruno Pançan%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/u8nE3ekMauDv4amKF' WHERE feegow_profissional_id = 17 OR doctor_name ILIKE '%Ricardo Zanetti%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/fFQ3QzTYatta4XDYk' WHERE feegow_profissional_id = 29 OR doctor_name ILIKE '%Luiz Strack%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/MzUDLYlYWmVZK0DA7' WHERE feegow_profissional_id = 23 OR doctor_name ILIKE '%Ana Paula%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/l4Fy7R6Lp8NtGyeVC' WHERE feegow_profissional_id = 22 OR doctor_name ILIKE '%Alexandre Acuña%' OR doctor_name ILIKE '%Alexandre Barao%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/lzED7uvo3X0CGmWsH' WHERE feegow_profissional_id = 82 OR doctor_name ILIKE '%Marcos Marochi%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/MLLloGYqLFGwW1a8G' WHERE feegow_profissional_id = 24 OR doctor_name ILIKE '%Cíntia Cenovicz%' OR doctor_name ILIKE '%Cintia Cenovicz%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/LE2hUa0w7j5iwcrzY' WHERE feegow_profissional_id = 37 OR doctor_name ILIKE '%Claudio Solak%' OR doctor_name ILIKE '%Danilo Saad%' OR doctor_name ILIKE '%Caroline Saad%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/Knrwq0pjviGAZa6qY' WHERE doctor_name ILIKE '%Eduardo Serman%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/xntYofSXbpKy4v25Y' WHERE feegow_profissional_id = 34 OR doctor_name ILIKE '%Lisa Paula Fernandes%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/JuJjcf678XkKsOjmu' WHERE feegow_profissional_id = 14 OR doctor_name ILIKE '%Marcelo Tessari%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/X1EwVSulo2GhlYkax' WHERE feegow_profissional_id = 3 OR doctor_name ILIKE '%Carlos Henrique%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/tLQVLWThqYdnGmKfA' WHERE feegow_profissional_id IN (9, 30, 80) OR doctor_name ILIKE '%Marcelo Cenovicz%' OR doctor_name ILIKE '%Murilo Cenovicz%' OR doctor_name ILIKE '%Fernanda Cenovicz%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/YTrvHTrib1vCJUCF1' WHERE feegow_profissional_id IN (10, 39, 40, 42, 43, 44, 45, 83) OR doctor_name ILIKE '%Carlos Miers%' OR doctor_name ILIKE '%Cristiano Gatelli%' OR doctor_name ILIKE '%Daniel Cartelli%' OR doctor_name ILIKE '%Franklin Hilgemberg%' OR doctor_name ILIKE '%Luis Felipe%' OR doctor_name ILIKE '%Rafael Pançan%' OR doctor_name ILIKE '%Rodrigo Fávaro%' OR doctor_name ILIKE '%Marina Polydoro%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/G62m5vhC5VEtCXh5F' WHERE feegow_profissional_id = 76 OR doctor_name ILIKE '%Thais Fernanda%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/uzr53VjByA178q79l' WHERE feegow_profissional_id = 6 OR doctor_name ILIKE '%Alisson Fucio%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/oWBeU2SSd8H0L2tix' WHERE feegow_profissional_id = 8 OR doctor_name ILIKE '%Carlos Koga%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/Xyi6u4GUJKYf41RYH' WHERE feegow_profissional_id = 7 OR doctor_name ILIKE '%Eduardo Bisinella%';
UPDATE doctor_configurations SET google_review_url = 'https://share.google/V1icma3JG4QGo8OQu' WHERE feegow_profissional_id = 90 OR doctor_name ILIKE '%Ricardo Jeczmionski%';

-- 2. Fallback Padrão da Clínica Inovare para médicos sem URL específica
UPDATE doctor_configurations SET google_review_url = 'https://share.google/OBtREC0KLjzx1YNOP' WHERE google_review_url IS NULL OR google_review_url = '';

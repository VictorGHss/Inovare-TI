-- Atualização da check constraint para suportar os novos estados de resiliência da Conta Azul
ALTER TABLE processed_receipts DROP CONSTRAINT ck_processed_receipts_status;

ALTER TABLE processed_receipts ADD CONSTRAINT ck_processed_receipts_status 
CHECK (status IN ('SENT', 'SKIPPED_DUPLICATE', 'FAILED', 'PENDING_RETRY', 'HISTORICO', 'FAILED_RETRYABLE', 'FAILED_PERMANENT'));

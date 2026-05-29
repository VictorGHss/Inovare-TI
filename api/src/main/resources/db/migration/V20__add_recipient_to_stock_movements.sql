-- Migration V20: Rastreabilidade FIFO com Recebedor e Suporte a Compras Parceladas de Lotes

-- 1) Adiciona recebedor às saídas de estoque
ALTER TABLE stock_movements ADD COLUMN recipient_user_id UUID;
ALTER TABLE stock_movements ADD CONSTRAINT fk_stock_movements_recipient_user FOREIGN KEY (recipient_user_id) REFERENCES users(id) ON DELETE SET NULL;

-- 2) Cria tabela de parcelamento de lotes de estoque
CREATE TABLE stock_batch_installments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_batch_id UUID NOT NULL REFERENCES stock_batches(id) ON DELETE CASCADE,
    due_date DATE NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    installment_number INT NOT NULL
);

CREATE INDEX idx_stock_batch_installments_batch ON stock_batch_installments(stock_batch_id);
CREATE INDEX idx_stock_batch_installments_due_date ON stock_batch_installments(due_date);

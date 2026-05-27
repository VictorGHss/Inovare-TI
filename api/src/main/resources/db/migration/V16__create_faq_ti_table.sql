-- V16: Criação da tabela de FAQ local da TI para o comando /ajuda do Discord
CREATE TABLE faq_ti (
    id SERIAL PRIMARY KEY,
    palavra_chave VARCHAR(80) NOT NULL,
    pergunta TEXT NOT NULL,
    resposta TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- Insere o registro semente (seed) de exemplo
INSERT INTO faq_ti (palavra_chave, pergunta, resposta)
VALUES (
    'feegow',
    'Como limpar o cache do Feegow?',
    'Feche o navegador, pressione Ctrl+F5 ou vá em configurações e limpe os dados de navegação.'
);

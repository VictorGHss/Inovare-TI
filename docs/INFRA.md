**Infra — Storage e Segurança**

Pacote: `api/src/main/java/br/dev/ctrls/inovareti/infra`

Principais serviços de infraestrutura:

- `LocalFileStorageService` (`infra/storage`)
  - Armazena arquivos no sistema de arquivos do servidor em diretório configurado por `file.upload-dir`.
  - Gera nomes únicos usando `UUID` e valida extensão/segurança ao resolver caminhos.
  - Limite de upload configurável via `app.upload.max-file-size-bytes` (padrão 5MB).
  - Métodos: `store(MultipartFile)`, `load(String) -> Resource`, `delete(String)`.

- `EncryptionService` (`infra/security`)
  - Implementa criptografia AES-GCM (AES/GCM/NoPadding) para proteger segredos do `Vault`.
  - Deriva a chave a partir de `app.vault.encryption-key` usando SHA-256 (32 bytes).
  - Gera IV de 12 bytes (SecureRandom), inclui IV no payload codificado em Base64.
  - Métodos: `encrypt(String)`, `decrypt(String)` e validações de integridade.
  - Erros de criptografia lançam `IllegalStateException` com mensagens claras.

- `TwoFactorSessionGuard` (`infra/security`)
  - Valida se a sessão atual foi marcada como `twoFactorVerified` (presente nos detalhes da autenticação populados pelo `SecurityFilter`).
  - Verifica também se o usuário ainda possui TOTP configurado no banco (`user.getTotpSecret()`).
  - Usa `UserRepository` para checar estado atual do 2FA.

Observações operacionais e segurança:
- A chave `app.vault.encryption-key` deve ser tratada como segredo (armazenar em vault de infraestrutura, não em repositório).
- AES-GCM com IV aleatório e tag de 128 bits fornece confidencialidade e integridade; validação do tamanho do payload evita erros de parsing.
- `LocalFileStorageService` armazena arquivos localmente — em produção, considere usar S3/Blob Storage para alta disponibilidade e backups.

Recomendações rápidas:
- Rotacionar `app.vault.encryption-key` exige migração dos segredos — documentar procedimento de rotação (re-encrypt com nova chave).
- Adicionar monitoramento/alertas no diretório de upload (uso de disco) e políticas de retenção/limpeza.

Referências: `LocalFileStorageService`, `EncryptionService`, `TwoFactorSessionGuard`.

# Plano de Transição do Piloto e Ativação de Planos

Este documento serve como guia de operações para ativação da lógica de cobrança e transição do piloto do motor de confirmação de consultas da **Inovare-TI**.

---

## (A) Ativação da Cobrança no Ambiente (`.env` / `.env.servidor`)

Atualmente, o sistema opera com a feature flag de faturamento inativa por padrão (dormente). Para iniciar a cobrança ativa e barrar disparos automáticos de médicos não pagantes ou inadimplentes:

1. Acesse o servidor ou o ambiente onde a aplicação está rodando.
2. Edite o arquivo de variáveis de ambiente correspondente (`.env` ou `.env.servidor`).
3. Adicione ou edite a seguinte linha, alterando de `false` para `true`:
   ```bash
   APP_BILLING_ENABLED=true
   ```
4. Reinicie o contêiner Docker da API para recarregar as propriedades da JVM:
   ```bash
   docker compose down api && docker compose up -d api
   ```

> [!IMPORTANT]
> Enquanto a chave `APP_BILLING_ENABLED` for `false` (ou estiver ausente), o motor de agendamentos funcionará normalmente para todos os médicos cadastrados, ignorando as colunas `is_active` e `subscription_end_date` no banco de dados.

---

## (B) Comandos SQL para Gestão de Assinaturas (Médicos Pagantes)

As assinaturas dos médicos são controladas pela tabela `appointment_doctor_mapping`. Quando `APP_BILLING_ENABLED=true` está ativo, o disparo de agendamentos de um médico ocorrerá apenas se:
* `is_active` for `true` **OU**
* `subscription_end_date` for uma data futura (`subscription_end_date > AGORA`).

Abaixo estão os comandos SQL administrativos para gerenciamento:

### 1. Consultar médicos cadastrados e o status de faturamento atual
```sql
SELECT id, profissional_id, profissional_nome, blip_queue_id, is_active, subscription_end_date 
FROM appointment_doctor_mapping;
```

### 2. Ativar assinatura de um médico específico (Definir como ativo por tempo indeterminado)
```sql
UPDATE appointment_doctor_mapping 
SET is_active = true, subscription_end_date = NULL 
WHERE profissional_id = '8'; -- Substitua '8' pelo ID do profissional no Feegow
```

### 3. Conceder um período de assinatura com data de expiração (Ex: até 31 de Julho de 2026)
```sql
UPDATE appointment_doctor_mapping 
SET is_active = false, subscription_end_date = '2026-07-31 23:59:59' 
WHERE profissional_id = '6'; -- Substitua '6' pelo ID do profissional no Feegow
```

### 4. Bloquear/Suspender temporariamente um médico específico
```sql
UPDATE appointment_doctor_mapping 
SET is_active = false, subscription_end_date = NULL 
WHERE profissional_id = '12'; -- Substitua '12' pelo ID do profissional no Feegow
```

### 5. Suspender em massa todos os médicos inativos/inadimplentes (Exceto médicos piloto pagantes)
```sql
UPDATE appointment_doctor_mapping 
SET is_active = false, subscription_end_date = NULL 
WHERE profissional_id NOT IN ('8', '6', '7', '13', '14', '12');
```

---

## (C) Template de Comunicação Pré-Desativação (48 Horas Antes)

Este template deve ser adaptado e disparado para os profissionais ou secretários **48 horas antes** de qualquer desativação do envio automático de mensagens por falta de assinatura.

### Template de Mensagem (WhatsApp / E-mail)

**Assunto:** Transição do Piloto de Confirmações Automáticas — Inovare-TI

> **Olá, atendimento do [Nome do Médico/Profissional]!**
>
> Esperamos que este contato encontre vocês bem.
>
> Durante a última semana, realizamos o piloto do novo **Motor de Confirmações Automáticas via WhatsApp** integrado à agenda Feegow de vocês. Os resultados iniciais foram excelentes, otimizando o fluxo de faltas e aumentando a taxa de confirmação dos pacientes em tempo recorde!
>
> Gostaríamos de comunicar que a fase de testes gratuitos do piloto se encerrará nas próximas 48 horas. Para garantir que seus pacientes continuem recebendo os lembretes automáticos e que a integração com o painel do WhatsApp Desk não seja interrompida, é necessária a regularização da sua assinatura da ferramenta.
>
> **Resumo do Status:**
> * **Serviço Integrado:** Motor de Confirmações Automáticas + Blip Desk
> * **Fim do Período de Testes:** [Data Final - ex: 25/06/2026] às 18:00
> * **Ação Necessária:** Contate o suporte financeiro no link abaixo para selecionar seu plano e manter o serviço ativo.
>
> 👉 **[Clique aqui para Falar com nosso Suporte Financeiro e Regularizar](https://suporte.inovareti.com.br/financeiro)**
>
> *Nota: Caso a regularização não seja efetuada até o fim do prazo acima, o envio de lembretes automáticos do seu consultório será pausado temporariamente, sem prejuízo aos agendamentos existentes no Feegow.*
>
> Agradecemos a parceria de sempre e seguimos à disposição para qualquer dúvida técnica!
>
> Atenciosamente,  
> **Suporte Técnico — Inovare-TI**

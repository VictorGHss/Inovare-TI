# Kit de Expansão — Arquivos necessários para montar o prompt do agente de IA

Objetivo: reunir os trechos de código essenciais que o agente de IA precisa para entender a "baixa" de estoque, o vínculo financeiro e o contrato visual do frontend (cores/layout/api). Cole os arquivos ou caminhos abaixo para que eu monte o prompt e gere mudanças seguras.

---

## 🔍 O que eu preciso ver (O "Kit de Expansão")

1) No Backend — A lógica da "baixa" (prioridade Alta)

- `InventoryService.java` ou `StockService.java` ou `StockDeductionService.java`
  - O método(s) que reduz `remaining_quantity`/`current_stock` em `stock_batches` ou `items`.
  - Incluir: assinatura do método, anotações (`@Transactional`), chamadas a `*Repository` (por exemplo `stockBatchRepository`, `stockMovementRepository`, `itemRepository`) e payloads que criam `stock_movements`.
  - Por favor cole também as classes/entidades relacionadas: `StockBatch.java`, `Item.java`, `StockMovement.java` (apenas campos e construtores relevantes se o arquivo for grande).

  - Onde procurar: `api/src/main/java/**/inventory/**`, `api/src/main/java/**/domain/inventory/**`, `api/src/main/java/**/service/**`.

2) No Backend — Ponto onde o ticket gera consumo

- `TicketService.java` (ou `TicketWorkflow`, `TicketHandler`)
  - O trecho onde o status do ticket muda para `RESOLVED` / `CLOSED` e o código decide gerar consumo de material.
  - Incluir: condição que verifica `requested_item_id`/`requested_quantity`, chamada para o service de inventário e qualquer evento/producer usado.

3) No Backend — Mapeamento financeiro/contaazul

- `FinancialLink.java` (ou `DoctorFinancialLink`, `ContaAzulLink`)
  - Entidade que mapeia o médico (ou cliente interno) ao identificador do ContaAzul (campo como `contaazul_id`, `contaAzulCustomerId`, etc.).
  - Incluir a definição da tabela/colunas e quaisquer repositórios/queries associadas.

---

4) No Frontend — Estilização e consumo

- `tailwind.config.js`
  - Quero ver a seção `theme.extend.colors` para confirmar onde adicionar `#ffa751` (nome sugerido: `inovare` ou `primary`).

- `Layout.tsx` ou `Sidebar.tsx` (ou `Header.tsx` / `Footer.tsx`)
  - O componente principal de layout para replicar padding, containers, classes utilitárias e áreas onde inserir badges/links financeiros.

- `api.ts` (ou `services/api.ts` / `lib/api.ts`)
  - A instância de `axios` (ou fetch wrapper) e os tipos TypeScript usados para as respostas das chamadas financeiras (por exemplo: tipos de `Ticket`, `Item`, `StockBatch`, `FinancialLink`).

---

## ✅ O que incluir ao enviar o código

- O arquivo Java/TSX/JS completo **ou** o trecho relevante (método inteiro + imports + declaração de classe). Prefiro o arquivo completo quando possível.
- As interfaces / repositórios usados (por exemplo `StockBatchRepository`, `TicketRepository`, `ItemRepository`).
- As entidades envolvidas (`StockBatch`, `StockMovement`, `Item`, `Ticket`, `FinancialLink`).
- O `tailwind.config.js` inteiro (se não existir na raiz `front/`, indique qual arquivo de configuração usam).
- `api.ts` com as definições de tipos que alimentam o frontend.

---

## 🛠️ Comandos úteis para localizar arquivos (rodar na raiz do repositório)

PowerShell (Windows):

```powershell
# Procurar classes Java específicas
Get-ChildItem -Path api\src\main\java -Recurse -Filter *.java |
  Select-String -Pattern 'class InventoryService|class StockService|class StockDeductionService|class TicketService|class FinancialLink' -List |
  ForEach-Object { $_.Path }

# Procurar referências a campos de tabela/coluna (stock_batches, remaining_quantity)
Select-String -Path api\src\main\java\**\*.java -Pattern 'stock_batches|remaining_quantity|remainingQuantity|stockBatch' -SimpleMatch -List

# Frontend: localizar arquivos de layout e api
Get-ChildItem -Path front -Recurse -Include tailwind.config.js,Layout.tsx,Sidebar.tsx,api.ts -ErrorAction SilentlyContinue | Select-Object FullName
```

Se tiver `rg` (ripgrep) instalado (mais rápido):

```bash
rg "class (InventoryService|StockService|StockDeductionService|TicketService|FinancialLink)" api/src/main/java -n
rg "stock_batches|remaining_quantity|stockBatch" api/src/main/java -n
rg "tailwind.config.js|Layout.tsx|Sidebar.tsx|api.ts" front -n
```

Ou usando `git grep`:

```bash
git -C api grep -n "InventoryService\|StockService\|TicketService\|FinancialLink" || true
git -C front grep -n "tailwind.config.js\|Layout.tsx\|Sidebar.tsx\|api.ts" || true
```

---

## ✉️ Como me enviar os arquivos

- Ideal: cole cada arquivo no chat com um cabeçalho indicando o caminho, por exemplo:

```
File: api/src/main/java/br/dev/ctrls/inovareti/service/InventoryService.java
```java
<cole o conteúdo completo aqui>
```

- Alternativas:
  - Compacte os arquivos em um zip e anexe (se o ambiente permitir upload).
  - Crie um branch/commit com os arquivos e envie o link do PR ou do commit.

---

## Checklist rápido (copie e cole quando enviar)

- [ ] `InventoryService` / `StockDeductionService` (método que reduz lotes)
- [ ] Repositórios usados (`StockBatchRepository`, `StockMovementRepository`, `ItemRepository`)
- [ ] `TicketService` (trecho de fechamento do ticket)
- [ ] `FinancialLink` (entidade que mapeia médico → contaAzulId)
- [ ] `tailwind.config.js` (theme.extend.colors com `#ffa751`)
- [ ] `Layout.tsx` / `Sidebar.tsx` (componentes de layout)
- [ ] `api.ts` (axios/fetch wrapper e tipos TS)

---

Quando você enviar esses arquivos eu: 1) criarei um prompt objetivo para o agente de IA com exemplos de transformação (backend + frontend), 2) gerarei patches sugeridos e 3) implementarei as mudanças não intrusivas (se aprovar).

Qualquer dúvida, cole o primeiro arquivo (por exemplo, `InventoryService.java`) e eu já começo a analisar.

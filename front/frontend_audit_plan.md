# Auditoria de Front-End — Inovare TI
**Data:** Maio de 2026  
**Escopo:** Pasta `front/` — React 19 + TypeScript + Vite  
**Objetivo:** Mapear a mistura de bibliotecas de estilo e propor um plano de unificação do design system.

---

## 1. Varredura de Dependências

### 1.1 Bibliotecas de Estilo Instaladas

| Biblioteca | Versão | Tipo | Presença no Projeto |
|---|---|---|---|
| `tailwindcss` | `^4.2.1` | devDependency | ✅ Ativa — usada em todo o projeto |
| `@tailwindcss/vite` | `^4.2.1` | devDependency | ✅ Plugin Vite configurado |
| `autoprefixer` | `^10.4.27` | devDependency | ✅ Presente (PostCSS) |
| `framer-motion` | `^12.36.0` | dependency | ✅ Usada para animações de página |
| Bootstrap | — | — | ❌ **Não instalado** |
| `react-bootstrap` | — | — | ❌ **Não instalado** |
| `@mui/material` | — | — | ❌ **Não instalado** |

### 1.2 Conclusão da Varredura de Dependências

> **A premissa inicial da auditoria não se confirma na íntegra.**  
> O projeto **não possui Bootstrap nem MUI instalados** como dependências no `package.json`. A "colcha de retalhos" identificada é de natureza diferente: trata-se de **inconsistências internas dentro do próprio Tailwind CSS**, com mistura de tokens de design (variáveis CSS customizadas vs. classes utilitárias hardcoded), além de padrões de componentes não padronizados.

### 1.3 Importações Globais de Estilo

**`front/index.html`**
- Nenhuma tag `<link>` para CDN de Bootstrap ou MUI.
- Apenas o entry point `src/main.tsx`.

**`front/src/index.css`**
```css
@import "tailwindcss";

@theme {
  --color-brand-primary: #feb56c;
  --color-brand-primary-dark: #f1a154;
  --color-brand-secondary: #fed8b0;
  --color-primary: #feb56c;
  --color-primary-hover: #f1a154;
  --color-secondary: #fed8b0;
}
```

**`front/tailwind.config.cjs`**
```js
colors: {
  brand: {
    primary: '#feb56c',
    'primary-dark': '#f1a154',
    secondary: '#fed8b0'
  }
}
```

**Problema identificado:** Existem **dois sistemas de tokens paralelos** para as mesmas cores:
- `brand-primary` / `brand-secondary` (via `tailwind.config.cjs`)
- `primary` / `primary-hover` / `secondary` (via `@theme` no `index.css`)

Isso gera classes duplicadas e inconsistentes no código.

---

## 2. Mapeamento da Colcha de Retalhos

### 2.1 Inconsistências de Tokens de Cor

A varredura dos componentes e páginas revelou o uso **simultâneo e intercambiável** dos dois sistemas de tokens:

| Arquivo | Usa `brand-*` | Usa `primary`/`primary-hover` | Observação |
|---|---|---|---|
| `DefaultLayout/index.tsx` | `bg-brand-secondary`, `text-brand-primary` | `text-primary`, `hover:text-primary-hover` | Mistura no mesmo arquivo |
| `Dashboard/index.tsx` | `bg-brand-primary`, `bg-brand-secondary` | `bg-primary`, `hover:bg-primary-hover` | Mistura no mesmo arquivo |
| `Assets/index.tsx` | `bg-brand-primary`, `border-brand-primary/20` | `bg-primary-hover`, `focus:ring-primary` | Mistura no mesmo arquivo |
| `UploadInvoiceModal.tsx` | `bg-brand-secondary`, `border-brand-primary` | `border-primary`, `hover:border-primary`, `bg-primary` | Mistura no mesmo arquivo |
| `PrintLabelModal.tsx` | — | `bg-primary`, `hover:bg-primary-hover` | Usa apenas `primary` |
| `ReportHubModal.tsx` | `text-brand-primary`, `hover:border-brand-secondary` | `text-primary`, `hover:border-primary` | Mistura no mesmo arquivo |
| `SlaBadge.tsx` | `bg-brand-secondary`, `text-brand-primary` | — | Consistente |
| `StatusBadge.tsx` | — | — | Usa apenas cores semânticas do Tailwind (red, yellow, green) |
| `NotificationBell.tsx` | `bg-brand-primary`, `text-brand-primary`, `hover:bg-brand-secondary` | — | Consistente |
| `UserDropdown.tsx` | `bg-brand-primary` | — | Consistente |
| `PageHero.tsx` | `bg-brand-secondary/40` | — | Consistente |
| `Inventory/index.tsx` | `bg-brand-primary`, `text-brand-primary`, `bg-brand-secondary` | — | Consistente |
| `Tickets/index.tsx` | `bg-brand-primary`, `text-brand-primary` | — | Consistente |
| `Users/index.tsx` | `bg-brand-secondary/40`, `text-brand-primary-dark` | — | Consistente |
| `Login/index.tsx` | `bg-brand-secondary/20`, `bg-brand-primary` | — | Consistente |

### 2.2 Áreas Mais Afetadas pela Inconsistência

**Nível ALTO (mistura direta no mesmo arquivo):**
1. **`Dashboard/index.tsx`** — usa `bg-primary` e `bg-brand-primary` no mesmo componente, para botões visualmente idênticos.
2. **`Assets/index.tsx`** — `inputClassName` usa `focus:ring-primary` enquanto botões usam `bg-brand-primary`.
3. **`UploadInvoiceModal.tsx`** — área de drag & drop usa `border-primary` e `bg-primary`, enquanto alertas usam `bg-brand-secondary`.
4. **`DefaultLayout/index.tsx`** — link do footer usa `text-primary hover:text-primary-hover`, enquanto a navegação usa `text-brand-primary`.
5. **`ReportHubModal.tsx`** — ícones e bordas misturam `text-primary` com `text-brand-primary`.

**Nível MÉDIO (padrão inconsistente entre arquivos similares):**
6. **`TicketDetails/index.tsx`** — botão "Voltar" usa cor hardcoded `border-[#feb56c]/40` e `hover:bg-[#fff8f1]` em vez de tokens.
7. **`PrintLabelModal.tsx`** — usa exclusivamente `primary`/`primary-hover`, divergindo do padrão `brand-*` adotado pela maioria.

### 2.3 Outros Problemas de Padronização

- **Cores hardcoded:** `border-[#feb56c]/40`, `hover:bg-[#fff8f1]`, `text-[#ffa751]` aparecem em `TicketDetails` e `Users`, contornando o sistema de tokens.
- **Ausência de componentes atômicos reutilizáveis:** Botões primários são recriados com as mesmas classes em cada página (ex: `flex items-center gap-2 bg-brand-primary hover:bg-brand-primary-dark text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors`), sem um componente `<Button>` centralizado.
- **Modais sem abstração:** `UploadInvoiceModal`, `PrintLabelModal`, `ReportHubModal` e os modais inline em `Users` compartilham a mesma estrutura de overlay (`fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50`) sem um componente `<Modal>` base.
- **Estrutura de componentes plana:** Todos os 17 componentes estão na raiz de `src/components/` sem subdivisão por domínio ou tipo.

---

## 3. Plano de Extinção das Inconsistências de Tokens

> Como Bootstrap e MUI não estão presentes, o plano foca na **unificação dos tokens de cor** e na **criação de componentes atômicos** para eliminar a duplicidade.

### 3.1 Fase 1 — Unificação dos Tokens (Sem Risco de Quebra)

**Objetivo:** Eliminar o sistema `primary`/`primary-hover`/`secondary` e manter apenas `brand-*`.

**Passo 1 — Remover tokens duplicados do `index.css`:**
```css
/* REMOVER do @theme em index.css */
--color-primary: #feb56c;        /* duplicata de brand-primary */
--color-primary-hover: #f1a154;  /* duplicata de brand-primary-dark */
--color-secondary: #fed8b0;      /* duplicata de brand-secondary */
```

**Passo 2 — Substituição global de classes:**

| Classe a remover | Substituir por |
|---|---|
| `bg-primary` | `bg-brand-primary` |
| `hover:bg-primary` | `hover:bg-brand-primary` |
| `bg-primary-hover` | `bg-brand-primary-dark` |
| `hover:bg-primary-hover` | `hover:bg-brand-primary-dark` |
| `text-primary` | `text-brand-primary` |
| `hover:text-primary-hover` | `hover:text-brand-primary-dark` |
| `border-primary` | `border-brand-primary` |
| `hover:border-primary` | `hover:border-brand-primary` |
| `focus:ring-primary` | `focus:ring-brand-primary` |
| `focus:border-primary` | `focus:border-brand-primary` |
| `text-secondary` | `text-brand-secondary` |
| `bg-secondary` | `bg-brand-secondary` |

**Passo 3 — Eliminar cores hardcoded:**

| Ocorrência | Arquivo | Substituir por |
|---|---|---|
| `border-[#feb56c]/40` | `TicketDetails/index.tsx` | `border-brand-primary/40` |
| `hover:bg-[#fff8f1]` | `TicketDetails/index.tsx` | `hover:bg-brand-secondary/30` |
| `text-[#ffa751]` | `Users/index.tsx` | `text-brand-primary` |

**Arquivos afetados pela Fase 1:**
- `src/pages/Dashboard/index.tsx`
- `src/pages/Assets/index.tsx`
- `src/pages/TicketDetails/index.tsx`
- `src/pages/Users/index.tsx`
- `src/components/UploadInvoiceModal.tsx`
- `src/components/PrintLabelModal.tsx`
- `src/components/ReportHubModal.tsx`
- `src/layouts/DefaultLayout/index.tsx`
- `src/index.css`

### 3.2 Fase 2 — Criação de Componentes Atômicos

**Objetivo:** Centralizar padrões repetidos em componentes reutilizáveis.

**Componente `<Button>` (`src/components/common/Button.tsx`):**
```tsx
// Variantes: primary | secondary | ghost | danger
// Tamanhos: sm | md | lg
// Suporte a: loading, disabled, ícone à esquerda
```

Elimina a repetição de `flex items-center gap-2 bg-brand-primary hover:bg-brand-primary-dark text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors` em pelo menos **12 arquivos**.

**Componente `<Modal>` (`src/components/common/Modal.tsx`):**
```tsx
// Props: isOpen, onClose, title, size (sm | md | lg)
// Inclui: overlay, header com botão X, body, footer slot
```

Elimina a duplicação da estrutura de overlay em `UploadInvoiceModal`, `PrintLabelModal`, `ReportHubModal` e nos modais inline de `Users`.

**Componente `<Badge>` (`src/components/common/Badge.tsx`):**
```tsx
// Variantes: success | warning | danger | info | neutral | brand
// Unifica StatusBadge e SlaBadge em uma base comum
```

### 3.3 Fase 3 — Consolidação do `tailwind.config.cjs`

Com o Tailwind v4 (que este projeto usa), a configuração via `@theme` no CSS é a abordagem recomendada. O `tailwind.config.cjs` pode ser simplificado ou removido, centralizando tudo no `index.css`:

```css
/* index.css — fonte única de verdade para tokens */
@import "tailwindcss";

@theme {
  --color-brand-primary:      #feb56c;
  --color-brand-primary-dark: #f1a154;
  --color-brand-secondary:    #fed8b0;
  /* Remover aliases duplicados */
}
```

---

## 4. Padronização de Diretórios e Componentes

### 4.1 Estrutura Atual

```
front/src/
├── assets/
├── components/          ← 17 arquivos misturados sem subdivisão
├── contexts/
├── hooks/
├── layouts/
│   └── DefaultLayout/
├── lib/
├── pages/
│   ├── Assets/
│   ├── Dashboard/
│   ├── Financeiro/
│   ├── Inventory/
│   ├── KnowledgeBase/
│   ├── Login/
│   ├── NewTicket/
│   ├── PrimeiroAcesso/
│   ├── Profile/
│   ├── Sectors/
│   ├── Settings/
│   ├── SystemLogs/
│   ├── TicketDetails/
│   ├── Tickets/
│   ├── Users/
│   └── Vault/
├── services/
├── types/
│   └── models/
├── App.tsx
├── index.css
└── main.tsx
```

**Problemas identificados:**
- `src/components/` é uma pasta plana com componentes de domínios completamente diferentes (UI genérico, domínio de tickets, domínio financeiro, domínio de inventário).
- Não há separação entre componentes de UI reutilizáveis e componentes de domínio.
- Hooks em `src/hooks/` são todos de domínio específico (financeiro, tickets), mas não há hooks de UI genéricos.
- `src/lib/` contém utilitários mistos (formatadores, paleta de cores, tratamento de erros de API).

### 4.2 Estrutura Proposta

```
front/src/
├── assets/
│
├── components/
│   ├── common/              ← Componentes de UI genéricos e reutilizáveis
│   │   ├── Button.tsx
│   │   ├── Badge.tsx
│   │   ├── Modal.tsx
│   │   ├── SkeletonTable.tsx
│   │   ├── PageHero.tsx
│   │   └── index.ts         ← Re-exporta tudo
│   │
│   ├── feedback/            ← Notificações, alertas, estados vazios
│   │   ├── NotificationBell.tsx
│   │   ├── StatusBadge.tsx
│   │   └── SlaBadge.tsx
│   │
│   ├── charts/              ← Componentes de visualização de dados
│   │   ├── ChartsBar.tsx
│   │   ├── ChartsPie.tsx
│   │   └── InventorySummaryCard.tsx
│   │
│   ├── tickets/             ← Componentes específicos do domínio de chamados
│   │   ├── TicketComments.tsx
│   │   └── UserTicketHistory.tsx
│   │
│   ├── inventory/           ← Componentes específicos do domínio de inventário
│   │   └── ReceivedItemsCard.tsx
│   │
│   └── shared/              ← Componentes transversais (usados em múltiplos domínios)
│       ├── MarkdownRenderer.tsx
│       ├── QrScannerLauncher.tsx
│       ├── PrintLabelModal.tsx
│       ├── UploadInvoiceModal.tsx
│       ├── ReportHubModal.tsx
│       └── UserDropdown.tsx
│
├── contexts/
│   └── AuthContext.tsx
│
├── hooks/
│   ├── ui/                  ← Hooks de comportamento de UI
│   │   └── useClickOutside.ts
│   │
│   └── domain/              ← Hooks de lógica de negócio
│       ├── useFinancialActions.ts
│       ├── useFinancialDashboard.ts
│       ├── useResolveTicket.ts
│       ├── useSystemLogs.ts
│       └── useTicketDetails.ts
│
├── layouts/
│   └── DefaultLayout/
│       └── index.tsx
│
├── lib/
│   ├── api/
│   │   └── apiError.ts
│   ├── formatters.ts
│   └── chartPalette.ts
│
├── pages/                   ← Mantém estrutura atual por domínio
│   └── ...
│
├── services/                ← Mantém estrutura atual
│   └── ...
│
├── types/
│   └── models/
│
├── App.tsx
├── index.css
└── main.tsx
```

### 4.3 Modelo de Layout Wrapper

O `DefaultLayout` atual já está bem estruturado. A proposta é formalizá-lo com slots explícitos e extrair o hook de realtime para fora do componente de layout:

```tsx
// src/layouts/DefaultLayout/index.tsx — estrutura proposta

export default function DefaultLayout() {
  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      {/* Cabeçalho fixo com logo, navegação desktop e ações do usuário */}
      <AppHeader />

      <div className="flex flex-1 min-h-0">
        {/* Sidebar mobile deslizante (overlay) */}
        <MobileSidebar />

        {/* Área de conteúdo principal */}
        <div className="flex min-w-0 flex-1 flex-col">
          <main className="flex-1 w-full max-w-full">
            {/* Animação de transição entre rotas */}
            <AnimatePresence mode="wait">
              <motion.div key={location.pathname} ...>
                <Outlet />   {/* ← Conteúdo da página injetado aqui */}
              </motion.div>
            </AnimatePresence>
          </main>

          {/* Rodapé fixo */}
          <AppFooter />
        </div>
      </div>
    </div>
  );
}
```

**Extração recomendada:**
- `AppHeader` → `src/layouts/DefaultLayout/AppHeader.tsx`
- `MobileSidebar` → `src/layouts/DefaultLayout/MobileSidebar.tsx`
- `AppFooter` → `src/layouts/DefaultLayout/AppFooter.tsx`
- `useAppointmentRealtime` → `src/hooks/domain/useAppointmentRealtime.ts` (extrair o `useEffect` de WebSocket do layout)

**Padrão de página autenticada:**
```tsx
// Toda página autenticada segue este padrão:
export default function MinhaPage() {
  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      {/* 1. Cabeçalho da página */}
      <PageHero
        eyebrow="Módulo"
        title="Título da Página"
        description="Descrição breve do módulo."
        actions={<BotoesDeAcao />}
      />

      {/* 2. Filtros / Controles (quando aplicável) */}
      <section className="mb-4 bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
        <Filtros />
      </section>

      {/* 3. Conteúdo principal */}
      <section className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6">
        <Conteudo />
      </section>
    </main>
  );
}
```

Este padrão já é seguido consistentemente por `Inventory`, `Tickets`, `Users`, `Assets` e `TicketDetails`. Formalizá-lo como convenção documentada garante que novas páginas sigam o mesmo visual.

---

## 5. Resumo Executivo e Prioridades

### O que foi encontrado

| Problema | Severidade | Impacto |
|---|---|---|
| Dois sistemas de tokens de cor paralelos (`brand-*` vs `primary`) | 🔴 Alta | Inconsistência visual entre páginas |
| Cores hardcoded (`#feb56c`, `#fff8f1`, `#ffa751`) | 🟡 Média | Dificulta manutenção e theming |
| Ausência de componente `<Button>` centralizado | 🟡 Média | ~12 duplicações de classes de botão |
| Ausência de componente `<Modal>` base | 🟡 Média | ~5 duplicações de estrutura de overlay |
| Pasta `components/` plana sem organização por domínio | 🟢 Baixa | Dificulta descoberta e manutenção |
| Hook de WebSocket acoplado ao layout | 🟢 Baixa | Dificulta testes e reutilização |

### O que NÃO foi encontrado

- ❌ Bootstrap (nem CDN, nem pacote npm)
- ❌ `react-bootstrap`
- ❌ `@mui/material` ou qualquer componente MUI
- ❌ Importações de CSS de terceiros além do `react-toastify`

### Ordem de Execução Recomendada

1. **Fase 1** — Unificar tokens: remover `primary`/`primary-hover`/`secondary` do `@theme` e substituir todas as ocorrências por `brand-*`. **Baixo risco, alto impacto visual.**
2. **Fase 2** — Criar `<Button>` e `<Modal>` em `src/components/common/`. Migrar gradualmente.
3. **Fase 3** — Reorganizar `src/components/` conforme estrutura proposta.
4. **Fase 4** — Extrair sub-componentes do `DefaultLayout` e o hook de realtime.

---

*Relatório gerado por auditoria local do repositório. Nenhum arquivo de código-fonte foi modificado.*

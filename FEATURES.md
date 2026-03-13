# FEATURES

Resumo executivo dos modulos principais da plataforma Inovare TI.

## Identidade e Experiencia

- Identidade visual baseada na cor primaria `#ffa751`, com interface responsiva e foco em leitura rapida.
- Navegacao pensada para desktop e mobile, incluindo experiencia instalada como app.
- Dashboard executivo com cards de resumo, rankings Top 5 e graficos de leitura operacional.

## Vault

- Cofre seguro para credenciais, documentos e notas sensiveis.
- Protecao por 2FA para leitura de segredos e visualizacao de anexos.
- Compartilhamento privado, por times tecnicos ou customizado por usuario.
- Edicao e exclusao de itens com controle estrito: apenas `owner_id` ou `ADMIN` podem alterar ou remover registros.
- Auditoria para autenticacao 2FA, criacao, leitura sensivel, edicao, exclusao e eventos de acesso.

## Chamados

- Abertura, atribuicao, transferencia, resolucao e fechamento de tickets com SLA.
- Priorizacao por severidade e categorizacao por dominio tecnico.
- Rankings por setores e solicitantes para analise de demanda.
- Suporte a solicitacoes de itens com integracao ao estoque.
- Notificacoes inteligentes por perfil (`ADMIN`/`TECHNICIAN`) com preferencia individual de recebimento.

## Inventario e Ativos

- Controle de itens, categorias, lotes e entradas de estoque.
- Cadastro de ativos vinculados a usuarios e acompanhamento de manutencoes.
- Visao de estoque baixo e indicadores consolidados para operacao.
- Integracao com QR Code e historico operacional no dashboard.
- Auditoria dedicada para criacao de itens, criacao de lotes, cadastro de ativos, edicao de ativos e leitura por QR Code.

## Base de Conhecimento

- Artigos com `status` `DRAFT` ou `PUBLISHED`, mantidos no schema base via `V1__init.sql`.
- Rascunhos visiveis apenas para o autor ou para `ADMIN`.
- Fluxo completo de criacao, publicacao e edicao no frontend e backend.

## PWA e Mobilidade

- Aplicacao instalavel com manifesto, atalhos e comportamento mobile-first.
- Leitura nativa de QR Code para navegar internamente no sistema.
- Layout adaptado para uso em smartphones, tablets e desktops.

## Auditoria 360

- Trilha de auditoria desacoplada via eventos Spring, com persistencia assicrona.
- Cobertura de autenticacao, Vault, chamados, inventario, ativos, base de conhecimento, perfil e gestao de usuarios.
- Consulta administrativa de logs com filtros por usuario, acao e periodo.
- Captura de IP com suporte a `X-Forwarded-For` para ambientes atras de proxy.
- Mapeamento expandido com eventos canonicos para: `TICKET_OPEN`, `TICKET_ASSIGN`, `TICKET_TRANSFER`, `TICKET_RESOLVE`, `STOCK_BATCH_CREATE`, `ITEM_CREATE`, `ASSET_CREATE`, `ASSET_EDIT`, `ASSET_QR_SCAN`, `ARTICLE_POST_PUBLIC`, `ARTICLE_POST_DRAFT`, `ARTICLE_EDIT`, `VAULT_AUTH_SUCCESS`, `VAULT_AUTH_FAIL`, `VAULT_ITEM_CREATE`, `VAULT_ITEM_VIEW`, `VAULT_ITEM_EDIT`, `VAULT_ITEM_DELETE`, `USER_CREATE`, `USER_EDIT`, `USER_PASSWORD_ADMIN_RESET`, `USER_2FA_ADMIN_RESET`, `SECTOR_CREATE` e `PROFILE_PASSWORD_CHANGE`.

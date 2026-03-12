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
- Auditoria para criacao, leitura sensivel, edicao, exclusao e eventos de acesso.

## Chamados

- Abertura, atribuicao, transferencia, resolucao e fechamento de tickets com SLA.
- Priorizacao por severidade e categorizacao por dominio tecnico.
- Rankings por setores e solicitantes para analise de demanda.
- Suporte a solicitacoes de itens com integracao ao estoque.

## Inventario e Ativos

- Controle de itens, categorias, lotes e entradas de estoque.
- Cadastro de ativos vinculados a usuarios e acompanhamento de manutencoes.
- Visao de estoque baixo e indicadores consolidados para operacao.
- Integracao com QR Code e historico operacional no dashboard.

## PWA e Mobilidade

- Aplicacao instalavel com manifesto, atalhos e comportamento mobile-first.
- Leitura nativa de QR Code para navegar internamente no sistema.
- Layout adaptado para uso em smartphones, tablets e desktops.

## Auditoria 360

- Trilha de auditoria desacoplada via eventos Spring, com persistencia assicrona.
- Cobertura de autenticacao, Vault, chamados, inventario, ativos, base de conhecimento e gestao de usuarios.
- Consulta administrativa de logs com filtros por usuario, acao e periodo.
- Captura de IP com suporte a `X-Forwarded-For` para ambientes atras de proxy.

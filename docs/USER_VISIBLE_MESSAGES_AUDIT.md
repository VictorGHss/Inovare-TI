# Auditoria de Mensagens Visíveis ao Usuário

Este relatório lista mensagens apresentadas por endpoints que são visíveis a usuários finais e recomenda traduções/ajustes.

Observações gerais:
- Política do projeto: mensagens visíveis ao usuário devem estar em português.
- Mudanças de texto devem preservar chaves de API públicas e contratos (JSON key names) quando clientes dependem delas.

Mensagens encontradas e ações tomadas:

- `ContaAzulController.forceRefresh`:
  - Mensagens retornadas: `{"autorizado": true, ...}` e `{"erro": "Aguarde antes de requisitar novo refresh"}` — já em português.
  - Ação: mantidas.

- Endpoints OAuth públicos (`/authorize`, `/callback`): redirecionamentos mantidos; mensagens internas não expostas.

Recomendações adicionais:
- Fazer varredura automática por strings literais em `src/main/resources` e controladores que retornam `Map.of("erro", ...)` e consolidar para `messages.properties` se internacionalização avançada for necessária.
- Validar com frontend se a chave JSON `autorizado` deve permanecer (compatibilidade).


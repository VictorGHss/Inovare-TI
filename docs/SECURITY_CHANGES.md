# Alterações de Segurança

Alterações aplicadas para resolver 403 em POST /api/auth/login:

- `SecurityConfig.java`
  - Liberado explicitamente os padrões de rota para autenticação tanto com
    o prefixo `/api` quanto sem ele: `/auth/**` e `/api/auth/**`.
  - Também foram liberados os endpoints do ContaAzul com e sem prefixo `/api`.
  - Observação: o CSRF já estava desabilitado com `.csrf(csrf -> csrf.disable())`.

- `SecurityFilter.java`
  - Atualizado `shouldNotFilter` para pular o filtro JWT quando a URI conter
    `/auth/` (assim o login não precisa de token) e também quando conter os
    caminhos do ContaAzul.

Motivação:
- Quando a aplicação define `server.servlet.context-path=/api`, o texto da URI
  pode conter o prefixo `/api` antes do caminho do controller. Para evitar
  discrepâncias entre o que o SecurityMatcher espera e a URI real, liberamos
  ambas as formas e ajustamos o filtro para detectar a substring `/auth/`.

Como testar rapidamente:
1. Reinicie a aplicação.
2. Verifique com `curl` do frontend ou do servidor se `POST https://<host>/api/auth/login`
   retorna 200/401 (dependendo das credenciais) ao invés de 403.


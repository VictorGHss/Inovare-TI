# Playbook: Triagem de alerta ContaAzulForceRefreshThrottled

Objetivo: guiar SRE/Dev na triagem de alertas gerados pela regra `ContaAzulForceRefreshThrottled`.

1) Identificar o alerta
- Verificar alerta no Alertmanager/Slack: `ContaAzulForceRefreshThrottled`.
- Anotar timestamp inicial e instâncias afetadas.

2) Consultar métricas
- Query rápida no Prometheus:
  - `increase(contaazul_force_refresh_throttled_total[5m])`
  - `rate(contaazul_force_refresh_throttled_total[5m])`
- Verificar tendência em 1h/6h.

3) Verificar logs da API
- Procurar entradas de log do `ContaAzulController` com WARN/INFO:

  - Comando (Kubernetes):

```bash
kubectl logs -l app=inovare-ti -n namespace --tail=200 | grep "ContaAzul force-refresh"
```

- Localizar `who` (principal) e `ip` nos logs para entender origem.

4) Determinar ação imediata
- Se origem for um usuário administrativo legítimo e volume pequeno: avisar e aguardar (possível auto-throttle do sistema).
- Se origem for automação ou ataque (vários IPs ou um IP com alta taxa):
  - Bloquear IP/Range no WAF/Ingress.
  - Adicionar regra temporária no NGINX ingress para limitar `POST /financeiro/contaazul/force-refresh`.

5) Correção e mitigação
- Ajustar TTL/threshold no RedisRateLimiter (`FORCE_REFRESH_COOLDOWN_MS`) se necessário.
- Se for falso positivo devido a deploy ou testes, considerar ajustar testes/CI que estejam chamando o endpoint.

6) Auditoria e follow-up
- Registrar incidente com evidências (logs, métricas) e ações tomadas.
- Se necessário, programar melhoria: implementar bucket-based limiter (Bucket4j + Redis) para controle de burst.

7) Exemplos de comandos de bloqueio (NGINX):

- Bloquear IP via NetworkPolicy (k8s):
```bash
kubectl apply -f - <<EOF
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: deny-bad-ip
  namespace: namespace
spec:
  podSelector:
    matchLabels:
      app: inovare-ti
  policyTypes:
  - Ingress
  ingress:
  - from:
    - ipBlock:
        cidr: 1.2.3.4/32
EOF
```

8) Contatos
- Time Plataforma: slack #plataforma
- Devs responsáveis: @lead-dev


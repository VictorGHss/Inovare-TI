# Deploy atômico e tratamento do Service Worker

Este diretório contém um script simples para deploy atômico de builds estáticas (`deploy_atomic.sh`) e um snippet de Nginx para tratar o cache do `sw.js`.

Por que isso ajuda
- Evita que clientes fiquem com um `sw.js` apontando para assets que não existem mais (causa 404 e falha no install do Workbox).
- Permite publicar toda a pasta `dist` de uma vez e trocar o symlink atual, garantindo consistência entre `sw.js` e `assets/`.

Como usar

1. Gere a build no CI/runner:

```bash
cd front
npm ci
npm run build
```

2. Execute o script apontando para a pasta `dist` e o destino remoto (ex.: `web@host:/var/www/itsm`):

```bash
./ops/deploy_atomic.sh ./front/dist web@servidor:/var/www/itsm /var/www/itsm/current
```

3. Atualize a configuração do Nginx usando o snippet `ops/nginx_sw.conf` (copie o bloco para dentro do `server` do seu site) e recarregue o Nginx:

```bash
sudo nginx -t && sudo systemctl reload nginx
```

4. Se usar CDN, invalide o cache para `/sw.js` e `/assets/*` após o deploy.

Limpeza de Service Worker no cliente (quando ocorrer 404 no precache)

No console do navegador, se necessário, execute:

```javascript
navigator.serviceWorker.getRegistrations().then(rs=>rs.forEach(r=>r.unregister()));
caches.keys().then(keys=>Promise.all(keys.map(k=>caches.delete(k))));
```

Observações
- Este script assume que você tem acesso SSH ao servidor e `rsync` instalado. Adapte permissões e usuário (`www-data`) conforme necessário.
- Para pipelines mais sofisticados, integre invalidation da CDN e checks de saúde após o swap de symlink.

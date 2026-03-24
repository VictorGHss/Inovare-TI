package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cliente leve para consumir a API pública da Conta Azul.
 *
 * Contém utilitários de consulta para eventos financeiros, vendas e pessoas
 * e implementa lógica de tolerância a variações no formato do payload
 * retornado pela Conta Azul (chaves em inglês/português, arrays/objetos, etc.).
 *
 * Os métodos deste cliente fazem refresh automático de token quando necessário
 * e convertem os responses em DTOs simples usados pela aplicação.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContaAzulClient {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 30;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ObjectMapper objectMapper;
    private final ContaAzulTokenService contaAzulTokenService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${app.contaazul.payments-url}")
    private String receivableEventsSearchUrl;

    @Value("${app.contaazul.baixa-details-url}")
    private String baixaDetailsUrl;

    @Value("${app.contaazul.sales-pdf-v1-url-template:https://api-v2.contaazul.com/v1/venda/{id}/imprimir}")
    private String salePdfV1UrlTemplate;

    @Value("${app.contaazul.sales-v2-url:https://api-v2.contaazul.com/v1/venda/busca}")
    private String salesV2Url;

    @Value("${app.contaazul.sales-v2-stable-url:https://api.contaazul.com/v2/sales}")
    private String salesV2StableUrl;

    @Value("${app.contaazul.customers-v1-url:https://api-v2.contaazul.com/v1/pessoas}")
    private String customersV1Url;

    @Value("${app.contaazul.customer-by-id-v1-url-template:https://api-v2.contaazul.com/v1/pessoas/{id}}")
    private String customerByIdV1UrlTemplate;

    /**
     * Indica se existe configuração de template/endpoint para obter PDFs de venda.
     *
     * Útil para alternar comportamento quando a integração de vendas não estiver
     * configurada no ambiente.
     */
    public boolean hasSalesConfiguration() {
        return StringUtils.hasText(salePdfV1UrlTemplate);
    }

    /**
     * Consulta eventos financeiros (contas a receber) e extrai o sale_id (venda_id) para download de PDF.
     */
    public List<SaleItem> fetchAcquittedSales() {
        LocalDate hoje = LocalDate.now();
        String dataVencimentoDe = hoje.withDayOfMonth(1).format(DATE_FORMATTER);
        String dataVencimentoAte = hoje.format(DATE_FORMATTER);
        return fetchAcquittedSales(dataVencimentoDe, dataVencimentoAte);
        }

        public List<SaleItem> fetchAcquittedSales(String dataVencimentoDe, String dataVencimentoAte) {
        List<SaleItem> sales = new ArrayList<>();

        for (int page = 1; page <= MAX_PAGES; page++) {
            String uri = buildReceivableSearchUri(page, dataVencimentoDe, dataVencimentoAte);

            log.info("Solicitando lista de vendas ao Conta Azul: {}", uri);
            log.debug("Consultando vendas na Conta Azul. pagina={}, uri={}", page, uri);

            String payload = executeJsonGetWithRefresh(uri);
            log.debug("JSON bruto retornado pela Conta Azul (pagina {}): {}", page, payload);
            List<SaleItem> pageItems = parseAcquittedSales(payload);

            if (pageItems.isEmpty()) {
                break;
            }

            sales.addAll(pageItems);

            if (pageItems.size() < PAGE_SIZE) {
                break;
            }
        }

        return sales;
    }

    /**
     * Baixa o PDF do recibo da baixa financeira usando o endpoint oficial de baixas.
     * Novo fluxo: https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/parcelas/baixa/{baixa_id}
     * Busca o anexo do tipo RECIBO_DIGITAL ou RECIBO e faz o download.
     */
    public byte[] downloadReceiptPdf(String baixaId) {
        if (!StringUtils.hasText(baixaId)) {
            throw new IllegalArgumentException("baixaId não pode ser vazio para download do recibo");
        }
        String uri = "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/parcelas/baixa/" + baixaId.trim();
        log.info("Baixando recibo via endpoint oficial de baixas: {}", uri);
        try {
            String payload = executeJsonGetWithRefresh(uri);
            JsonNode root = objectMapper.readTree(payload.getBytes(StandardCharsets.UTF_8));
            JsonNode anexosNode = readArrayNode(root, "anexos", "evento.anexos", "data.anexos", "content.anexos");
            if (anexosNode != null && anexosNode.isArray()) {
                for (JsonNode anexoNode : anexosNode) {
                    String tipo = readText(anexoNode, "tipo", "type", "categoria");
                    String url = readText(anexoNode, "url", "link", "download_url", "arquivo.url");
                    if (StringUtils.hasText(tipo) && ("RECIBO_DIGITAL".equalsIgnoreCase(tipo) || "RECIBO".equalsIgnoreCase(tipo)) && StringUtils.hasText(url)) {
                        return downloadFile(url);
                    }
                }
            }
            // Nenhum anexo de recibo encontrado — sinaliza que o recibo ainda não foi gerado
            throw new NoReceiptAvailableException("Nenhum anexo de recibo encontrado para baixa " + baixaId);
        } catch (ContaAzulHttpException ex) {
            if (!ex.isStatus(401)) {
                throw ex;
            }
            log.warn("Token expirado ao baixar recibo da baixa {}. Tentando refresh.", baixaId);
            // Renova token e recarrega do banco para obter o objeto com metadata
            ContaAzulOAuthToken refreshed = contaAzulTokenService.forceRefreshAndReloadFromDatabase();
            // Tenta novamente usando explicitamente o token renovado
            String uriRetry = "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/parcelas/baixa/" + baixaId.trim();
            try {
                String payload = executeJsonGetResponse(uriRetry, refreshed).body();
                JsonNode root = objectMapper.readTree(payload.getBytes(StandardCharsets.UTF_8));
                JsonNode anexosNode = readArrayNode(root, "anexos", "evento.anexos", "data.anexos", "content.anexos");
                if (anexosNode != null && anexosNode.isArray()) {
                    for (JsonNode anexoNode : anexosNode) {
                        String tipo = readText(anexoNode, "tipo", "type", "categoria");
                        String url = readText(anexoNode, "url", "link", "download_url", "arquivo.url");
                        if (StringUtils.hasText(tipo) && ("RECIBO_DIGITAL".equalsIgnoreCase(tipo) || "RECIBO".equalsIgnoreCase(tipo)) && StringUtils.hasText(url)) {
                            return downloadFile(url, refreshed.getAccessToken());
                        }
                    }
                }
                // Nenhum anexo de recibo encontrado — sinaliza que o recibo ainda não foi gerado
                throw new NoReceiptAvailableException("Nenhum anexo de recibo encontrado para baixa " + baixaId);
            } catch (IOException e) {
                throw new IllegalStateException("Falha ao parsear payload de anexo após refresh do token para baixa " + baixaId, e);
            }
        } catch (IOException | IllegalStateException e) {
            throw e instanceof IOException ? new IllegalStateException("Falha ao baixar recibo da baixa " + baixaId, e) : (IllegalStateException) e;
        }
    }

    /**
     * Busca a baixa financeira de uma parcela pelo ID da parcela.
     * Endpoint: GET https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/parcelas/{parcela_id}/baixa
     * Retorna o id da primeira baixa encontrada.
     */
    public Optional<String> fetchBaixaIdByParcelaId(String parcelaId) {
        if (!StringUtils.hasText(parcelaId)) {
            return Optional.empty();
        }
        String uri = "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/parcelas/" + parcelaId.trim() + "/baixa";
        try {
            String payload = executeJsonGetWithRefresh(uri);
            JsonNode root = objectMapper.readTree(payload.getBytes(StandardCharsets.UTF_8));
            // O endpoint pode retornar um array de baixas ou um objeto único
            if (root.isArray() && root.size() > 0) {
                JsonNode first = root.get(0);
                String baixaId = readText(first, "id", "baixa_id");
                return StringUtils.hasText(baixaId) ? Optional.of(baixaId) : Optional.empty();
            } else if (root.isObject()) {
                String baixaId = readText(root, "id", "baixa_id");
                return StringUtils.hasText(baixaId) ? Optional.of(baixaId) : Optional.empty();
            }
        } catch (ContaAzulHttpException ex) {
            log.warn("Falha ao buscar baixa para parcela {}: erro HTTP ContaAzul.", parcelaId, ex);
        } catch (IOException ex) {
            log.warn("Falha ao parsear resposta de baixa para parcela {}.", parcelaId, ex);
        }
        return Optional.empty();
    }

    /**
     * Busca o ID do cliente na Conta Azul por e-mail.
     */
    public Optional<String> findCustomerIdByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return Optional.empty();
        }

        String normalizedEmail = email.trim();
        log.info("Buscando cliente no Conta Azul pelo e-mail: {}", normalizedEmail);

        String uri = UriComponentsBuilder.fromUriString(customersV1Url)
            .queryParam("emails", normalizedEmail)
            .build()
            .encode(StandardCharsets.UTF_8)
            .toUriString();

        try {
            String payload = executeJsonGetWithRefresh(uri);
            log.debug("Resposta da API Conta Azul: {}", payload);
            return parseCustomerIdByEmail(payload, normalizedEmail);
        } catch (RuntimeException ex) {
            log.warn("Falha ao consultar cliente Conta Azul por e-mail {}: {}", email, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Busca uma venda liquidada específica pelo seu ID.
     */
    public Optional<SaleItem> findAcquittedSaleById(String saleId) {
        if (!StringUtils.hasText(saleId)) {
            return Optional.empty();
        }

        String normalizedSaleId = saleId.trim();
        LocalDate hoje = LocalDate.now();
        String dataVencimentoDe = hoje.withDayOfMonth(1).format(DATE_FORMATTER);
        String dataVencimentoAte = hoje.format(DATE_FORMATTER);

        for (int page = 1; page <= MAX_PAGES; page++) {
            String uri = buildReceivableSearchUri(page, dataVencimentoDe, dataVencimentoAte);

            String payload = executeJsonGetWithRefresh(uri);
            List<SaleItem> pageItems = parseAcquittedSales(payload);

            Optional<SaleItem> match = pageItems.stream()
                    .filter(item -> normalizedSaleId.equals(item.saleId()))
                    .findFirst();

            if (match.isPresent()) {
                return match;
            }

            if (pageItems.isEmpty() || pageItems.size() < PAGE_SIZE) {
                break;
            }
        }

        return Optional.empty();
    }

    /**
     * Busca o e-mail do cliente pelo ID na Conta Azul usando /v1/pessoas/{id}.
     */
    public Optional<String> findCustomerEmailById(String customerId) {
        if (!StringUtils.hasText(customerId)) {
            return Optional.empty();
        }

        String uri = customerByIdV1UrlTemplate
                .replace("{id}", customerId.trim())
                .replace("{customerId}", customerId.trim());

        log.debug("Solicitando detalhes do médico em: /v1/pessoas/{}", customerId.trim());

        try {
            String payload = executeJsonGetWithRefresh(uri);
            return parseCustomerEmailById(payload);
        } catch (RuntimeException ex) {
            log.warn("Falha ao consultar e-mail do cliente Conta Azul (id={}): {}", customerId, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Retorna a lista completa de pessoas (clientes/médicos) consultando a API de clientes
     * e aplicando paginação até que não haja mais itens.
     */
    public List<PessoaItem> fetchAllPessoas() {
        List<PessoaItem> pessoas = new ArrayList<>();
        Long totalExpected = null;

        for (int page = 1; page <= MAX_PAGES; page++) {
            String uri = UriComponentsBuilder.fromUriString(customersV1Url)
                    .queryParam("pagina", page)
                    .queryParam("tamanho_pagina", PAGE_SIZE)
                    .build()
                    .toUriString();

            String payload = executeJsonGetWithRefresh(uri);
            PessoasPage pageResult = parsePessoasPage(payload);

            if (pageResult.total() != null && pageResult.total() > 0) {
                totalExpected = pageResult.total();
            }

            if (pageResult.itens().isEmpty()) {
                break;
            }

            pessoas.addAll(pageResult.itens());

            if (totalExpected != null && pessoas.size() >= totalExpected) {
                break;
            }

            if (pageResult.itens().size() < PAGE_SIZE) {
                break;
            }
        }

        return pessoas;
    }

    /**
     * Recupera vendas do endpoint "committed" aplicando fallback para identificar
     * parcelas já liquidadas (útil quando o endpoint principal de parcelas não
     * fornece todas as informações necessárias).
     */
    public List<SaleItem> fetchCommittedSalesWithAcquittedParcels() {
        List<SaleItem> sales = new ArrayList<>();

        for (int page = 1; page <= MAX_PAGES; page++) {
            String payload = fetchCommittedSalesPagePayload(page);
            log.debug("JSON de Vendas (Fallback): {}", payload);
            List<SaleItem> pageItems = parseCommittedSalesWithAcquittedParcels(payload);

            if (pageItems.isEmpty()) {
                break;
            }

            sales.addAll(pageItems);

            if (pageItems.size() < PAGE_SIZE) {
                break;
            }
        }

        return sales;
    }

    /**
     * Recupera detalhes da parcela identificado por `uuidParcela`, buscando campos
     * que possam conter referência à venda e à baixa associada.
     * Retorna `ParcelaDetailDTO` com `vendaId` e `baixaId` quando disponíveis.
     */
    public Optional<ParcelaDetailDTO> fetchParcelaDetail(String uuidParcela) {
        if (!StringUtils.hasText(uuidParcela)) {
            return Optional.empty();
        }

        String normalizedParcelaUuid = uuidParcela.trim();
        // Detalhe da parcela utiliza endpoint específico (não o /buscar de lista resumida)
        String uri = "https://api-v2.contaazul.com/v1/financeiro/contas-a-receber/" + normalizedParcelaUuid;

        try {
            String payload = executeJsonGetWithRefresh(uri);
            log.info("DEBUG BAIXA JSON: " + payload);
            if (!StringUtils.hasText(payload)) {
                return Optional.empty();
            }

            log.info("!!! [PARCELA DEBUG] JSON recebido: {}", payload);

            JsonNode root = objectMapper.readTree(payload.getBytes(StandardCharsets.UTF_8));
                // Heurística: o campo de origem pode estar em caminhos diferentes
                // dependendo da versão do payload; tentamos os caminhos mais
                // comuns primeiro para identificar se a parcela pertence a uma VENDA.
                String origem = readText(
                    root,
                    "evento.referencia.origem",
                    "evento_financeiro.referencia.origem",
                    "referencia.origem");

            String saleId = null;
            if ("VENDA".equalsIgnoreCase(origem)) {
                saleId = readText(
                        root,
                        "evento.referencia.id",
                        "evento_financeiro.referencia.id",
                        "referencia.id");
            }

            // Caso a origem indique uma venda, tentamos extrair o id da venda
            // a partir de vários caminhos possíveis, priorizando campos
            // explicitamente vinculados à referência do evento.
            if (!StringUtils.hasText(saleId)) {
                saleId = readText(
                        root,
                        "venda_id",
                        "sale_id",
                        "venda.id",
                        "sale.id",
                        "origem.venda_id",
                        "origem.sale_id",
                        "origem.venda.id");
            }

            String baixaId = readText(
                    root,
                    "evento.baixa.id",
                    "evento.baixa_id",
                    "evento.id_baixa",
                    "baixa.id",
                    "baixa_id",
                    "id_baixa",
                    "baixas.0.id",
                    "evento.baixas.0.id",
                    "itens.0.id_baixa",
                    "itens.0.baixa.id",
                    "itens.0.baixado_em");

                // Normaliza IDs retornando null quando vazios
                String normalizedSaleId = Optional.ofNullable(saleId)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .orElse(null);

                String normalizedBaixaId = Optional.ofNullable(baixaId)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .orElse(null);

            if (!StringUtils.hasText(normalizedSaleId) && !StringUtils.hasText(normalizedBaixaId)) {
                return Optional.empty();
            }

            return Optional.of(new ParcelaDetailDTO(normalizedSaleId, normalizedBaixaId));
        } catch (ContaAzulHttpException ex) {
            if (!ex.isStatus(404)) {
                throw ex;
            }
            log.warn("Detalhe da parcela {} não encontrado no Conta Azul.", normalizedParcelaUuid);
            return Optional.empty();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de detalhe da parcela da Conta Azul.", ex);
        }
    }

    /**
     * Recupera o detalhe de uma "baixa" (evento financeiro) no Conta Azul.
     *
     * <p>O método faz chamada HTTP ao endpoint de baixa, parseia o payload JSON
     * e extrai anexos e identificador de recibo digital quando disponíveis.</p>
     *
     * @param baixaId Identificador da baixa no Conta Azul. Se nulo ou vazio, retorna {@link Optional#empty()}.
     * @return {@link Optional} contendo {@link BaixaDetailDTO} quando encontrado, caso contrário vazio.
     * @throws IllegalStateException em caso de falha ao parsear o payload retornado.
     * @throws ContaAzulHttpException em caso de erro HTTP diferente de 404.
     */
    public Optional<BaixaDetailDTO> fetchBaixaDetail(String baixaId) {
        if (!StringUtils.hasText(baixaId)) {
            return Optional.empty();
        }

        String normalizedBaixaId = baixaId.trim();
        String uri = normalizeBaixaBaseUrl().replace("{id}", normalizedBaixaId);

        try {
            String payload = executeJsonGetWithRefresh(uri);
            if (!StringUtils.hasText(payload)) {
                return Optional.empty();
            }

            log.debug("JSON bruto retornado pela Conta Azul (baixa {}): {}", normalizedBaixaId, payload);

            JsonNode root = objectMapper.readTree(payload.getBytes(StandardCharsets.UTF_8));
            String idReciboDigitalGlobal = readText(root, "id_recibo_digital", "evento.id_recibo_digital", "idReciboDigital", "recibo.id_recibo_digital", "recibo.id");
            JsonNode anexosNode = readArrayNode(root, "anexos", "evento.anexos", "data.anexos", "content.anexos");

            if (anexosNode == null || !anexosNode.isArray() || anexosNode.isEmpty()) {
                return Optional.of(new BaixaDetailDTO(List.of(), idReciboDigitalGlobal));
            }

            List<BaixaAttachmentDTO> anexos = new ArrayList<>();
            for (JsonNode anexoNode : anexosNode) {
                String id = readText(anexoNode, "id", "anexo_id");
                String tipo = readText(anexoNode, "tipo", "type", "categoria");
                String url = readText(anexoNode, "url", "link", "download_url", "arquivo.url");
                anexos.add(new BaixaAttachmentDTO(id, tipo, url));
            }

            return Optional.of(new BaixaDetailDTO(anexos, idReciboDigitalGlobal));
        } catch (ContaAzulHttpException ex) {
            if (!ex.isStatus(404)) {
                throw ex;
            }
            log.warn("Detalhe da baixa {} não encontrado no Conta Azul.", normalizedBaixaId);
            return Optional.empty();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de detalhe da baixa da Conta Azul.", ex);
        }
    }

    /**
     * Baixa o conteúdo binário disponível em uma URL externa via HTTP GET.
     *
     * <p>Usado para obter anexos/recibos que estão acessíveis por URL retornada
     * pela API do Conta Azul. Retorna um array vazio quando a URL é nula ou vazia.</p>
     *
     * @param url URL absoluta para download do arquivo.
     * @return conteúdo do arquivo como array de bytes; array vazio quando a URL não for informada.
     * @throws IllegalStateException em caso de falha de IO ou interrupção durante o download.
     * @throws ContaAzulHttpException quando o servidor retornar código de status não-2xx.
     */
    public byte[] downloadFile(String url) {
        if (!StringUtils.hasText(url)) {
            return new byte[0];
        }

        String sanitizedUri = sanitizeContaAzulUri(url.trim());
        URI externalUri = URI.create(sanitizedUri);
        log.debug("MANDANDO PARA O MUNDO EXTERNO: {}", externalUri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(externalUri)
                .GET()
                .build();

        try {
            log.info("CUIDADO: Enviando para: " + externalUri.toString());
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ContaAzulHttpException(response.statusCode(), toUtf8String(response.body()));
            }
            return response.body() != null ? response.body() : new byte[0];
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Falha ao baixar arquivo da Conta Azul.", ex);
        }
    }

    /**
     * Baixa o conteúdo binário de uma URL protegida, fornecendo um token Bearer.
     *
     * <p>Semelhante a {@link #downloadFile(String)}, porém adiciona o cabeçalho
     * Authorization quando {@code bearerToken} for informado.</p>
     *
     * @param url URL absoluta para download do arquivo.
     * @param bearerToken token Bearer a ser usado no cabeçalho Authorization; pode ser nulo.
     * @return conteúdo do arquivo como array de bytes; array vazio quando a URL não for informada.
     * @throws IllegalStateException em caso de falha de IO ou interrupção durante o download.
     * @throws ContaAzulHttpException quando o servidor retornar código de status não-2xx.
     */
    public byte[] downloadFile(String url, String bearerToken) {
        if (!StringUtils.hasText(url)) {
            return new byte[0];
        }

        String sanitizedUri = sanitizeContaAzulUri(url.trim());
        URI externalUri = URI.create(sanitizedUri);
        log.debug("MANDANDO PARA O MUNDO EXTERNO: {}", externalUri);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(externalUri)
                .GET();

        if (StringUtils.hasText(bearerToken)) {
            builder.header("Authorization", "Bearer " + bearerToken.trim());
        }

        HttpRequest request = builder.build();

        try {
            log.info("CUIDADO: Enviando para: " + externalUri.toString());
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ContaAzulHttpException(response.statusCode(), toUtf8String(response.body()));
            }
            return response.body() != null ? response.body() : new byte[0];
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Falha ao baixar arquivo da Conta Azul.", ex);
        }
    }

    /**
     * Baixa um arquivo público disponível em uma URL sem autenticação.
     *
     * <p>Comportamento idêntico a {@link #downloadFile(String)}, mas sem adicionar
     * logs ou cabeçalhos de autenticação específicos — adequado para recursos públicos.</p>
     *
     * @param url URL absoluta do arquivo público.
     * @return conteúdo do arquivo como array de bytes; array vazio quando a URL não for informada.
     * @throws IllegalStateException em caso de falha de IO ou interrupção durante o download.
     * @throws ContaAzulHttpException quando o servidor retornar código de status não-2xx.
     */
    public byte[] downloadPublicFile(String url) {
        if (!StringUtils.hasText(url)) {
            return new byte[0];
        }

        String sanitizedUri = sanitizeContaAzulUri(url.trim());
        URI externalUri = URI.create(sanitizedUri);
        log.debug("MANDANDO PARA O MUNDO EXTERNO: {}", externalUri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(externalUri)
                .GET()
                .build();

        try {
            log.info("CUIDADO: Enviando para: " + externalUri.toString());
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ContaAzulHttpException(response.statusCode(), toUtf8String(response.body()));
            }
            return response.body() != null ? response.body() : new byte[0];
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Falha ao baixar arquivo público da Conta Azul.", ex);
        }
    }

    // Lógica Sniper removida: busca por número de venda não é mais utilizada para recibo

    /**
     * Busca a página de vendas comprometidas (vendas alteradas) no período de
     * ontem até hoje e retorna o payload JSON bruto.
     *
     * <p>Usado internamente para paginação ao sincronizar vendas modificadas.</p>
     *
     * @param page número da página a ser recuperada (base 1).
     * @return payload JSON bruto retornado pelo endpoint de busca de vendas.
     */
    private String fetchCommittedSalesPagePayload(int page) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        String dataAlteracaoDe = LocalDateTime.of(yesterday, java.time.LocalTime.MIDNIGHT).format(DATE_TIME_FORMATTER);
        String dataAlteracaoAte = LocalDateTime.of(today, java.time.LocalTime.of(23, 59, 59)).format(DATE_TIME_FORMATTER);

        log.debug("Buscando vendas alteradas no período completo: {} até {}", dataAlteracaoDe, dataAlteracaoAte);

        String primaryUri = UriComponentsBuilder.fromUriString(normalizeSaleSearchBaseUrl(salesV2Url))
                .queryParam("pagina", page)
                .queryParam("tamanho_pagina", PAGE_SIZE)
                .queryParam("data_alteracao_de", dataAlteracaoDe)
                .queryParam("data_alteracao_ate", dataAlteracaoAte)
                .build()
                .toUriString();

        try {
            return executeJsonGetWithRefresh(primaryUri);
        } catch (ContaAzulHttpException ex) {
            if (!ex.isStatus(404)) {
                throw ex;
            }
            String stableUri = UriComponentsBuilder.fromUriString(salesV2StableUrl)
                    .queryParam("pagina", page)
                    .queryParam("tamanho_pagina", PAGE_SIZE)
                    .queryParam("data_alteracao_de", dataAlteracaoDe)
                    .queryParam("data_alteracao_ate", dataAlteracaoAte)
                    .build()
                    .toUriString();

            log.warn("Endpoint de vendas não encontrado em {}. Tentando fallback estável em {}.", primaryUri, stableUri);
            return executeJsonGetWithRefresh(stableUri);
        }
    }

    /**
     * Executa uma requisição HTTP GET que espera receber JSON e gerencia
     * automaticamente o refresh de token quando necessário.
     *
     * @param uri URL a ser consultada
     * @return corpo da resposta como {@link String}
     */
    private String executeJsonGetWithRefresh(String uri) {
        return executeJsonGetResponseWithRefresh(uri).body();
    }

    /**
     * Variante que retorna o {@link HttpResponse} completo e realiza refresh
     * do token quando a API remota sinaliza 401 Unauthorized.
     *
     * @param uri URL a ser consultada
     * @return {@link HttpResponse} contendo o corpo da resposta
     */
    private HttpResponse<String> executeJsonGetResponseWithRefresh(String uri) {
        ContaAzulOAuthToken token = contaAzulTokenService.getValidTokenFromDatabase();

        try {
            return executeJsonGetResponse(uri, token);
        } catch (ContaAzulHttpException ex) {
            if (!ex.isStatus(401)) {
                throw ex;
            }
            log.warn("Token expirado ao consultar vendas. Tentando refresh.");
            token = contaAzulTokenService.forceRefreshAndReloadFromDatabase();
            return executeJsonGetResponse(uri, token);
        }
    }

    /**
     * Executa o HTTP GET assinando a requisição com o access token fornecido e
     * valida o status da resposta convertendo erros em {@link ContaAzulHttpException}.
     *
     * @param uri URL a ser consultada
     * @param token token que será usado no cabeçalho Authorization
     * @return {@link HttpResponse} contendo o corpo da resposta
     */
    private HttpResponse<String> executeJsonGetResponse(String uri, ContaAzulOAuthToken token) {
        String url = sanitizeContaAzulUri(uri);
        String authorizationHeader = "Bearer " + token.getAccessToken();
        String sanitizedAuthorizationHeader = sanitizeAuthorizationHeader(authorizationHeader);

        log.info("ContaAzul external request URI (JSON): {}", url);
        log.debug(
                "Enviando requisição para {} com Token iniciado em {}...",
                url,
                sanitizeTokenPrefix(token.getAccessToken()));
        log.trace("Header Authorization sanitizado enviado: {}", sanitizedAuthorizationHeader);
        URI externalUri = URI.create(url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(externalUri)
                .header("Authorization", authorizationHeader)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            log.debug("URL ABSOLUTA SENDO ENVIADA: " + url);
            log.debug("ENVIANDO REQUISIÇÃO PARA HOST EXTERNO: " + externalUri.getHost());
            log.info("CUIDADO: Enviando para: " + url);
            log.debug("MANDANDO PARA O MUNDO EXTERNO: " + externalUri.toString());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error(
                        "Conta Azul retornou {} ao consultar URI {}. Corpo do erro: {}",
                        response.statusCode(),
                        url,
                        response.body());
                throw new ContaAzulHttpException(response.statusCode(), response.body());
            }
            return response;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Falha ao consultar endpoint JSON da Conta Azul.", ex);
        }
    }

    /**
     * Converte um array de bytes para {@link String} usando UTF-8.
     * Retorna string vazia quando o corpo for nulo ou vazio.
     *
     * @param body array de bytes a ser convertido
     * @return string UTF-8 correspondente ou string vazia
     */
    private String toUtf8String(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * Faz o parse do payload JSON retornado pelo endpoint de contas a receber
     * e retorna apenas as parcelas que estejam com status de pagamento
     * (liquidadas/recebidas).
     *
     * @param jsonPayload payload JSON bruto
     * @return lista de {@link SaleItem} representando vendas/parcelas liquidadas
     */
    private List<SaleItem> parseAcquittedSales(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            JsonNode entries = resolveArrayNodeFromDtoOrFallback(root);
            if (entries == null || !entries.isArray() || entries.isEmpty()) {
                return List.of();
            }

            List<SaleItem> sales = new ArrayList<>();
            for (JsonNode node : entries) {
                String parcelaId = readText(
                        node,
                        "id",
                        "parcela_id",
                        "parcela.id",
                        "titulo.id");

                String origem = readText(
                    node,
                    "evento_financeiro.referencia.origem",
                    "referencia.origem",
                    "origem",
                    "source");

                // A venda pode aparecer aninhada sob diferentes chaves;
                // primeiro tentamos extrair um objeto `venda` com id, depois
                // procuramos referências diretas em campos comuns.
                String vendaNestedId = readText(
                    node,
                    "venda.id",
                    "sale.id");

                VendaRef venda = StringUtils.hasText(vendaNestedId)
                    ? new VendaRef(vendaNestedId)
                    : null;

                String origemSaleId = readText(
                    node,
                    "evento_financeiro.referencia.id",
                    "referencia.id",
                        "origem.venda_id",
                        "origem.sale_id",
                        "origem.venda.id",
                        "origem.id");

                String vendaId = readText(
                    node,
                    "evento_financeiro.referencia.id",
                    "referencia.id",
                        "venda_id",
                        "sale_id",
                        "sale.id",
                        "venda.id");

                String descricao = readText(
                    node,
                    "descricao",
                    "description",
                    "historico",
                    "observacao");

                String baixaId = readText(
                    node,
                    "baixas.0.id",
                    "evento_financeiro.baixas.0.id",
                    "evento.baixas.0.id",
                    "id_baixa",
                    "baixa_id");

                String idReciboDigital = readText(
                    node,
                    "baixas.0.id_recibo_digital",
                    "evento_financeiro.baixas.0.id_recibo_digital",
                    "evento.baixas.0.id_recibo_digital",
                    "id_recibo_digital");

                String saleId = venda != null && StringUtils.hasText(venda.id())
                    ? venda.id()
                    : (StringUtils.hasText(origemSaleId)
                        ? origemSaleId
                        : (StringUtils.hasText(vendaId) ? vendaId : null));

                String status = readText(node, "status", "sale.status", "situacao", "estado");

                if (!isReceivableItemPaid(status)) {
                    continue;
                }

                String customerUuid = readText(
                        node,
                        "customer.id",
                        "customer.uuid",
                        "customerId",
                        "customer_uuid",
                        "cliente.id",
                        "cliente.uuid",
                        "contato.id",
                        "pessoa.id",
                        "origem.cliente.id");
                    String customerName = readText(
                        node,
                        "customer.name",
                        "customer_name",
                        "cliente.nome",
                        "nome",
                        "contato.nome",
                        "pessoa.nome");

                        sales.add(new SaleItem(saleId, customerUuid, customerName, parcelaId, origem, venda, origemSaleId, vendaId, descricao, null, true, baixaId, idReciboDigital));
            }

            return sales;
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de vendas da Conta Azul.", ex);
        }
    }

    /**
     * Tenta localizar um nó de array dentro do JSON retornado pela Conta Azul.
     * Procura por chaves comuns utilizadas pela API (`data`, `items`, `itens`, `content`) e
     * também aceita o nó raiz quando já for um array.
     *
     * @param root nó JSON raiz retornado pelo serviço remoto
     * @return {@link JsonNode} representando o array de itens ou um array vazio quando nenhum for encontrado
     */
    private JsonNode resolveArrayNode(JsonNode root) {
        if (root == null || root.isNull()) {
            return objectMapper.createArrayNode();
        }

        if (root.isArray()) {
            return root;
        }

        if (root.has("data") && root.get("data").isArray()) {
            return root.get("data");
        }

        // suporte para chave em português 'itens' no nível raiz
        if (root.has("itens") && root.get("itens").isArray()) {
            return root.get("itens");
        }

        if (root.has("items") && root.get("items").isArray()) {
            return root.get("items");
        }

        if (root.has("content") && root.get("content").isArray()) {
            return root.get("content");
        }

        if (root.has("content") && root.get("content").isObject()) {
            JsonNode content = root.get("content");

            if (content.has("items") && content.get("items").isArray()) {
                return content.get("items");
            }

            if (content.has("data") && content.get("data").isArray()) {
                return content.get("data");
            }

            // suporte para chave em português 'itens' dentro de content
            if (content.has("itens") && content.get("itens").isArray()) {
                return content.get("itens");
            }
        }

        return objectMapper.createArrayNode();
    }

    /**
     * Tenta identificar o array de itens a partir do mapeamento para o DTO
     * cognitivo (`ReceivablesSearchResponseDTO`) e, caso o mapeamento falhe,
     * aplica um fallback genérico que procura por chaves conhecidas no JSON.
     *
     * @param root nó JSON raiz recebido da API
     * @return {@link JsonNode} contendo o array de itens encontrado
     */
    private JsonNode resolveArrayNodeFromDtoOrFallback(JsonNode root) {
        try {
            ReceivablesSearchResponseDTO dto = objectMapper.treeToValue(root, ReceivablesSearchResponseDTO.class);
            if (dto != null) {
                if (dto.itens() != null && !dto.itens().isEmpty()) {
                    return resolveArrayNode(root);
                }

                if (dto.content() != null && dto.content().itens() != null && !dto.content().itens().isEmpty()) {
                    return resolveArrayNode(root);
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            log.debug("Falha ao mapear DTO de parcelas (itens). Aplicando fallback por JsonNode.", ex);
        }

        return resolveArrayNode(root);
    }

    /**
     * Verifica se o status informado indica que o item foi pago/liquidado.
     * Suporta variações em inglês e português e considera nulo/vazio como pago
     * por compatibilidade com payloads legados que podem omitir o campo.
     *
     * @param status texto de status retornado pela API
     * @return {@code true} quando o status indica pagamento, {@code false} caso contrário
     */
    private boolean isReceivableItemPaid(String status) {
        if (!StringUtils.hasText(status)) {
            return true;
        }

        String normalizedStatus = status.trim();
        return "ACQUITTED".equalsIgnoreCase(normalizedStatus)
                || "RECEBIDO".equalsIgnoreCase(normalizedStatus)
                || "PAGO".equalsIgnoreCase(normalizedStatus)
                || "PAID".equalsIgnoreCase(normalizedStatus)
                || "LIQUIDADO".equalsIgnoreCase(normalizedStatus);
    }

    /**
     * Constói a URI de busca de contas a receber com paginação e filtros de data.
     * @param page página (base 1)
     * @param dataVencimentoDe data inicial no formato ISO (yyyy-MM-dd)
     * @param dataVencimentoAte data final no formato ISO (yyyy-MM-dd)
     * @return URI completa como {@link String}
     */
    private String buildReceivableSearchUri(int page, String dataVencimentoDe, String dataVencimentoAte) {
        return UriComponentsBuilder.fromUriString(normalizeReceivablesBaseUrl())
                .queryParam("pagina", page)
                .queryParam("tamanho_pagina", PAGE_SIZE)
                .queryParam("status", "RECEBIDO")
                .queryParam("data_vencimento_de", dataVencimentoDe)
                .queryParam("data_vencimento_ate", dataVencimentoAte)
                .build()
                .toUriString();
    }

    /**
     * Retorna a URL base para a busca de contas a receber; usa valor
     * configurado em propriedades ou um fallback padrão.
     * @return URL base como {@link String}
     */
    private String normalizeReceivablesBaseUrl() {
        if (!StringUtils.hasText(receivableEventsSearchUrl)) {
            return "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/contas-a-receber/buscar";
        }

        return receivableEventsSearchUrl.trim();
    }

    /**
     * Retorna a URL base para detalhes de baixa, usando valor configurado ou
     * fallback padrão quando ausente.
     * @return URL base de detalhe de baixa
     */
    private String normalizeBaixaBaseUrl() {
        if (!StringUtils.hasText(baixaDetailsUrl)) {
            return "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/parcelas/baixa/{id}";
        }

        return baixaDetailsUrl.trim();
    }

    /**
     * Normaliza a URL de busca de vendas aplicando correções conhecidas entre
     * endpoints v1/v2 da Conta Azul.
     * @param rawUrl URL configurada
     * @return URL normalizada para uso nas consultas de venda
     */
    private String normalizeSaleSearchBaseUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return "https://api-v2.contaazul.com/v1/venda/busca";
        }

        String normalized = rawUrl.trim();
        if (normalized.contains("/v1/sales")) {
            return normalized.replace("/v1/sales", "/v1/venda/busca");
        }

        return normalized;
    }
    // normalizeSalePrintTemplate removido — impressão via endpoint de venda não é mais utilizada

    /**
     * Retorna um prefixo curto do token para exibição em logs, evitando
     * exposição do token completo em mensagens de debug.
     */
    private String sanitizeTokenPrefix(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return "n/a";
        }

        String normalized = accessToken.trim();
        return normalized.length() <= 4 ? normalized : normalized.substring(0, 4);
    }

    /**
     * Sanitiza o header Authorization para logs, preservando apenas prefixos
     * e suprimindo o conteúdo sensível do token quando necessário.
     * @param authorizationHeader header completo
     * @return representação segura para uso em logs
     */
    private String sanitizeAuthorizationHeader(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return "Authorization: n/a";
        }

        String value = authorizationHeader.trim();
        if (!value.startsWith("Bearer ")) {
            return "Authorization: [formato inválido]";
        }

        String token = value.substring("Bearer ".length());
        if (!StringUtils.hasText(token)) {
            return "Authorization: Bearer [vazio]";
        }

        String normalizedToken = token.trim();
        if (normalizedToken.length() <= 8) {
            return "Authorization: Bearer " + normalizedToken;
        }

        String start = normalizedToken.substring(0, 4);
        String end = normalizedToken.substring(normalizedToken.length() - 4);
        return "Authorization: Bearer " + start + "..." + end;
    }

    /**
     * Normaliza URIs que possam apontar para hosts/paths legados da Conta Azul
     * convertendo-os para o host `api-v2.contaazul.com` e removendo prefixos
     * redundantes como `/api/v1/`.
     */
    private String sanitizeContaAzulUri(String uri) {
        if (!StringUtils.hasText(uri)) {
            return uri;
        }

        String sanitized = uri.trim();
        // Normaliza padrões antigos /api/v1/ para /v1/ no host v2
        sanitized = sanitized.replace("https://api-v2.contaazul.com/api/v1/", "https://api-v2.contaazul.com/v1/");
        sanitized = sanitized.replace("https://api.contaazul.com/api/v1/", "https://api-v2.contaazul.com/v1/");
        // Garante que qualquer referência ao host legado utilize o host api-v2
        sanitized = sanitized.replace("api.contaazul.com", "api-v2.contaazul.com");
        return sanitized;
    }

    private Optional<String> parseCustomerIdByEmail(String jsonPayload, String email) {
        if (!StringUtils.hasText(jsonPayload)) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            String normalizedEmail = email.trim();

            if (root.isObject()) {
                String directEmail = readText(root, "email", "data.email", "emails.0.address", "emails.0.email");
                String directId = readText(root, "id", "uuid", "data.id", "data.uuid");
                if (StringUtils.hasText(directEmail)
                        && StringUtils.hasText(directId)
                        && normalizedEmail.equalsIgnoreCase(directEmail.trim())) {
                    return Optional.of(directId);
                }
            }

            JsonNode entries = resolveArrayNode(root);
            if (entries == null || !entries.isArray()) {
                return Optional.empty();
            }

            if (entries.isEmpty()) {
                log.info("Nenhum cliente encontrado para o e-mail informado.");
                return Optional.empty();
            }

            for (JsonNode node : entries) {
                String nodeEmail = readText(node, "email", "emails.0.address", "emails.0.email");
                if (StringUtils.hasText(nodeEmail) && normalizedEmail.equalsIgnoreCase(nodeEmail.trim())) {
                    String customerId = readText(node, "id", "uuid", "customer.id", "customer.uuid");
                    if (StringUtils.hasText(customerId)) {
                        return Optional.of(customerId);
                    }
                }
            }
        } catch (IOException ex) {
            log.warn("Falha ao parsear retorno de cliente Conta Azul por e-mail {}.", email, ex);
        }

        return Optional.empty();
    }

    /**
     * Extrai e retorna o e-mail do cliente a partir do payload JSON retornado
     * pelo endpoint de pessoa/cliente. Retorna {@link Optional#empty()} quando
     * não for possível recuperar o e-mail.
     *
     * @param jsonPayload payload JSON bruto
     * @return {@link Optional} contendo e-mail quando presente
     */
    private Optional<String> parseCustomerEmailById(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            String email = readText(
                    root,
                    "email",
                    "data.email",
                    "customer.email",
                    "person.email",
                    "emails.0.address",
                    "emails.0.email",
                    "contacts.0.email");

            return StringUtils.hasText(email) ? Optional.of(email) : Optional.empty();
        } catch (IOException ex) {
            log.warn("Falha ao parsear retorno de e-mail do cliente na Conta Azul.", ex);
            return Optional.empty();
        }
    }

    /**
     * Converte o payload de listagem de pessoas em uma página de resultados
     * contendo itens e o total estimado quando presente.
     *
     * @param jsonPayload payload JSON bruto
     * @return {@link PessoasPage} com itens e total
     */
    private PessoasPage parsePessoasPage(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return new PessoasPage(List.of(), null);
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            Long total = readLong(root, "total", "content.total", "paginacao.total", "meta.total");

            JsonNode entries = resolveArrayNode(root);
            if (entries == null || !entries.isArray() || entries.isEmpty()) {
                return new PessoasPage(List.of(), total);
            }

            List<PessoaItem> itens = new ArrayList<>();
            for (JsonNode node : entries) {
                String id = readText(node, "id", "uuid", "pessoa.id");
                String nome = readText(node, "nome", "name", "razao_social", "fantasia");
                String email = readText(node, "email", "emails.0.address", "emails.0.email");

                if (!StringUtils.hasText(id)) {
                    continue;
                }

                itens.add(new PessoaItem(id, nome, email));
            }

            return new PessoasPage(itens, total);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de pessoas da Conta Azul.", ex);
        }
    }

    /**
     * Faz o parse do payload retornado pelo endpoint "committed" e tenta
     * identificar, por heurística, se a venda possui parcelas liquidadas.
     *
     * @param jsonPayload payload JSON bruto do endpoint committed
     * @return lista de {@link SaleItem} extraídos do payload
     */
    private List<SaleItem> parseCommittedSalesWithAcquittedParcels(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            JsonNode entries = resolveArrayNode(root);
            if (entries == null || !entries.isArray() || entries.isEmpty()) {
                return List.of();
            }

            List<SaleItem> sales = new ArrayList<>();
            for (JsonNode saleNode : entries) {
                String saleId = readText(saleNode, "id", "sale_id", "venda.id");
                if (!StringUtils.hasText(saleId)) {
                    continue;
                }

                String customerUuid = readText(
                        saleNode,
                        "customer.id",
                        "customer.uuid",
                        "cliente.id",
                        "cliente.uuid",
                        "person.id",
                        "pessoa.id");

                String customerName = readText(
                        saleNode,
                        "customer.name",
                        "cliente.nome",
                        "person.nome",
                        "person.name");

                String saleNumber = readText(
                    saleNode,
                    "number",
                    "numero",
                    "sale.number",
                    "venda.numero");

                sales.add(new SaleItem(
                        saleId,
                        customerUuid,
                        customerName,
                        null,
                        "VENDA",
                        new VendaRef(saleId),
                        saleId,
                    saleId,
                    null,
                    saleNumber,
                    hasAcquittedInstallmentInSaleNode(saleNode),
                    null,
                    null));
            }

            return sales;
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de vendas COMMITTED da Conta Azul.", ex);
        }
    }

    // hasAcquittedInstallmentInSaleByNumberItem removido — fluxo Sniper não é mais utilizado

    private boolean hasAcquittedInstallmentInSaleNode(JsonNode saleNode) {
        if (saleNode == null || saleNode.isNull()) {
            return false;
        }

        JsonNode installments = saleNode.get("installments");
        if (installments != null && installments.isArray()) {
            for (JsonNode installment : installments) {
                String installmentStatus = readText(installment, "status", "situacao", "estado");
                if ("ACQUITTED".equalsIgnoreCase(installmentStatus)) {
                    return true;
                }
            }
        }

        JsonNode parcelas = saleNode.get("parcelas");
        if (parcelas != null && parcelas.isArray()) {
            for (JsonNode parcela : parcelas) {
                String parcelaStatus = readText(parcela, "status", "situacao", "estado");
                if ("ACQUITTED".equalsIgnoreCase(parcelaStatus)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Lê um valor textual navegando por diferentes caminhos possíveis dentro do
     * nó JSON. Cada caminho suportado pode conter segmentos separados por '.' e
     * índices numéricos para acessar arrays.
     *
     * @param node nó JSON de base
     * @param paths caminhos alternativos a serem consultados
     * @return texto encontrado ou {@code null} quando não houver valor
     */
    private String readText(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode current = node;
            for (String segment : path.split("\\.")) {
                if (current == null) {
                    break;
                }

                if (current.isArray()) {
                    int index;
                    try {
                        index = Integer.parseInt(segment);
                    } catch (NumberFormatException ex) {
                        current = null;
                        break;
                    }

                    current = index >= 0 && index < current.size() ? current.get(index) : null;
                    continue;
                }

                current = current.get(segment);
            }

            if (current != null && !current.isNull()) {
                String value = current.asText();
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }

        return null;
    }

    /**
     * Lê um valor numérico (Long) a partir dos caminhos fornecidos usando
     * a mesma lógica de navegação de {@link #readText(JsonNode, String...)}.
     * Retorna {@code null} quando o valor não existir ou não puder ser convertido.
     *
     * @param node nó JSON de base
     * @param paths caminhos alternativos a serem consultados
     * @return {@link Long} encontrado ou {@code null}
     */
    private Long readLong(JsonNode node, String... paths) {
        String value = readText(node, paths);
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private JsonNode readArrayNode(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode current = node;
            for (String segment : path.split("\\.")) {
                if (current == null) {
                    break;
                }

                current = current.get(segment);
            }

            if (current != null && current.isArray()) {
                return current;
            }
        }

        return null;
    }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record ReceivablesSearchResponseDTO(
            @JsonProperty("itens") List<ReceivableItemDTO> itens,
            @JsonProperty("content") ReceivablesContentDTO content) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record ReceivablesContentDTO(
            @JsonProperty("itens") List<ReceivableItemDTO> itens) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record ReceivableItemDTO(
            @JsonProperty("id") String id,
            @JsonProperty("baixas") List<ReceivableBaixaDTO> baixas) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record ReceivableBaixaDTO(
            @JsonProperty("id") String id,
            @JsonProperty("id_recibo_digital") String idReciboDigital) {
        }

        /** Referência simplificada a uma venda (ID). */
        public record VendaRef(String id) {
        }

        /**
         * DTO leve representando uma parcela/venda relevante para processamento.
         *
         * Campos principais:
         * - `saleId`: identificador da venda (pode vir de vários campos do payload)
         * - `customerUuid`/`customerName`: identificação e nome do cliente associado
         * - `parcelaId`: identificador da parcela/conta a receber
         * - `origem`/`venda`/`origemSaleId`/`vendaId`: diferentes formas de referenciar
         *   a venda dependendo do formato retornado pela Conta Azul
         * - `descricao`/`saleNumber`: campos auxiliares para auditoria/diagnóstico
         * - `hasAcquittedInstallment`: flag heurística indicando presença de parcela
         *   liquidada na estrutura da venda
         * - `baixaId`/`idReciboDigital`: referências à baixa financeira e recibo
         *
         * Este registro é usado internamente pela automação para decidir quais
         * PDFs baixar e quais vendas processar.
         */
        public record SaleItem(
            String saleId,
            String customerUuid,
            String customerName,
            String parcelaId,
            String origem,
            VendaRef venda,
            String origemSaleId,
            String vendaId,
            String descricao,
            String saleNumber,
            boolean hasAcquittedInstallment,
            String baixaId,
            String idReciboDigital) {
    }

            // DTOs SaleByNumber removidos — fluxo Sniper desativado

        private record PessoasPage(List<PessoaItem> itens, Long total) {
        }

        /**
         * Registro representando uma pessoa (cliente/médico) retornada pela
         * Conta Azul. Usado pela sincronização de pessoas/contatos.
         */
        public record PessoaItem(
            String id,
            String nome,
            String email) {
        }

        /**
         * DTO contendo identificadores encontrados para uma parcela (vendaId e baixaId).
         * Retorna `null` nos campos que não puderem ser localizados.
         */
        public record ParcelaDetailDTO(
            String vendaId,
            String baixaId) {
        }

        /**
         * DTO com detalhes da baixa, incluindo lista de anexos e id do recibo
         * digital global quando disponível.
         */
        public record BaixaDetailDTO(
            List<BaixaAttachmentDTO> anexos,
            String idReciboDigital) {
        }

        /**
         * Representa um anexo (attachment) retornado na consulta de baixa.
         * - `tipo` ajuda a identificar recibos (RECIBO_DIGITAL / RECIBO)
         * - `url` é a localização para download do binário
         */
        public record BaixaAttachmentDTO(
            String id,
            String tipo,
            String url) {
        }

        /**
         * Exceção interna representando respostas HTTP não 2xx retornadas
         * pela API da Conta Azul. Carrega o código de status e o corpo
         * da resposta para diagnóstico quando necessário.
         */
        private static final class ContaAzulHttpException extends RuntimeException {
            private final int statusCode;
            private final String responseBody;

            private ContaAzulHttpException(int statusCode, String responseBody) {
                super("Conta Azul retornou erro HTTP " + statusCode);
                this.statusCode = statusCode;
                this.responseBody = responseBody;
            }

            private boolean isStatus(int expectedStatus) {
                return this.statusCode == expectedStatus;
            }

            @SuppressWarnings("unused")
            private String responseBody() {
                return responseBody;
            }
        }
}
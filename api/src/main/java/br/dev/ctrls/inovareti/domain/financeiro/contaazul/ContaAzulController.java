package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/financeiro/contaazul")
@RequiredArgsConstructor
public class ContaAzulController {
    /**
     * Controller administrativo para integração com Conta Azul.
     * Expõe endpoints para iniciar autorização OAuth, verificar status,
     * realizar callback, consultar clientes por e-mail, obter e-mails de cliente,
     * disparar testes de envio real e forçar refresh de token.
     *
     * Observação: endpoints anotados com `@PreAuthorize("hasRole('ADMIN')")`
     * devem ser acessados apenas por usuários administrativos.
     */

    private final ContaAzulTokenService contaAzulTokenService;
    private final ContaAzulClient contaAzulClient;
    private final ContaAzulAutomationService contaAzulAutomationService;

    private static final Logger logger = LoggerFactory.getLogger(ContaAzulController.class);
        // Limite simples em memória (por principal+IP) como fallback quando Redis não estiver disponível.
        private static final java.util.concurrent.ConcurrentMap<String, java.util.concurrent.atomic.AtomicInteger> FORCE_REFRESH_COUNT =
            new java.util.concurrent.ConcurrentHashMap<>();
        private static final java.util.concurrent.ConcurrentMap<String, Long> FORCE_REFRESH_WINDOW_START =
            new java.util.concurrent.ConcurrentHashMap<>();
        private static final long FORCE_REFRESH_WINDOW_MS = 60_000L; // 1 minuto
        private static final int FORCE_REFRESH_MAX = 3; // máximo 3 requisições por janela por usuário+IP

        @Autowired(required = false)
        private ContaAzulMetrics contaAzulMetrics;

        @Autowired(required = false)
        private RedisRateLimiter redisRateLimiter;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${contaazul.redirect-uri}")
    private String contaAzulRedirectUri;

    @GetMapping("/authorize")
    public void startAuthorization(HttpServletResponse response) throws java.io.IOException {
        String authorizationUrl = contaAzulTokenService.buildAuthorizationUrl(contaAzulRedirectUri);
        response.sendRedirect(authorizationUrl);
    }

    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContaAzulTokenService.AuthorizationStatus> getAuthorizationStatus() {
        return ResponseEntity.ok(contaAzulTokenService.getAuthorizationStatus());
    }

    @GetMapping("/callback")
    public void callback(@RequestParam("code") String code, HttpServletResponse response) throws java.io.IOException {
        contaAzulTokenService.exchangeAuthorizationCode(code, contaAzulRedirectUri);
        response.sendRedirect(buildFinanceiroSuccessRedirect());
    }

    @GetMapping("/check-customer/{email}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContaAzulCustomerCheckResponseDTO> checkCustomerByEmail(@PathVariable String email) {
        String customerId = contaAzulClient.findCustomerIdByEmail(email).orElse(null);
        return ResponseEntity.ok(new ContaAzulCustomerCheckResponseDTO(email, customerId));
    }

    @GetMapping("/customer-email/{customerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContaAzulCustomerEmailResponseDTO> getCustomerEmail(@PathVariable String customerId) {
        String email = contaAzulClient.findCustomerEmailById(customerId).orElse(null);
        return ResponseEntity.ok(new ContaAzulCustomerEmailResponseDTO(customerId, email));
    }

    @PostMapping("/teste-envio-real/{saleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TesteEnvioRealResponseDTO> triggerRealSaleTest(@PathVariable String saleId) {
        TesteEnvioRealResult result = contaAzulAutomationService.processRealSaleTest(saleId);
        return ResponseEntity.ok(new TesteEnvioRealResponseDTO(
                result.saleId(),
                result.doctorName(),
                result.recipientEmail(),
                result.pdfBytes()));
    }

    @PostMapping("/force-refresh")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> forceRefresh(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String who = (auth != null) ? auth.getName() : "anonymous";
        String ip = request.getRemoteAddr();
        logger.info("ContaAzul force-refresh requested by {} from {}", who, ip);

        String key = who + ":" + ip;
        long now = System.currentTimeMillis();
        if (redisRateLimiter != null) {
            // chave distribuída no Redis; janela de 1 minuto e máximo de 3 requisições
            var redisKey = "contaazul:force_refresh:" + key;
            boolean acquired = redisRateLimiter.tryAcquire(redisKey, FORCE_REFRESH_MAX, java.time.Duration.ofMillis(FORCE_REFRESH_WINDOW_MS));
            if (!acquired) {
                logger.warn("ContaAzul force-refresh throttled for {} from {}", who, ip);
                if (contaAzulMetrics != null) {
                    contaAzulMetrics.incrementForceRefreshThrottled();
                }
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("erro", "Aguarde antes de requisitar novo refresh"));
            }
        } else {
            // fallback em memória: contador + janela deslizante simples
            Long windowStart = FORCE_REFRESH_WINDOW_START.getOrDefault(key, 0L);
            java.util.concurrent.atomic.AtomicInteger counter = FORCE_REFRESH_COUNT.computeIfAbsent(key, k -> new java.util.concurrent.atomic.AtomicInteger(0));
            if ((now - windowStart) >= FORCE_REFRESH_WINDOW_MS) {
                // iniciar nova janela
                FORCE_REFRESH_WINDOW_START.put(key, now);
                counter.set(0);
            }

            int current = counter.incrementAndGet();
            if (current > FORCE_REFRESH_MAX) {
                logger.warn("ContaAzul force-refresh throttled for {} from {}", who, ip);
                if (contaAzulMetrics != null) {
                    contaAzulMetrics.incrementForceRefreshThrottled();
                }
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("erro", "Aguarde antes de requisitar novo refresh"));
            }
        }

        try {
            var reloaded = contaAzulTokenService.forceRefreshAndReloadFromDatabase();
            // no fallback em memória já mantemos contadores; nada a fazer quando Redis presente
            return ResponseEntity.ok(Map.of(
                    "autorizado", true,
                    "expiraEm", reloaded.getExpiresAt(),
                    "atualizadoEm", reloaded.getRefreshedAt()
            ));
        } catch (IllegalStateException ex) {
            logger.warn("ContaAzul force-refresh bad request by {}: {}", who, ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("ContaAzul force-refresh failed for {}: {}", who, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("erro", ex.getMessage()));
        }
    }

    private String buildFinanceiroSuccessRedirect() {
        String base = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;

        return base + "/financeiro?success=true";
    }

        public record ContaAzulCustomerCheckResponseDTO(
            String email,
            String clienteId) {
    }
        /**
         * Resposta simples para verificação de existência de cliente por e-mail.
         */
        public record ContaAzulCustomerEmailResponseDTO(
            String clienteId,
            String email) {
        }

        /**
         * DTO de resposta do endpoint de teste real contendo identificadores
         * básicos e tamanho do PDF enviado.
         */
        public record TesteEnvioRealResponseDTO(
            String vendaId,
            String nomeMedico,
            String emailDestinatario,
            int tamanhoPdf) {
        }
}

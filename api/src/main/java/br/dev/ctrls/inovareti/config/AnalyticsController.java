package br.dev.ctrls.inovareti.config;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.analytics.dto.DashboardAnalyticsDTO;
import br.dev.ctrls.inovareti.domain.analytics.usecase.GetDashboardAnalyticsUseCase;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controlador REST para endpoints de analítica.
 * Fornece métricas agregadas para as visões de dashboard e relatórios.
 */
@Slf4j
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final GetDashboardAnalyticsUseCase getDashboardAnalyticsUseCase;
    private final UserRepository userRepository;

    /**
     * GET /api/analytics/dashboard
     * Retorna métricas agregadas para a visão de dashboard.
     * Aplica isolamento por perfil de usuário (tenant isolation).
     *
     * @return dados de analítica do dashboard com contagens de chamados e alertas de estoque
     */
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardAnalyticsDTO> getDashboardAnalytics() {
        log.info("GET /api/analytics/dashboard - Fetching dashboard analytics");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId;

        try {
            userId = UUID.fromString(auth.getPrincipal().toString());
        } catch (Exception e) {
            log.warn("Could not parse user ID from authentication");
            return ResponseEntity.badRequest().build();
        }

        var user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        DashboardAnalyticsDTO analytics = getDashboardAnalyticsUseCase.execute(userId, user.getRole());
        return ResponseEntity.ok(analytics);
    }
}

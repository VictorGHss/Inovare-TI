package br.dev.ctrls.inovareti.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.analytics.dto.DashboardAnalyticsDTO;
import br.dev.ctrls.inovareti.domain.analytics.usecase.GetDashboardAnalyticsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller for analytics endpoints.
 * Provides aggregated metrics for dashboard and reporting views.
 */
@Slf4j
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final GetDashboardAnalyticsUseCase getDashboardAnalyticsUseCase;

    /**
     * GET /api/analytics/dashboard
     * Returns aggregated metrics for the dashboard view.
     * 
     * @return Dashboard analytics data including ticket counts and inventory alerts
     */
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardAnalyticsDTO> getDashboardAnalytics() {
        log.info("GET /api/analytics/dashboard - Fetching dashboard analytics");
        DashboardAnalyticsDTO analytics = getDashboardAnalyticsUseCase.execute();
        return ResponseEntity.ok(analytics);
    }
}

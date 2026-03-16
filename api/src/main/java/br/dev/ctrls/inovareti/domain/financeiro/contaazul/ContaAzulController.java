package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/financeiro/contaazul")
@RequiredArgsConstructor
public class ContaAzulController {

    private final ContaAzulTokenService contaAzulTokenService;

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

    private String buildFinanceiroSuccessRedirect() {
        String base = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;

        return base + "/financeiro?success=true";
    }
}

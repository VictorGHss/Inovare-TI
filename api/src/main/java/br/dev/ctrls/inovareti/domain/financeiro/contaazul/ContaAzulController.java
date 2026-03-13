package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/financeiro/contaazul")
@RequiredArgsConstructor
public class ContaAzulController {

    private final ContaAzulTokenService contaAzulTokenService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @GetMapping("/authorize")
    @PreAuthorize("hasRole('ADMIN')")
    public RedirectView startAuthorization(HttpServletRequest request) {
        String redirectUri = contaAzulTokenService.resolveRedirectUri(request);
        String authorizationUrl = contaAzulTokenService.buildAuthorizationUrl(redirectUri);
        return new RedirectView(authorizationUrl);
    }

    @GetMapping("/callback")
    public RedirectView callback(
            @RequestParam("code") String code,
            HttpServletRequest request) {
        String redirectUri = contaAzulTokenService.resolveRedirectUri(request);
        contaAzulTokenService.exchangeAuthorizationCode(code, redirectUri);
        return new RedirectView(buildFinanceiroSuccessRedirect());
    }

    private String buildFinanceiroSuccessRedirect() {
        String base = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;

        return base + "/financeiro?contaazul=success";
    }
}

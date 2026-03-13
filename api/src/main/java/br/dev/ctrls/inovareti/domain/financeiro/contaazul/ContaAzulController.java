package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/financeiro/contaazul")
@RequiredArgsConstructor
public class ContaAzulController {

    private final ContaAzulTokenService contaAzulTokenService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${contaazul.redirect-uri}")
    private String contaAzulRedirectUri;

    @GetMapping("/authorize")
    @PreAuthorize("hasRole('ADMIN')")
    public RedirectView startAuthorization() {
        String authorizationUrl = contaAzulTokenService.buildAuthorizationUrl(contaAzulRedirectUri);
        return new RedirectView(authorizationUrl);
    }

    @GetMapping("/callback")
    public RedirectView callback(@RequestParam("code") String code) {
        contaAzulTokenService.exchangeAuthorizationCode(code, contaAzulRedirectUri);
        return new RedirectView(buildFinanceiroSuccessRedirect());
    }

    private String buildFinanceiroSuccessRedirect() {
        String base = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;

        return base + "/financeiro?contaazul=success";
    }
}

package br.dev.ctrls.inovareti.modules.auth.domain.port.output;

import br.dev.ctrls.inovareti.domain.user.User;

public interface AuthenticatorPort {
    User authenticate(String email, String password);
}

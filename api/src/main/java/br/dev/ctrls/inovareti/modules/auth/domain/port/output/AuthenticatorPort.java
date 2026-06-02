package br.dev.ctrls.inovareti.modules.auth.domain.port.output;

import br.dev.ctrls.inovareti.modules.user.domain.model.User;

public interface AuthenticatorPort {
    User authenticate(String email, String password);
}

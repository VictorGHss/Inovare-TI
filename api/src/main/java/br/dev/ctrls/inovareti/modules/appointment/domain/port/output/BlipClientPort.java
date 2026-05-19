package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;

import java.util.List;
import java.util.Map;

public interface BlipClientPort {

    enum AuthorizationScope { ROUTER, DESK }

    record BlipQueue(String id, String name) {}

    Map<String, Object> getBlipQueues();

    List<BlipQueue> listBlipQueues();

    void mergeContactExtras(String phoneNumber, Map<String, String> extras);

    Map<String, Object> executeCommand(Map<String, Object> payload, AuthorizationScope scope);

    Map<String, Object> executeMessage(Map<String, Object> payload, AuthorizationScope scope);

    String normalizeUserIdentity(String userIdentity);
}

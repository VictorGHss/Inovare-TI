package br.dev.ctrls.inovareti.core.shared.domain.port.output;

public interface AuditPort {

    void record(String module, String action, String details, String traceId);
}

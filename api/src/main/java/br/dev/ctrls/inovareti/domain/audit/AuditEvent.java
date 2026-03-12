package br.dev.ctrls.inovareti.domain.audit;

import java.util.UUID;

import lombok.Getter;

/**
 * Evento de auditoria publicado via ApplicationEventPublisher.
 * Desacopla o núcleo de negócio da persistência do log.
 */
@Getter
public class AuditEvent {

    private final UUID userId;
    private final AuditAction action;
    private final String resourceType;
    private final UUID resourceId;
    private final String details;
    private final String ipAddress;

    private AuditEvent(Builder builder) {
        this.userId       = builder.userId;
        this.action       = builder.action;
        this.resourceType = builder.resourceType;
        this.resourceId   = builder.resourceId;
        this.details      = builder.details;
        this.ipAddress    = builder.ipAddress;
    }

    public static Builder of(AuditAction action) {
        return new Builder(action);
    }

    public static class Builder {
        private final AuditAction action;
        private UUID userId;
        private String resourceType;
        private UUID resourceId;
        private String details;
        private String ipAddress;

        private Builder(AuditAction action) {
            this.action = action;
        }

        public Builder userId(UUID userId)             { this.userId = userId;             return this; }
        public Builder resourceType(String type)       { this.resourceType = type;         return this; }
        public Builder resourceId(UUID id)             { this.resourceId = id;             return this; }
        public Builder details(String details)         { this.details = details;           return this; }
        public Builder ipAddress(String ip)            { this.ipAddress = ip;              return this; }

        public AuditEvent build() {
            return new AuditEvent(this);
        }
    }
}

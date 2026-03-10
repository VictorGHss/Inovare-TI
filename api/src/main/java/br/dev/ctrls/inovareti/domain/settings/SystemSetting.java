package br.dev.ctrls.inovareti.domain.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "system_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemSetting {

    @Id
    @Column(name = "id", nullable = false, length = 100)
    private String id;

    @NotBlank
    @Column(name = "value", nullable = false, length = 255)
    private String value;

    @Column(name = "description", length = 500)
    private String description;
}

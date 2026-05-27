package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "faq_ti")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaqTi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "palavra_chave", nullable = false, length = 80)
    private String palavraChave;

    @Column(name = "pergunta", nullable = false, columnDefinition = "TEXT")
    private String pergunta;

    @Column(name = "resposta", nullable = false, columnDefinition = "TEXT")
    private String resposta;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

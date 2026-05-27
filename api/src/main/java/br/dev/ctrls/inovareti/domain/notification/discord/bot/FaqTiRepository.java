package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FaqTiRepository extends JpaRepository<FaqTi, Integer> {

    @Query("SELECT f FROM FaqTi f WHERE LOWER(f.palavraChave) LIKE LOWER(CONCAT('%', :busca, '%')) OR LOWER(f.pergunta) LIKE LOWER(CONCAT('%', :busca, '%')) OR LOWER(f.resposta) LIKE LOWER(CONCAT('%', :busca, '%'))")
    List<FaqTi> searchFaq(@Param("busca") String busca);
}

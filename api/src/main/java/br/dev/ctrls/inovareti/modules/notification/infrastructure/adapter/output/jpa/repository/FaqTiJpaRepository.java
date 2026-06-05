package br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.jpa.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import br.dev.ctrls.inovareti.modules.notification.domain.model.FaqTi;

@Repository
public interface FaqTiJpaRepository extends JpaRepository<FaqTi, Integer> {

    @Query("SELECT f FROM FaqTi f WHERE LOWER(f.palavraChave) LIKE LOWER(CONCAT('%', :busca, '%')) OR LOWER(f.pergunta) LIKE LOWER(CONCAT('%', :busca, '%')) OR LOWER(f.resposta) LIKE LOWER(CONCAT('%', :busca, '%'))")
    List<FaqTi> searchFaq(@Param("busca") String busca);

    java.util.Optional<FaqTi> findByPalavraChaveIgnoreCase(String palavraChave);
}

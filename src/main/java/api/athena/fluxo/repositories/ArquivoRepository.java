package api.athena.fluxo.repositories;

import api.athena.fluxo.model.entities.Arquivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArquivoRepository extends JpaRepository<Arquivo, Long> {
    List<Arquivo> findByVersaoId(Long versaoId);

    Optional<Arquivo> findByVersaoIdAndNomeOriginalContaining(Long versaoId, String nomeOriginal);
}

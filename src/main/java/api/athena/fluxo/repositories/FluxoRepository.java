package api.athena.fluxo.repositories;

import api.athena.fluxo.model.entities.Fluxo;
import api.athena.fluxo.model.entities.Setor;
import api.athena.fluxo.model.enums.StatusFluxo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FluxoRepository extends JpaRepository<Fluxo, Long> {

    Page<Fluxo> findBySetorCodigo(String setorCodigo, Pageable pageable);

    Page<Fluxo> findBySetorCodigoAndStatus(String setorCodigo, StatusFluxo status, Pageable pageable);

    @Query("SELECT f FROM Fluxo f WHERE " +
            "LOWER(f.titulo) LIKE LOWER(CONCAT('%', :busca, '%')) OR " +
            "LOWER(f.descricao) LIKE LOWER(CONCAT('%', :busca, '%')) OR " +
            "EXISTS (SELECT t FROM f.tags t WHERE LOWER(t) LIKE LOWER(CONCAT('%', :busca, '%')))")
    Page<Fluxo> searchByTituloOrDescricaoOrTags(@Param("busca") String busca, Pageable pageable);

    long countBySetor(Setor setor);
}

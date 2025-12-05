package api.athena.fluxo.repositories;

import api.athena.fluxo.model.entities.Versao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VersaoRepository extends JpaRepository<Versao, Long> {
    List<Versao> findByFluxoIdOrderByNumeroDesc(Long fluxoId);

    Optional<Versao> findByFluxoIdAndNumero(Long fluxoId, Integer numero);
}
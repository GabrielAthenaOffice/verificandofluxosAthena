package api.athena.fluxo.repositories;

import api.athena.fluxo.model.entities.Setor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SetorRepository extends JpaRepository<Setor, Long> {
    Optional<Setor> findByCodigo(String codigo);
    Optional<Setor> findByNome(String nome);
}

package api.athena.fluxo.config;

import api.athena.fluxo.model.entities.Setor;
import api.athena.fluxo.repositories.SetorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final SetorRepository setorRepository;

    @Override
    public void run(String... args) throws Exception {
        inicializarSetores();
    }

    private void inicializarSetores() {
        List<Setor> setoresIniciais = Arrays.asList(
                new Setor(null, "Tecnologia da Informação", "Departamento de TI", "TI", null),
                new Setor(null, "Recursos Humanos", "Departamento de RH", "RH", null),
                new Setor(null, "Financeiro", "Departamento Financeiro", "FIN", null),
                new Setor(null, "Administrativo", "Departamento Administrativo", "ADM", null),
                new Setor(null, "Comercial", "Departamento Comercial", "COM", null),
                new Setor(null, "Operacional", "Departamento Operacional", "OPS", null));

        for (Setor setor : setoresIniciais) {
            if (setorRepository.findByCodigo(setor.getCodigo()).isEmpty()) {
                setorRepository.save(setor);
                log.info("Setor criado: {} ({})", setor.getNome(), setor.getCodigo());
            }
        }
    }
}

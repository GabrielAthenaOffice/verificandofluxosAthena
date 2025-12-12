package api.athena.fluxo.dto;

import api.athena.fluxo.model.enums.StatusFluxo;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class FluxoResponseDTO {
    private Long id;
    private String titulo;
    private String descricao;
    private String codigo;
    private StatusFluxo status;
    private String setorNome;
    private String setorCodigo;
    private String publicadoPorNome;
    private Integer versaoAtual;
    private Long visualizacoes;
    private List<String> tags;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
    private String documentoUrl; // URL assinada do documento principal
}

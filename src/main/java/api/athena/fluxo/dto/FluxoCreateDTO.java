package api.athena.fluxo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class FluxoCreateDTO {

    @NotBlank(message = "Título é obrigatório")
    private String titulo;

    private String descricao;

    @NotBlank(message = "Código do setor é obrigatório")
    private String codigoSetor; // Ex: RH, FIN, TI

    private List<String> tags = new ArrayList<>();
}

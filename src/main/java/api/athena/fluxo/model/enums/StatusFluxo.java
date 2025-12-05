package api.athena.fluxo.model.enums;

import lombok.Getter;

@Getter
public enum StatusFluxo {
    RASCUNHO("Rascunho"),
    PUBLICADO("Publicado"),
    ARQUIVADO("Arquivado"),
    EM_REVISAO("Em Revisão");

    private final String descricao;

    StatusFluxo(String descricao) {
        this.descricao = descricao;
    }

}

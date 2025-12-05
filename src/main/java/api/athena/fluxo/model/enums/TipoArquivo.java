package api.athena.fluxo.model.enums;

import lombok.Getter;

@Getter
public enum TipoArquivo {
    HTML("HTML/Bizagi"),
    PDF("PDF"),
    ZIP("ZIP"),
    IMAGEM("Imagem"),
    CSS("CSS"),
    JAVASCRIPT("JavaScript"),
    OUTRO("Outro");

    private final String descricao;

    TipoArquivo(String descricao) {
        this.descricao = descricao;
    }

}

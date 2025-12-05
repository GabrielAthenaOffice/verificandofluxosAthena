package api.athena.fluxo.model.entities;

import api.athena.fluxo.model.enums.TipoArquivo;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "arquivos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Arquivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "versao_id", nullable = false)
    private Versao versao;

    @Column(nullable = false)
    private String nomeOriginal;

    @Column(nullable = false)
    private String caminhoSupabase; // path no Supabase Storage

    @Enumerated(EnumType.STRING)
    private TipoArquivo tipo;

    @Column(name = "tamanho_bytes")
    private Long tamanhoBytes;

    private String mimeType;

    // Para arquivos HTML processados do Bizagi
    @Column(columnDefinition = "TEXT")
    private String htmlConteudo;

    private LocalDateTime uploadedEm = LocalDateTime.now();
}

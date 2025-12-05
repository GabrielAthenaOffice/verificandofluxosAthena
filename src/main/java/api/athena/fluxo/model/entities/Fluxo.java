package api.athena.fluxo.model.entities;

import api.athena.fluxo.model.enums.StatusFluxo;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fluxos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Fluxo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(nullable = false)
    private String codigo; // codigo unico do processo (ex: RH-001)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusFluxo status = StatusFluxo.RASCUNHO;

    @ManyToOne
    @JoinColumn(name = "setor_id")
    private Setor setor;

    @Column(name = "publicado_por_id")
    private Long publicadoPorId; // ID do usuário do ZapZap

    @Column(name = "publicado_por_nome")
    private String publicadoPorNome;

    @OneToMany(mappedBy = "fluxo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Versao> versoes = new ArrayList<>();

    @Column(name = "versao_atual")
    private Integer versaoAtual = 1;

    private LocalDateTime criadoEm = LocalDateTime.now();
    private LocalDateTime atualizadoEm = LocalDateTime.now();

    @Column(name = "visualizacoes")
    private Long visualizacoes = 0L;

    // Tags para busca
    @ElementCollection
    @CollectionTable(name = "fluxo_tags", joinColumns = @JoinColumn(name = "fluxo_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();
}

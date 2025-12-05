package api.athena.fluxo.model.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "versoes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Versao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fluxo_id", nullable = false)
    private Fluxo fluxo;

    @Column(nullable = false)
    private Integer numero;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @OneToMany(mappedBy = "versao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Arquivo> arquivos = new ArrayList<>();

    private LocalDateTime criadoEm = LocalDateTime.now();
}

package api.athena.fluxo.model.entities;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "setores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Setor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nome;

    private String descricao;

    @Column(nullable = false, unique = true)
    private String codigo; // Ex: RH, FIN, TI

    @OneToMany(mappedBy = "setor")
    private List<Fluxo> fluxos = new ArrayList<>();
}

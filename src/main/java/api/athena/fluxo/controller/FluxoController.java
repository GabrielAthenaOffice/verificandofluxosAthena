package api.athena.fluxo.controller;

import api.athena.fluxo.dto.FluxoCreateDTO;
import api.athena.fluxo.dto.FluxoResponseDTO;
import api.athena.fluxo.model.enums.StatusFluxo;
import api.athena.fluxo.security.UserPrincipal;
import api.athena.fluxo.service.FluxoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/fluxos")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Fluxos", description = "Gestão de fluxos e processos")
public class FluxoController {

    private final FluxoService fluxoService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'LIDER_DE_SETOR', 'FUNCIONARIO')")
    @Operation(summary = "Publicar novo fluxo", description = "Cria um novo fluxo e processa arquivo ZIP ou HTML")
    public ResponseEntity<FluxoResponseDTO> publicarFluxo(
            @RequestPart("fluxo") @Valid FluxoCreateDTO dto,
            @RequestPart(value = "arquivo", required = false) MultipartFile arquivo,
            @AuthenticationPrincipal UserPrincipal usuario) {

        FluxoResponseDTO response = fluxoService.publicarFluxo(dto, arquivo, usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Listar fluxos", description = "Lista fluxos com filtros opcionais por setor, status e busca")
    public ResponseEntity<Page<FluxoResponseDTO>> listarFluxos(
            @RequestParam(required = false) String setor,
            @RequestParam(required = false) StatusFluxo status,
            @RequestParam(required = false) String busca,
            Pageable pageable) {

        Page<FluxoResponseDTO> fluxos = fluxoService.listarFluxos(setor, status, busca, pageable);
        return ResponseEntity.ok(fluxos);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar fluxo por ID")
    public ResponseEntity<FluxoResponseDTO> buscarPorId(@PathVariable Long id) {
        FluxoResponseDTO fluxo = fluxoService.buscarPorId(id);
        return ResponseEntity.ok(fluxo);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIDER_DE_SETOR')")
    @Operation(summary = "Atualizar status do fluxo")
    public ResponseEntity<FluxoResponseDTO> atualizarStatus(
            @PathVariable Long id,
            @RequestParam StatusFluxo status,
            @AuthenticationPrincipal UserPrincipal usuario) {

        FluxoResponseDTO fluxo = fluxoService.atualizarStatus(id, status, usuario);
        return ResponseEntity.ok(fluxo);
    }

    @PostMapping(value = "/{id}/versoes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'LIDER_DE_SETOR')")
    @Operation(summary = "Publicar nova versão")
    public ResponseEntity<FluxoResponseDTO> novaVersao(
            @PathVariable Long id,
            @RequestPart("arquivo") MultipartFile arquivo,
            @RequestParam(required = false) String observacoes,
            @AuthenticationPrincipal UserPrincipal usuario) {

        FluxoResponseDTO fluxo = fluxoService.publicarNovaVersao(id, arquivo, observacoes, usuario);
        return ResponseEntity.ok(fluxo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIDER_DE_SETOR')")
    @Operation(summary = "Deletar fluxo")
    public ResponseEntity<Void> deletarFluxo(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal usuario) {

        fluxoService.deletarFluxo(id, usuario);
        return ResponseEntity.noContent().build();
    }
}

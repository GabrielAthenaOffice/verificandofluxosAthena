package api.athena.fluxo.service;

import api.athena.fluxo.dto.FluxoCreateDTO;
import api.athena.fluxo.dto.FluxoResponseDTO;
import api.athena.fluxo.model.entities.Fluxo;
import api.athena.fluxo.model.entities.Setor;
import api.athena.fluxo.model.entities.Versao;
import api.athena.fluxo.model.entities.Arquivo;
import api.athena.fluxo.model.enums.StatusFluxo;
import api.athena.fluxo.repositories.FluxoRepository;
import api.athena.fluxo.repositories.SetorRepository;
import api.athena.fluxo.repositories.VersaoRepository;
import api.athena.fluxo.repositories.ArquivoRepository;
import api.athena.fluxo.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FluxoService {

    private final FluxoRepository fluxoRepository;
    private final SetorRepository setorRepository;
    private final VersaoRepository versaoRepository;
    private final ArquivoRepository arquivoRepository;
    private final ZipProcessorService zipProcessorService;
    private final ArquivoService arquivoService;

    @Transactional
    public FluxoResponseDTO publicarFluxo(FluxoCreateDTO dto,
            MultipartFile arquivo,
            UserPrincipal usuario) {

        log.info("Publicando fluxo: {} por usuário {}", dto.getTitulo(), usuario.getEmail());

        // Buscar setor
        Setor setor = setorRepository.findByCodigo(dto.getCodigoSetor())
                .orElseThrow(() -> new IllegalArgumentException("Setor não encontrado: " + dto.getCodigoSetor()));

        // Criar fluxo
        Fluxo fluxo = new Fluxo();
        fluxo.setTitulo(dto.getTitulo());
        fluxo.setDescricao(dto.getDescricao());
        fluxo.setCodigo(gerarCodigoUnico(setor));
        fluxo.setSetor(setor);
        fluxo.setPublicadoPorId(usuario.getId());
        fluxo.setPublicadoPorNome(usuario.getEmail());
        fluxo.setStatus(StatusFluxo.RASCUNHO);
        fluxo.setTags(dto.getTags());

        fluxo = fluxoRepository.save(fluxo);

        // Criar primeira versão
        Versao versao = new Versao();
        versao.setFluxo(fluxo);
        versao.setNumero(1);
        versao.setObservacoes("Versão inicial");
        versao = versaoRepository.save(versao);

        // Processar arquivo
        if (arquivo != null) {
            try {
                if (arquivo.getOriginalFilename().endsWith(".zip")) {
                    zipProcessorService.processarZip(arquivo, versao);
                } else {
                    arquivoService.salvarArquivo(arquivo, versao);
                }
            } catch (Exception e) {
                log.error("Erro ao processar arquivo: {}", e.getMessage());
                throw new RuntimeException("Erro ao processar arquivo: " + e.getMessage());
            }
        }

        log.info("Fluxo publicado com sucesso: {}", fluxo.getCodigo());
        return toResponseDTO(fluxo);
    }

    @Transactional(readOnly = true)
    public Page<FluxoResponseDTO> listarFluxos(String setorCodigo,
            StatusFluxo status,
            String busca,
            Pageable pageable) {

        Page<Fluxo> fluxos;

        if (setorCodigo != null && status != null) {
            fluxos = fluxoRepository.findBySetorCodigoAndStatus(setorCodigo, status, pageable);
        } else if (setorCodigo != null) {
            fluxos = fluxoRepository.findBySetorCodigo(setorCodigo, pageable);
        } else if (busca != null && !busca.isEmpty()) {
            fluxos = fluxoRepository.searchByTituloOrDescricaoOrTags(busca, pageable);
        } else {
            fluxos = fluxoRepository.findAll(pageable);
        }

        return fluxos.map(this::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public FluxoResponseDTO buscarPorId(Long id) {
        Fluxo fluxo = fluxoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Fluxo não encontrado"));

        // Incrementar visualizações
        fluxo.setVisualizacoes(fluxo.getVisualizacoes() + 1);
        fluxoRepository.save(fluxo);

        return toResponseDTO(fluxo);
    }

    @Transactional
    public FluxoResponseDTO atualizarStatus(Long id,
            StatusFluxo novoStatus,
            UserPrincipal usuario) {

        Fluxo fluxo = fluxoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Fluxo não encontrado"));

        // Verificar permissão
        if (!usuario.getRole().equals("ADMIN") &&
                !fluxo.getPublicadoPorId().equals(usuario.getId())) {
            throw new AccessDeniedException("Você não tem permissão para alterar este fluxo");
        }

        fluxo.setStatus(novoStatus);
        fluxo.setAtualizadoEm(LocalDateTime.now());

        fluxo = fluxoRepository.save(fluxo);
        log.info("Status do fluxo {} alterado para {}", fluxo.getCodigo(), novoStatus);

        return toResponseDTO(fluxo);
    }

    @Transactional
    public void deletarFluxo(Long id, UserPrincipal usuario) {
        Fluxo fluxo = fluxoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Fluxo não encontrado"));

        // Verificar permissão
        if (!usuario.getRole().equals("ADMIN") &&
                !fluxo.getPublicadoPorId().equals(usuario.getId())) {
            throw new AccessDeniedException("Você não tem permissão para deletar este fluxo");
        }

        fluxoRepository.delete(fluxo);
        log.info("Fluxo {} deletado por {}", fluxo.getCodigo(), usuario.getEmail());
    }

    @Transactional
    public FluxoResponseDTO publicarNovaVersao(Long fluxoId,
            MultipartFile arquivo,
            String observacoes,
            UserPrincipal usuario) {

        Fluxo fluxo = fluxoRepository.findById(fluxoId)
                .orElseThrow(() -> new IllegalArgumentException("Fluxo não encontrado"));

        // Verificar permissão
        if (!usuario.getRole().equals("ADMIN") &&
                !fluxo.getPublicadoPorId().equals(usuario.getId())) {
            throw new AccessDeniedException("Você não tem permissão para criar nova versão");
        }

        Integer novoNumero = fluxo.getVersaoAtual() + 1;

        Versao novaVersao = new Versao();
        novaVersao.setFluxo(fluxo);
        novaVersao.setNumero(novoNumero);
        novaVersao.setObservacoes(observacoes);
        novaVersao = versaoRepository.save(novaVersao);

        // Processar novo arquivo
        if (arquivo != null) {
            try {
                if (arquivo.getOriginalFilename().endsWith(".zip")) {
                    zipProcessorService.processarZip(arquivo, novaVersao);
                } else {
                    arquivoService.salvarArquivo(arquivo, novaVersao);
                }
            } catch (Exception e) {
                throw new RuntimeException("Erro ao processar arquivo: " + e.getMessage());
            }
        }

        fluxo.setVersaoAtual(novoNumero);
        fluxo.setAtualizadoEm(LocalDateTime.now());
        fluxo = fluxoRepository.save(fluxo);

        log.info("Nova versão ({}) publicada para fluxo {}", novoNumero, fluxo.getCodigo());
        return toResponseDTO(fluxo);
    }

    private String gerarCodigoUnico(Setor setor) {
        long count = fluxoRepository.countBySetor(setor);
        return String.format("%s-%03d", setor.getCodigo(), count + 1);
    }

    private FluxoResponseDTO toResponseDTO(Fluxo fluxo) {
        FluxoResponseDTO dto = new FluxoResponseDTO();
        dto.setId(fluxo.getId());
        dto.setTitulo(fluxo.getTitulo());
        dto.setDescricao(fluxo.getDescricao());
        dto.setCodigo(fluxo.getCodigo());
        dto.setStatus(fluxo.getStatus());
        dto.setSetorNome(fluxo.getSetor().getNome());
        dto.setSetorCodigo(fluxo.getSetor().getCodigo());
        dto.setPublicadoPorNome(fluxo.getPublicadoPorNome());
        dto.setVersaoAtual(fluxo.getVersaoAtual());
        dto.setVisualizacoes(fluxo.getVisualizacoes());
        dto.setTags(fluxo.getTags());
        dto.setCriadoEm(fluxo.getCriadoEm());
        dto.setAtualizadoEm(fluxo.getAtualizadoEm());

        // Buscar URL do documento principal
        try {
            dto.setDocumentoUrl(buscarUrlDocumentoPrincipal(fluxo));
        } catch (Exception e) {
            log.warn("Erro ao buscar URL do documento para fluxo {}: {}", fluxo.getId(), e.getMessage());
        }

        return dto;
    }

    /**
     * Busca a URL assinada do documento principal do fluxo.
     * Prioriza o primeiro arquivo da versão atual.
     */
    private String buscarUrlDocumentoPrincipal(Fluxo fluxo) throws IOException {
        // Buscar versão atual
        Optional<Versao> versaoOpt = versaoRepository.findByFluxoIdAndNumero(fluxo.getId(), fluxo.getVersaoAtual());
        if (versaoOpt.isEmpty()) {
            return null;
        }

        // Buscar arquivos da versão
        List<Arquivo> arquivos = arquivoRepository.findByVersaoId(versaoOpt.get().getId());
        if (arquivos.isEmpty()) {
            return null;
        }

        // Priorizar: index.html > primeiro .html > primeiro .pdf > primeiro arquivo
        Arquivo arquivoPrincipal = arquivos.stream()
                .filter(a -> a.getNomeOriginal().equalsIgnoreCase("index.html"))
                .findFirst()
                .orElseGet(() -> arquivos.stream()
                        .filter(a -> a.getNomeOriginal().toLowerCase().endsWith(".html"))
                        .findFirst()
                        .orElseGet(() -> arquivos.stream()
                                .filter(a -> a.getNomeOriginal().toLowerCase().endsWith(".pdf"))
                                .findFirst()
                                .orElse(arquivos.get(0))));

        return arquivoService.getSignedUrl(arquivoPrincipal.getCaminhoSupabase());
    }
}

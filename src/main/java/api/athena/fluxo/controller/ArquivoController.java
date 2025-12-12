package api.athena.fluxo.controller;

import api.athena.fluxo.model.entities.Arquivo;
import api.athena.fluxo.model.entities.Versao;
import api.athena.fluxo.repositories.ArquivoRepository;
import api.athena.fluxo.repositories.VersaoRepository;
import api.athena.fluxo.service.ArquivoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controller para servir arquivos/documentos de fluxos.
 * 
 * Suporta acesso direto aos documentos armazenados no Supabase,
 * funcionando como um repositório de documentos acessível a qualquer momento.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Arquivos", description = "Acesso aos documentos dos fluxos")
public class ArquivoController {

        private final ArquivoService arquivoService;
        private final ArquivoRepository arquivoRepository;
        private final VersaoRepository versaoRepository;

        /**
         * Obtém URL assinada para um arquivo por ID.
         */
        @GetMapping("/arquivos/{id}")
        @Operation(summary = "Obter URL assinada do arquivo", description = "Retorna uma URL assinada temporária para acessar o arquivo")
        public ResponseEntity<?> getArquivoUrl(@PathVariable Long id,
                        @RequestParam(defaultValue = "3600") int expiresIn) {
                try {
                        Arquivo arquivo = arquivoRepository.findById(id)
                                        .orElseThrow(() -> new IllegalArgumentException("Arquivo não encontrado"));

                        String signedUrl = arquivoService.getSignedUrl(arquivo.getCaminhoSupabase(), expiresIn);

                        return ResponseEntity.ok(Map.of(
                                        "id", arquivo.getId(),
                                        "nome", arquivo.getNomeOriginal(),
                                        "tipo", arquivo.getTipo(),
                                        "mimeType", arquivo.getMimeType() != null ? arquivo.getMimeType() : "",
                                        "url", signedUrl,
                                        "expiresIn", expiresIn));
                } catch (IOException e) {
                        log.error("Erro ao gerar URL assinada para arquivo {}: {}", id, e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(Map.of("error", "Erro ao gerar URL de acesso"));
                }
        }

        /**
         * Redireciona para o arquivo no Supabase (download direto).
         */
        @GetMapping("/arquivos/{id}/download")
        @Operation(summary = "Download do arquivo", description = "Redireciona para a URL assinada do arquivo para download")
        public ResponseEntity<Void> downloadArquivo(@PathVariable Long id) {
                try {
                        Arquivo arquivo = arquivoRepository.findById(id)
                                        .orElseThrow(() -> new IllegalArgumentException("Arquivo não encontrado"));

                        String signedUrl = arquivoService.getSignedUrl(arquivo.getCaminhoSupabase());

                        return ResponseEntity.status(HttpStatus.FOUND)
                                        .location(URI.create(signedUrl))
                                        .build();
                } catch (IOException e) {
                        log.error("Erro ao redirecionar para arquivo {}: {}", id, e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
        }

        /**
         * Lista todos os arquivos de uma versão específica.
         */
        @GetMapping("/fluxos/{fluxoId}/versoes/{versaoId}/arquivos")
        @Operation(summary = "Listar arquivos da versão", description = "Retorna todos os arquivos de uma versão com suas URLs assinadas")
        public ResponseEntity<?> listarArquivosDaVersao(
                        @PathVariable Long fluxoId,
                        @PathVariable Long versaoId) {

                Versao versao = versaoRepository.findById(versaoId)
                                .orElseThrow(() -> new IllegalArgumentException("Versão não encontrada"));

                // Verificar se a versão pertence ao fluxo
                if (!versao.getFluxo().getId().equals(fluxoId)) {
                        return ResponseEntity.badRequest()
                                        .body(Map.of("error", "Versão não pertence ao fluxo especificado"));
                }

                List<Arquivo> arquivos = arquivoService.buscarPorVersao(versaoId);

                List<Map<String, Object>> response = arquivos.stream()
                                .map(arquivo -> {
                                        try {
                                                String signedUrl = arquivoService
                                                                .getSignedUrl(arquivo.getCaminhoSupabase());
                                                return Map.<String, Object>of(
                                                                "id", arquivo.getId(),
                                                                "nome", arquivo.getNomeOriginal(),
                                                                "tipo", arquivo.getTipo().name(),
                                                                "mimeType",
                                                                arquivo.getMimeType() != null ? arquivo.getMimeType()
                                                                                : "",
                                                                "tamanho",
                                                                arquivo.getTamanhoBytes() != null
                                                                                ? arquivo.getTamanhoBytes()
                                                                                : 0,
                                                                "url", signedUrl);
                                        } catch (IOException e) {
                                                log.warn("Erro ao gerar URL para arquivo {}: {}", arquivo.getId(),
                                                                e.getMessage());
                                                return Map.<String, Object>of(
                                                                "id", arquivo.getId(),
                                                                "nome", arquivo.getNomeOriginal(),
                                                                "tipo", arquivo.getTipo().name(),
                                                                "error", "Erro ao gerar URL");
                                        }
                                })
                                .collect(Collectors.toList());

                return ResponseEntity.ok(response);
        }

        // ==================== ENDPOINTS DE VISUALIZAÇÃO DIRETA ====================

        /**
         * Visualiza o documento principal do fluxo.
         * Retorna a URL assinada para acesso direto ao documento.
         * 
         * Este é o endpoint principal para acessar um documento publicado.
         */
        @GetMapping("/documentos/{fluxoId}")
        @Operation(summary = "Obter documento do fluxo", description = "Retorna informações e URL assinada do documento principal do fluxo")
        public ResponseEntity<?> getDocumentoFluxo(
                        @PathVariable Long fluxoId,
                        @RequestParam(required = false) Integer versao) {
                try {
                        // Buscar versão
                        Versao versaoObj;
                        if (versao != null) {
                                versaoObj = versaoRepository.findByFluxoIdAndNumero(fluxoId, versao)
                                                .orElseThrow(() -> new IllegalArgumentException(
                                                                "Versão não encontrada"));
                        } else {
                                // Buscar versão mais recente
                                List<Versao> versoes = versaoRepository.findByFluxoIdOrderByNumeroDesc(fluxoId);
                                if (versoes.isEmpty()) {
                                        return ResponseEntity.notFound().build();
                                }
                                versaoObj = versoes.get(0);
                        }

                        // Buscar arquivo principal
                        Arquivo arquivoPrincipal = encontrarArquivoPrincipal(versaoObj.getId());
                        if (arquivoPrincipal == null) {
                                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                                .body(Map.of("error", "Nenhum documento encontrado para este fluxo"));
                        }

                        String signedUrl = arquivoService.getSignedUrl(arquivoPrincipal.getCaminhoSupabase());

                        return ResponseEntity.ok(Map.of(
                                        "id", arquivoPrincipal.getId(),
                                        "nome", arquivoPrincipal.getNomeOriginal(),
                                        "tipo", arquivoPrincipal.getTipo().name(),
                                        "mimeType",
                                        arquivoPrincipal.getMimeType() != null ? arquivoPrincipal.getMimeType() : "",
                                        "tamanho",
                                        arquivoPrincipal.getTamanhoBytes() != null ? arquivoPrincipal.getTamanhoBytes()
                                                        : 0,
                                        "versao", versaoObj.getNumero(),
                                        "url", signedUrl));

                } catch (IllegalArgumentException e) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(Map.of("error", e.getMessage()));
                } catch (IOException e) {
                        log.error("Erro ao obter documento do fluxo {}: {}", fluxoId, e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(Map.of("error", "Erro ao gerar URL de acesso"));
                }
        }

        /**
         * Redireciona diretamente para o documento do fluxo.
         * Ideal para uso em iframes ou links diretos.
         */
        @GetMapping("/documentos/{fluxoId}/visualizar")
        @Operation(summary = "Visualizar documento do fluxo", description = "Redireciona para o documento para visualização direta")
        public ResponseEntity<Void> visualizarDocumento(
                        @PathVariable Long fluxoId,
                        @RequestParam(required = false) Integer versao) {
                try {
                        // Buscar versão
                        Versao versaoObj;
                        if (versao != null) {
                                versaoObj = versaoRepository.findByFluxoIdAndNumero(fluxoId, versao)
                                                .orElseThrow(() -> new IllegalArgumentException(
                                                                "Versão não encontrada"));
                        } else {
                                // Buscar versão mais recente
                                List<Versao> versoes = versaoRepository.findByFluxoIdOrderByNumeroDesc(fluxoId);
                                if (versoes.isEmpty()) {
                                        return ResponseEntity.notFound().build();
                                }
                                versaoObj = versoes.get(0);
                        }

                        // Buscar arquivo principal
                        Arquivo arquivoPrincipal = encontrarArquivoPrincipal(versaoObj.getId());
                        if (arquivoPrincipal == null) {
                                return ResponseEntity.notFound().build();
                        }

                        String signedUrl = arquivoService.getSignedUrl(arquivoPrincipal.getCaminhoSupabase());

                        return ResponseEntity.status(HttpStatus.FOUND)
                                        .location(URI.create(signedUrl))
                                        .build();

                } catch (Exception e) {
                        log.error("Erro ao visualizar documento do fluxo {}: {}", fluxoId, e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
                }
        }

        /**
         * Encontra o arquivo principal de uma versão.
         * Prioriza: index.html > primeiro .html > primeiro .pdf > primeiro arquivo
         */
        private Arquivo encontrarArquivoPrincipal(Long versaoId) {
                List<Arquivo> arquivos = arquivoRepository.findByVersaoId(versaoId);

                if (arquivos.isEmpty()) {
                        return null;
                }

                // Primeiro, procurar por index.html
                Optional<Arquivo> index = arquivos.stream()
                                .filter(a -> a.getNomeOriginal().equalsIgnoreCase("index.html"))
                                .findFirst();

                if (index.isPresent()) {
                        return index.get();
                }

                // Depois, qualquer HTML que não esteja em subdiretório
                Optional<Arquivo> htmlRoot = arquivos.stream()
                                .filter(a -> a.getNomeOriginal().toLowerCase().endsWith(".html"))
                                .filter(a -> !a.getNomeOriginal().contains("/"))
                                .findFirst();

                if (htmlRoot.isPresent()) {
                        return htmlRoot.get();
                }

                // Depois, qualquer PDF
                Optional<Arquivo> pdf = arquivos.stream()
                                .filter(a -> a.getNomeOriginal().toLowerCase().endsWith(".pdf"))
                                .findFirst();

                if (pdf.isPresent()) {
                        return pdf.get();
                }

                // Por último, o primeiro arquivo disponível
                return arquivos.get(0);
        }
}

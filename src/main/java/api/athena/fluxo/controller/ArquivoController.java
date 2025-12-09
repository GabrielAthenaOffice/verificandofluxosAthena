package api.athena.fluxo.controller;

import api.athena.fluxo.model.entities.Arquivo;
import api.athena.fluxo.model.entities.Versao;
import api.athena.fluxo.repositories.ArquivoRepository;
import api.athena.fluxo.repositories.VersaoRepository;
import api.athena.fluxo.service.ArquivoService;
import api.athena.fluxo.service.HtmlRenderService;
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
 * Controller para servir arquivos de fluxos.
 * 
 * Suporta duas formas de acesso:
 * 1. Por ID do arquivo: /api/arquivos/{id}
 * 2. Por caminho relativo (para manter compatibilidade com HTML do Bizagi):
 * /api/fluxos/{fluxoId}/v/{versaoNum}/files/**
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Arquivos", description = "Acesso aos arquivos dos fluxos")
public class ArquivoController {

        private final ArquivoService arquivoService;
        private final ArquivoRepository arquivoRepository;
        private final VersaoRepository versaoRepository;
        private final HtmlRenderService htmlRenderService;

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
                                        "mimeType", arquivo.getMimeType(),
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
         * Retorna o conteúdo HTML diretamente (para preview inline).
         * Útil para arquivos HTML do Bizagi que precisam ser renderizados no navegador.
         */
        @GetMapping("/arquivos/{id}/preview")
        @Operation(summary = "Preview do HTML", description = "Retorna o conteúdo HTML para renderização inline")
        public ResponseEntity<String> previewHtml(@PathVariable Long id) {
                Arquivo arquivo = arquivoRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Arquivo não encontrado"));

                if (arquivo.getHtmlConteudo() == null) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body("Arquivo não é um HTML ou conteúdo não disponível");
                }

                return ResponseEntity.ok()
                                .contentType(MediaType.TEXT_HTML)
                                .body(arquivo.getHtmlConteudo());
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

        /**
         * Serve um arquivo pelo caminho relativo (mantém compatibilidade com HTML do
         * Bizagi).
         * 
         * Exemplo: /api/fluxos/1/v/1/files/libs/css/app.css
         * 
         * Este endpoint permite que o HTML do Bizagi funcione corretamente,
         * redirecionando as requisições de arquivos para URLs assinadas do Supabase.
         */
        @GetMapping("/fluxos/{fluxoId}/v/{versaoNum}/files/**")
        @Operation(summary = "Acessar arquivo por caminho", description = "Redireciona para o arquivo no Supabase baseado no caminho relativo")
        public ResponseEntity<Void> getArquivoPorCaminho(
                        @PathVariable Long fluxoId,
                        @PathVariable Integer versaoNum,
                        jakarta.servlet.http.HttpServletRequest request) {

                try {
                        // Extrair o caminho relativo do arquivo
                        String fullPath = request.getRequestURI();
                        String basePath = String.format("/api/fluxos/%d/v/%d/files/", fluxoId, versaoNum);
                        String relativePath = fullPath.substring(fullPath.indexOf(basePath) + basePath.length());

                        log.debug("Buscando arquivo: fluxoId={}, versao={}, path={}", fluxoId, versaoNum, relativePath);

                        // Buscar a versão
                        Versao versao = versaoRepository.findByFluxoIdAndNumero(fluxoId, versaoNum)
                                        .orElseThrow(() -> new IllegalArgumentException("Versão não encontrada"));

                        // Buscar o arquivo pelo nome original (pode ser caminho como
                        // "libs/css/app.css")
                        Arquivo arquivo = arquivoRepository.findByVersaoIdAndNomeOriginalContaining(
                                        versao.getId(), relativePath)
                                        .orElseThrow(() -> new IllegalArgumentException(
                                                        "Arquivo não encontrado: " + relativePath));

                        String signedUrl = arquivoService.getSignedUrl(arquivo.getCaminhoSupabase());

                        return ResponseEntity.status(HttpStatus.FOUND)
                                        .location(URI.create(signedUrl))
                                        .build();

                } catch (Exception e) {
                        log.error("Erro ao servir arquivo: {}", e.getMessage());
                        return ResponseEntity.notFound().build();
                }
        }

        /**
         * Serve arquivos estáticos diretamente pelo fluxo (compatibilidade com HTML do Bizagi).
         * 
         * Este endpoint aceita requisições no formato: /api/fluxos/{fluxoId}/libs/...
         * ou /api/fluxos/{fluxoId}/** (exceto /visualizar) e tenta encontrar o arquivo na versão mais recente.
         * 
         * Exemplo: /api/fluxos/2/libs/js/app.bizagi.min.js
         * 
         * IMPORTANTE: Este endpoint deve vir ANTES do endpoint /visualizar para evitar conflitos.
         */
        @GetMapping(value = {"/fluxos/{fluxoId}/libs/**", "/fluxos/{fluxoId}/key.json.js", "/fluxos/{fluxoId}/configuration.json.js"})
        @Operation(summary = "Acessar arquivo estático do fluxo", description = "Serve arquivos estáticos (JS, CSS, etc.) do fluxo usando a versão mais recente")
        public ResponseEntity<?> getArquivoEstatico(
                        @PathVariable Long fluxoId,
                        jakarta.servlet.http.HttpServletRequest request) {

                try {
                        // Extrair o caminho relativo do arquivo
                        String fullPath = request.getRequestURI();
                        String basePath = String.format("/api/fluxos/%d/", fluxoId);
                        String relativePath = fullPath.substring(fullPath.indexOf(basePath) + basePath.length());

                        log.debug("Buscando arquivo estático: fluxoId={}, path={}", fluxoId, relativePath);

                        // Buscar a versão mais recente do fluxo
                        List<Versao> versoes = versaoRepository.findByFluxoIdOrderByNumeroDesc(fluxoId);
                        if (versoes.isEmpty()) {
                                log.warn("Nenhuma versão encontrada para o fluxo {}", fluxoId);
                                return ResponseEntity.notFound().build();
                        }

                        Versao versao = versoes.get(0);

                        // Buscar o arquivo pelo nome original (tentar match exato primeiro)
                        Optional<Arquivo> arquivoExato = arquivoRepository.findByVersaoId(versao.getId()).stream()
                                        .filter(a -> a.getNomeOriginal().equals(relativePath) ||
                                                        a.getNomeOriginal().endsWith("/" + relativePath))
                                        .findFirst();

                        Arquivo arquivo;
                        if (arquivoExato.isPresent()) {
                                arquivo = arquivoExato.get();
                        } else {
                                // Tentar busca parcial como fallback
                                arquivo = arquivoRepository.findByVersaoIdAndNomeOriginalContaining(
                                                versao.getId(), relativePath)
                                                .orElseThrow(() -> new IllegalArgumentException(
                                                                "Arquivo não encontrado: " + relativePath));
                        }

                        // Determinar MIME type
                        String mimeType = arquivo.getMimeType();
                        if (mimeType == null || mimeType.isEmpty()) {
                                // Detectar MIME type pela extensão
                                String fileName = arquivo.getNomeOriginal().toLowerCase();
                                if (fileName.endsWith(".js")) {
                                        mimeType = "application/javascript";
                                } else if (fileName.endsWith(".css")) {
                                        mimeType = "text/css";
                                } else if (fileName.endsWith(".json")) {
                                        mimeType = "application/json";
                                } else {
                                        mimeType = "application/octet-stream";
                                }
                        }

                        // Para arquivos pequenos (JS, CSS, JSON), servir diretamente para garantir MIME type correto
                        // Para arquivos grandes, redirecionar para o Supabase
                        long tamanho = arquivo.getTamanhoBytes() != null ? arquivo.getTamanhoBytes() : 0;
                        boolean servirDiretamente = tamanho > 0 && tamanho < 5 * 1024 * 1024; // 5MB

                        if (servirDiretamente) {
                                try {
                                        String signedUrl = arquivoService.getSignedUrl(arquivo.getCaminhoSupabase());
                                        byte[] conteudo = arquivoService.downloadFromSupabase(signedUrl);

                                        return ResponseEntity.ok()
                                                        .contentType(MediaType.parseMediaType(mimeType))
                                                        .header("Cache-Control", "public, max-age=3600")
                                                        .body(conteudo);
                                } catch (IOException e) {
                                        log.warn("Erro ao servir arquivo diretamente, redirecionando: {}", e.getMessage());
                                        // Fallback para redirecionamento
                                }
                        }

                        // Redirecionar para a URL assinada
                        String signedUrl = arquivoService.getSignedUrl(arquivo.getCaminhoSupabase());
                        return ResponseEntity.status(HttpStatus.FOUND)
                                        .header("Content-Type", mimeType)
                                        .location(URI.create(signedUrl))
                                        .build();

                } catch (Exception e) {
                        log.error("Erro ao servir arquivo estático: fluxoId={}, path={}, error={}", 
                                        fluxoId, request.getRequestURI(), e.getMessage());
                        return ResponseEntity.notFound().build();
                }
        }

        // ==================== ENDPOINTS DE VISUALIZAÇÃO RENDERIZADA
        // ====================

        /**
         * Visualiza o fluxo com HTML completamente renderizado.
         * Todos os caminhos relativos (CSS, JS, imagens, etc.) são convertidos
         * para URLs assinadas do Supabase.
         * 
         * Este é o endpoint principal para visualizar um fluxo no navegador.
         * 
         * Exemplo: GET /api/fluxos/1/visualizar
         * GET /api/fluxos/1/visualizar?versao=2
         */
        @GetMapping(value = "/fluxos/{fluxoId}/visualizar", produces = MediaType.TEXT_HTML_VALUE)
        @Operation(summary = "Visualizar fluxo", description = "Retorna o HTML do fluxo com todos os recursos (CSS, JS, imagens) corretamente referenciados")
        public ResponseEntity<String> visualizarFluxo(
                        @PathVariable Long fluxoId,
                        @RequestParam(required = false) Integer versao) {
                try {
                        String htmlRenderizado = htmlRenderService.renderizarFluxo(fluxoId, versao);

                        return ResponseEntity.ok()
                                        .contentType(MediaType.TEXT_HTML)
                                        .body(htmlRenderizado);

                } catch (IllegalArgumentException e) {
                        log.warn("Fluxo não encontrado: {}", e.getMessage());
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body("<html><body><h1>Fluxo não encontrado</h1><p>" + e.getMessage()
                                                        + "</p></body></html>");
                } catch (IOException e) {
                        log.error("Erro ao renderizar fluxo {}: {}", fluxoId, e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body("<html><body><h1>Erro ao carregar fluxo</h1><p>" + e.getMessage()
                                                        + "</p></body></html>");
                }
        }

        /**
         * Visualiza um arquivo HTML específico renderizado.
         * Útil para acessar páginas secundárias do fluxo.
         */
        @GetMapping(value = "/arquivos/{id}/visualizar", produces = MediaType.TEXT_HTML_VALUE)
        @Operation(summary = "Visualizar arquivo HTML", description = "Retorna o HTML de um arquivo específico com recursos corretamente referenciados")
        public ResponseEntity<String> visualizarArquivo(@PathVariable Long id) {
                try {
                        String htmlRenderizado = htmlRenderService.renderizarArquivo(id);

                        return ResponseEntity.ok()
                                        .contentType(MediaType.TEXT_HTML)
                                        .body(htmlRenderizado);

                } catch (IllegalArgumentException e) {
                        log.warn("Arquivo não encontrado: {}", e.getMessage());
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body("<html><body><h1>Arquivo não encontrado</h1></body></html>");
                } catch (IOException e) {
                        log.error("Erro ao renderizar arquivo {}: {}", id, e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body("<html><body><h1>Erro ao carregar arquivo</h1></body></html>");
                }
        }
}

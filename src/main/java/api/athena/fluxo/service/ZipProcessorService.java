package api.athena.fluxo.service;

import api.athena.fluxo.model.entities.Arquivo;
import api.athena.fluxo.model.entities.Versao;
import api.athena.fluxo.model.enums.TipoArquivo;
import api.athena.fluxo.repositories.ArquivoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service responsável por processar arquivos ZIP do Bizagi.
 * 
 * Extrai todos os arquivos mantendo a estrutura de pastas original,
 * permitindo que os caminhos relativos do HTML continuem funcionando.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ZipProcessorService {

    private final ArquivoService arquivoService;
    private final ArquivoRepository arquivoRepository;

    /**
     * Processa um arquivo ZIP do Bizagi, extraindo todos os arquivos
     * e fazendo upload para o Supabase mantendo a estrutura de pastas.
     */
    public void processarZip(MultipartFile zipFile, Versao versao) throws IOException {
        log.info("Processando arquivo ZIP: {}", zipFile.getOriginalFilename());

        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(zipFile.getInputStream()))) {

            ZipEntry entry;
            int fileCount = 0;
            int errorCount = 0;

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String fileName = entry.getName();

                // Ignorar arquivos de sistema Mac/Windows
                if (shouldIgnore(fileName)) {
                    log.debug("Ignorando arquivo de sistema: {}", fileName);
                    zis.closeEntry();
                    continue;
                }

                log.debug("Processando entrada: {}", fileName);

                // Ler conteúdo do arquivo
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; // Buffer maior para melhor performance
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                byte[] fileBytes = baos.toByteArray();

                // Processar o arquivo
                try {
                    processarArquivo(fileName, fileBytes, versao);
                    fileCount++;
                } catch (Exception e) {
                    log.error("Erro ao processar arquivo {}: {}", fileName, e.getMessage());
                    errorCount++;
                }

                zis.closeEntry();
            }

            log.info("ZIP processado. {} arquivos extraídos, {} erros.", fileCount, errorCount);
        }
    }

    /**
     * Processa um arquivo individual do ZIP.
     * Mantém o caminho original para preservar a estrutura de pastas.
     */
    private void processarArquivo(String filePath, byte[] content, Versao versao) throws IOException {
        TipoArquivo tipo = detectarTipoArquivo(filePath);
        String mimeType = detectMimeType(filePath);

        // Upload para Supabase mantendo a estrutura de pastas
        String supabasePath = arquivoService.uploadToSupabase(
                content,
                filePath, // Mantém o caminho completo como nome
                mimeType,
                versao.getFluxo().getCodigo());

        // Criar registro no banco
        Arquivo arquivo = new Arquivo();
        arquivo.setVersao(versao);
        arquivo.setNomeOriginal(filePath); // Caminho completo como "libs/css/app.css"
        arquivo.setTipo(tipo);
        arquivo.setCaminhoSupabase(supabasePath);
        arquivo.setTamanhoBytes((long) content.length);
        arquivo.setMimeType(mimeType);

        // Se for HTML, salvar conteúdo também no banco para preview rápido
        if (tipo == TipoArquivo.HTML) {
            arquivo.setHtmlConteudo(new String(content, StandardCharsets.UTF_8));
        }

        arquivoRepository.save(arquivo);
        log.debug("Arquivo processado: {} -> {}", filePath, supabasePath);
    }

    /**
     * Detecta o tipo do arquivo baseado na extensão.
     */
    private TipoArquivo detectarTipoArquivo(String fileName) {
        String lower = fileName.toLowerCase();

        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return TipoArquivo.HTML;
        }
        if (lower.endsWith(".css")) {
            return TipoArquivo.CSS;
        }
        if (lower.endsWith(".js")) {
            return TipoArquivo.JAVASCRIPT;
        }
        if (lower.endsWith(".pdf")) {
            return TipoArquivo.PDF;
        }
        if (isImageFile(lower)) {
            return TipoArquivo.IMAGEM;
        }

        return TipoArquivo.OUTRO;
    }

    /**
     * Detecta o MIME type baseado na extensão do arquivo.
     */
    private String detectMimeType(String fileName) {
        String lower = fileName.toLowerCase();

        // HTML
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html";
        }

        // CSS
        if (lower.endsWith(".css")) {
            return "text/css";
        }

        // JavaScript
        if (lower.endsWith(".js")) {
            return "application/javascript";
        }

        // Imagens
        if (lower.endsWith(".png"))
            return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
            return "image/jpeg";
        if (lower.endsWith(".gif"))
            return "image/gif";
        if (lower.endsWith(".svg"))
            return "image/svg+xml";
        if (lower.endsWith(".bmp"))
            return "image/bmp";
        if (lower.endsWith(".ico"))
            return "image/x-icon";
        if (lower.endsWith(".webp"))
            return "image/webp";

        // Fontes
        if (lower.endsWith(".woff"))
            return "font/woff";
        if (lower.endsWith(".woff2"))
            return "font/woff2";
        if (lower.endsWith(".ttf"))
            return "font/ttf";
        if (lower.endsWith(".eot"))
            return "application/vnd.ms-fontobject";
        if (lower.endsWith(".otf"))
            return "font/otf";

        // Documentos
        if (lower.endsWith(".pdf"))
            return "application/pdf";
        if (lower.endsWith(".json"))
            return "application/json";
        if (lower.endsWith(".xml"))
            return "application/xml";

        // Arquivos de processo
        if (lower.endsWith(".bpm") || lower.endsWith(".bpmn")) {
            return "application/octet-stream";
        }

        return "application/octet-stream";
    }

    /**
     * Verifica se é um arquivo de imagem.
     */
    private boolean isImageFile(String lowerFileName) {
        return lowerFileName.endsWith(".png") ||
                lowerFileName.endsWith(".jpg") ||
                lowerFileName.endsWith(".jpeg") ||
                lowerFileName.endsWith(".gif") ||
                lowerFileName.endsWith(".svg") ||
                lowerFileName.endsWith(".bmp") ||
                lowerFileName.endsWith(".ico") ||
                lowerFileName.endsWith(".webp");
    }

    /**
     * Verifica se o arquivo deve ser ignorado (arquivos de sistema).
     */
    private boolean shouldIgnore(String fileName) {
        // Arquivos de sistema Mac
        if (fileName.startsWith("__MACOSX/") || fileName.contains("/.DS_Store")) {
            return true;
        }
        if (fileName.endsWith(".DS_Store")) {
            return true;
        }

        // Arquivos de sistema Windows
        if (fileName.equals("Thumbs.db") || fileName.endsWith("/Thumbs.db")) {
            return true;
        }
        if (fileName.equals("desktop.ini") || fileName.endsWith("/desktop.ini")) {
            return true;
        }

        return false;
    }
}

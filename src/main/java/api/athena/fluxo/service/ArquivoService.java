package api.athena.fluxo.service;

import api.athena.fluxo.model.entities.Arquivo;
import api.athena.fluxo.model.entities.Versao;
import api.athena.fluxo.model.enums.TipoArquivo;
import api.athena.fluxo.repositories.ArquivoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArquivoService {

    private final ArquivoRepository arquivoRepository;
    private final OkHttpClient httpClient = new OkHttpClient();

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.servicekey}")
    private String supabaseServiceKey;

    @Value("${supabase.bucket.name}")
    private String bucketName;

    /**
     * Salva um arquivo individual (não ZIP) associado a uma versão.
     * Útil para uploads de arquivos HTML ou outros documentos.
     */
    public Arquivo salvarArquivo(MultipartFile file, Versao versao) throws IOException {
        log.info("Salvando arquivo: {} para versão {}", file.getOriginalFilename(), versao.getId());

        String originalFilename = file.getOriginalFilename();
        String mimeType = file.getContentType();
        byte[] content = file.getBytes();

        // Determinar tipo do arquivo
        TipoArquivo tipo = detectarTipoArquivo(originalFilename, mimeType);

        // Upload para Supabase
        String path = uploadToSupabase(
                content,
                originalFilename,
                mimeType,
                versao.getFluxo().getCodigo());

        // Criar entidade Arquivo
        Arquivo arquivo = new Arquivo();
        arquivo.setVersao(versao);
        arquivo.setNomeOriginal(originalFilename);
        arquivo.setTipo(tipo);
        arquivo.setCaminhoSupabase(path);
        arquivo.setTamanhoBytes((long) content.length);
        arquivo.setMimeType(mimeType);

        // Se for HTML, armazenar conteúdo também no banco
        if (tipo == TipoArquivo.HTML) {
            arquivo.setHtmlConteudo(new String(content, StandardCharsets.UTF_8));
        }

        arquivo = arquivoRepository.save(arquivo);
        log.info("Arquivo salvo com sucesso: {} -> {}", originalFilename, path);

        return arquivo;
    }

    /**
     * Faz upload de bytes para o Supabase Storage.
     * Retorna o caminho do arquivo no bucket.
     */
    public String uploadToSupabase(byte[] content, String fileName, String mimeType, String fluxoCodigo)
            throws IOException {
        // Gerar nome único para evitar colisões
        String uniqueFileName = generateUniqueName(fileName);
        String path = String.format("fluxos-arquivos/fluxos/%s/%s", fluxoCodigo, uniqueFileName);

        String uploadUrl = String.format("%s/storage/v1/object/%s/%s",
                supabaseUrl, bucketName, path);

        log.debug("Fazendo upload para: {}", uploadUrl);

        RequestBody body = RequestBody.create(content, MediaType.parse(mimeType));

        Request request = new Request.Builder()
                .url(uploadUrl)
                .header("Authorization", "Bearer " + supabaseServiceKey)
                .header("Content-Type", mimeType)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Sem detalhes";
                log.error("Erro no upload para Supabase. Status: {}, Body: {}", response.code(), errorBody);
                throw new IOException("Falha no upload para Supabase: " + response.code());
            }

            log.info("Upload realizado com sucesso: {}", path);
            return path;
        }
    }

    // Duração padrão para URLs assinadas (1 hora)
    private static final int DEFAULT_SIGNED_URL_EXPIRY = 3600;

    /**
     * Gera URL assinada para acesso ao arquivo (bucket privado).
     * Usa duração padrão de 1 hora.
     */
    public String getSignedUrl(String path) throws IOException {
        return getSignedUrl(path, DEFAULT_SIGNED_URL_EXPIRY);
    }

    /**
     * Gera URL assinada para acesso temporário (arquivos privados).
     * 
     * @param path             Caminho do arquivo no bucket
     * @param expiresInSeconds Tempo de expiração em segundos
     * @return URL completa assinada para acesso ao arquivo
     */
    public String getSignedUrl(String path, int expiresInSeconds) throws IOException {
        String signUrl = String.format("%s/storage/v1/object/sign/%s/%s",
                supabaseUrl, bucketName, path);

        String jsonBody = String.format("{\"expiresIn\": %d}", expiresInSeconds);

        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(signUrl)
                .header("Authorization", "Bearer " + supabaseServiceKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Sem detalhes";
                log.error("Erro ao gerar URL assinada para {}. Status: {}, Body: {}", path, response.code(), errorBody);
                throw new IOException("Falha ao gerar URL assinada: " + response.code());
            }

            String responseBody = response.body().string();

            // Parse usando org.json para maior robustez
            org.json.JSONObject json = new org.json.JSONObject(responseBody);
            String signedPath = json.getString("signedURL");

            log.debug("URL assinada gerada para: {}", path);
            return supabaseUrl + signedPath;
        }
    }

    /**
     * Baixa o conteúdo de um arquivo do Supabase usando a URL assinada.
     * Útil para fazer proxy do conteúdo e servir com MIME type correto.
     */
    public byte[] downloadFromSupabase(String signedUrl) throws IOException {
        Request request = new Request.Builder()
                .url(signedUrl)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Sem detalhes";
                log.error("Erro ao baixar arquivo do Supabase. Status: {}, Body: {}", response.code(), errorBody);
                throw new IOException("Falha ao baixar arquivo do Supabase: " + response.code());
            }

            if (response.body() == null) {
                throw new IOException("Resposta vazia do Supabase");
            }

            return response.body().bytes();
        }
    }

    /**
     * Deleta um arquivo do Supabase Storage.
     */
    public void deletarDoSupabase(String path) throws IOException {
        String deleteUrl = String.format("%s/storage/v1/object/%s/%s",
                supabaseUrl, bucketName, path);

        Request request = new Request.Builder()
                .url(deleteUrl)
                .header("Authorization", "Bearer " + supabaseServiceKey)
                .delete()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 404) {
                log.warn("Falha ao deletar arquivo do Supabase: {} - Status: {}", path, response.code());
            } else {
                log.info("Arquivo deletado do Supabase: {}", path);
            }
        }
    }

    /**
     * Busca todos os arquivos de uma versão.
     */
    public List<Arquivo> buscarPorVersao(Long versaoId) {
        return arquivoRepository.findByVersaoId(versaoId);
    }

    /**
     * Deleta um arquivo do banco e do Supabase.
     */
    public void deletarArquivo(Long arquivoId) throws IOException {
        Arquivo arquivo = arquivoRepository.findById(arquivoId)
                .orElseThrow(() -> new IllegalArgumentException("Arquivo não encontrado"));

        // Deletar do Supabase primeiro
        if (arquivo.getCaminhoSupabase() != null) {
            deletarDoSupabase(arquivo.getCaminhoSupabase());
        }

        // Deletar do banco
        arquivoRepository.delete(arquivo);
        log.info("Arquivo {} deletado completamente", arquivoId);
    }

    // ==================== Métodos auxiliares ====================

    private TipoArquivo detectarTipoArquivo(String fileName, String mimeType) {
        if (fileName == null) {
            return TipoArquivo.OUTRO;
        }

        String lower = fileName.toLowerCase();

        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return TipoArquivo.HTML;
        }
        if (lower.endsWith(".png") || lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg") || lower.endsWith(".gif") ||
                lower.endsWith(".svg") || lower.endsWith(".bmp")) {
            return TipoArquivo.IMAGEM;
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

        return TipoArquivo.OUTRO;
    }

    private String generateUniqueName(String originalName) {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            String name = originalName.substring(0, dotIndex);
            String ext = originalName.substring(dotIndex);
            return name + "_" + uuid + ext;
        }
        return originalName + "_" + uuid;
    }
}

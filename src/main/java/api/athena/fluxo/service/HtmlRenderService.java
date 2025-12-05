package api.athena.fluxo.service;

import api.athena.fluxo.model.entities.Arquivo;
import api.athena.fluxo.model.entities.Versao;
import api.athena.fluxo.repositories.ArquivoRepository;
import api.athena.fluxo.repositories.VersaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service responsável por renderizar HTMLs do Bizagi com caminhos reescritos.
 * 
 * Substitui todos os caminhos relativos (src, href, etc.) por URLs assinadas
 * do Supabase, permitindo que o HTML seja visualizado corretamente no
 * navegador.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HtmlRenderService {

    private final ArquivoRepository arquivoRepository;
    private final VersaoRepository versaoRepository;
    private final ArquivoService arquivoService;

    // Cache de URLs assinadas para evitar múltiplas chamadas ao Supabase
    // Duração de 1 hora para as URLs
    private static final int URL_EXPIRY_SECONDS = 3600;

    /**
     * Renderiza o HTML principal de um fluxo com todos os caminhos reescritos.
     * 
     * @param fluxoId   ID do fluxo
     * @param versaoNum Número da versão (ou null para versão atual)
     * @return HTML renderizado com URLs assinadas
     */
    public String renderizarFluxo(Long fluxoId, Integer versaoNum) throws IOException {
        // Buscar versão
        Versao versao;
        if (versaoNum != null) {
            versao = versaoRepository.findByFluxoIdAndNumero(fluxoId, versaoNum)
                    .orElseThrow(() -> new IllegalArgumentException("Versão não encontrada"));
        } else {
            // Buscar versão mais recente
            List<Versao> versoes = versaoRepository.findByFluxoIdOrderByNumeroDesc(fluxoId);
            if (versoes.isEmpty()) {
                throw new IllegalArgumentException("Nenhuma versão encontrada para o fluxo");
            }
            versao = versoes.get(0);
        }

        // Buscar o arquivo HTML principal (index.html ou similar)
        Arquivo htmlPrincipal = encontrarHtmlPrincipal(versao.getId());
        if (htmlPrincipal == null || htmlPrincipal.getHtmlConteudo() == null) {
            throw new IllegalArgumentException("HTML principal não encontrado");
        }

        // Renderizar com caminhos reescritos
        return renderizarHtml(htmlPrincipal.getHtmlConteudo(), versao.getId());
    }

    /**
     * Renderiza um arquivo HTML específico com caminhos reescritos.
     */
    public String renderizarArquivo(Long arquivoId) throws IOException {
        Arquivo arquivo = arquivoRepository.findById(arquivoId)
                .orElseThrow(() -> new IllegalArgumentException("Arquivo não encontrado"));

        if (arquivo.getHtmlConteudo() == null) {
            throw new IllegalArgumentException("Arquivo não é um HTML ou conteúdo não disponível");
        }

        return renderizarHtml(arquivo.getHtmlConteudo(), arquivo.getVersao().getId());
    }

    /**
     * Processa o HTML e substitui todos os caminhos relativos por URLs assinadas.
     */
    private String renderizarHtml(String htmlContent, Long versaoId) throws IOException {
        // Buscar todos os arquivos da versão para criar mapa de caminhos
        List<Arquivo> arquivos = arquivoRepository.findByVersaoId(versaoId);
        Map<String, String> urlMap = criarMapaUrls(arquivos);

        // Usar Jsoup para processar o HTML
        Document doc = Jsoup.parse(htmlContent);
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.html);

        // Reescrever links CSS
        reescreverAtributos(doc, "link[href]", "href", urlMap);

        // Reescrever scripts
        reescreverAtributos(doc, "script[src]", "src", urlMap);

        // Reescrever imagens
        reescreverAtributos(doc, "img[src]", "src", urlMap);

        // Reescrever fontes e outros recursos em @import e url() do CSS inline
        reescreverCssInline(doc, urlMap);

        // Reescrever links em âncoras (para PDFs e outros arquivos)
        reescreverAtributos(doc, "a[href]", "href", urlMap);

        // Reescrever source (para áudio/vídeo)
        reescreverAtributos(doc, "source[src]", "src", urlMap);

        // Reescrever object (para PDFs embarcados)
        reescreverAtributos(doc, "object[data]", "data", urlMap);

        // Reescrever embed
        reescreverAtributos(doc, "embed[src]", "src", urlMap);

        return doc.html();
    }

    /**
     * Cria um mapa de caminho relativo -> URL assinada.
     */
    private Map<String, String> criarMapaUrls(List<Arquivo> arquivos) throws IOException {
        Map<String, String> urlMap = new HashMap<>();

        for (Arquivo arquivo : arquivos) {
            if (arquivo.getCaminhoSupabase() != null) {
                try {
                    String signedUrl = arquivoService.getSignedUrl(
                            arquivo.getCaminhoSupabase(),
                            URL_EXPIRY_SECONDS);

                    // Mapear pelo nome original (caminho relativo como "libs/css/app.css")
                    urlMap.put(arquivo.getNomeOriginal(), signedUrl);

                    // Também mapear variações do caminho
                    // Ex: "libs/css/app.css" também deve funcionar com "./libs/css/app.css"
                    if (!arquivo.getNomeOriginal().startsWith("./")) {
                        urlMap.put("./" + arquivo.getNomeOriginal(), signedUrl);
                    }

                    log.debug("Mapeado: {} -> {}", arquivo.getNomeOriginal(), signedUrl);
                } catch (Exception e) {
                    log.warn("Erro ao gerar URL para {}: {}", arquivo.getNomeOriginal(), e.getMessage());
                }
            }
        }

        return urlMap;
    }

    /**
     * Reescreve atributos de elementos que contêm caminhos.
     */
    private void reescreverAtributos(Document doc, String selector, String atributo, Map<String, String> urlMap) {
        Elements elements = doc.select(selector);

        for (Element element : elements) {
            String caminhoOriginal = element.attr(atributo);

            if (caminhoOriginal.isEmpty() || isUrlAbsoluta(caminhoOriginal)) {
                continue; // Pular URLs absolutas e vazias
            }

            // Normalizar o caminho
            String caminhoNormalizado = normalizarCaminho(caminhoOriginal);

            // Buscar no mapa
            String urlAssinada = urlMap.get(caminhoNormalizado);
            if (urlAssinada != null) {
                element.attr(atributo, urlAssinada);
                log.debug("Reescrito {}: {} -> {}", atributo, caminhoOriginal, urlAssinada);
            } else {
                log.debug("Caminho não encontrado no mapa: {}", caminhoOriginal);
            }
        }
    }

    /**
     * Reescreve URLs em CSS inline (style tags e atributos style).
     */
    private void reescreverCssInline(Document doc, Map<String, String> urlMap) {
        // Processar tags <style>
        Elements styleTags = doc.select("style");
        for (Element style : styleTags) {
            String cssContent = style.html();
            String cssReescrito = reescreverUrlsNoCss(cssContent, urlMap);
            style.html(cssReescrito);
        }

        // Processar atributos style
        Elements elementsWithStyle = doc.select("[style]");
        for (Element element : elementsWithStyle) {
            String styleContent = element.attr("style");
            String styleReescrito = reescreverUrlsNoCss(styleContent, urlMap);
            element.attr("style", styleReescrito);
        }
    }

    /**
     * Reescreve URLs dentro de conteúdo CSS (url(...) e @import).
     */
    private String reescreverUrlsNoCss(String css, Map<String, String> urlMap) {
        // Padrão para url(...)
        java.util.regex.Pattern urlPattern = java.util.regex.Pattern.compile(
                "url\\(['\"]?([^)\"']+)['\"]?\\)",
                java.util.regex.Pattern.CASE_INSENSITIVE);

        java.util.regex.Matcher matcher = urlPattern.matcher(css);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String caminhoOriginal = matcher.group(1);

            if (!isUrlAbsoluta(caminhoOriginal)) {
                String caminhoNormalizado = normalizarCaminho(caminhoOriginal);
                String urlAssinada = urlMap.get(caminhoNormalizado);

                if (urlAssinada != null) {
                    matcher.appendReplacement(result, "url('" + urlAssinada.replace("$", "\\$") + "')");
                }
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Normaliza um caminho removendo ./ e resolvendo ../
     */
    private String normalizarCaminho(String caminho) {
        String normalizado = caminho;

        // Remover ./ do início
        if (normalizado.startsWith("./")) {
            normalizado = normalizado.substring(2);
        }

        // Remover query strings
        int queryIndex = normalizado.indexOf('?');
        if (queryIndex > 0) {
            normalizado = normalizado.substring(0, queryIndex);
        }

        // Remover fragmentos
        int hashIndex = normalizado.indexOf('#');
        if (hashIndex > 0) {
            normalizado = normalizado.substring(0, hashIndex);
        }

        return normalizado;
    }

    /**
     * Verifica se é uma URL absoluta (http://, https://, //, data:, etc.)
     */
    private boolean isUrlAbsoluta(String url) {
        return url.startsWith("http://") ||
                url.startsWith("https://") ||
                url.startsWith("//") ||
                url.startsWith("data:") ||
                url.startsWith("blob:") ||
                url.startsWith("javascript:") ||
                url.startsWith("#");
    }

    /**
     * Encontra o arquivo HTML principal de uma versão.
     * Prioriza: index.html > *.html no root
     */
    private Arquivo encontrarHtmlPrincipal(Long versaoId) {
        List<Arquivo> arquivos = arquivoRepository.findByVersaoId(versaoId);

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

        // Por último, qualquer HTML
        return arquivos.stream()
                .filter(a -> a.getNomeOriginal().toLowerCase().endsWith(".html"))
                .findFirst()
                .orElse(null);
    }
}

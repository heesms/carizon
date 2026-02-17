package com.carizon.rag.service;

import com.carizon.common.http.HttpClientService;
import com.carizon.rag.config.RagProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * LLM ??뺥돩??(Ollama ?癒?뮉 Hugging Face ????
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {

    private static final Pattern HANJA_PATTERN = Pattern.compile("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]");
    private static final String KOREAN_ONLY_SYSTEM_PROMPT =
            "Always answer in Korean Hangul only. Do not use Hanja or Chinese characters. "
                    + "Keep the wording concise and natural.";
    
    private final RagProperties ragProperties;
    private final HttpClientService httpClientService;
    private final ObjectMapper objectMapper;
    
    /**
     * ?袁⑨세?袁る뱜??LLM???袁⑤뼎??랁??臾먮뼗 獄쏆룄由?     */
    public String generateResponse(String prompt) throws IOException {
        return generateResponse(prompt, null);
    }

    /**
     * ?袁⑨세?袁る뱜??LLM???袁⑤뼎??랁??臾먮뼗 獄쏆룄由?(????筌왖??
     */
    public String generateResponse(String prompt, GenerationOptions options) throws IOException {
        String provider = ragProperties.getLlm().getProvider();

        if ("ollama".equalsIgnoreCase(provider)) {
            return generateOllamaResponse(prompt, options);
        } else if ("huggingface".equalsIgnoreCase(provider)) {
            return generateHuggingFaceResponse(prompt);
        } else {
            throw new IllegalArgumentException("Unknown LLM provider: " + provider);
        }
    }
    
    /**
     * Ollama????????臾먮뼗 ??밴쉐 (嚥≪뮇類???쎈뻬 ?袁⑹뒄)
     */
    private String generateOllamaResponse(String prompt, GenerationOptions options) throws IOException {
        RagProperties.Llm.Ollama config = ragProperties.getLlm().getOllama();
        String baseUrl = config.getBaseUrl();
        String model = config.getModel();
        log.debug("[Ollama] model={}, url={}/api/generate", model, baseUrl);

        String url = baseUrl + "/api/generate";
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("stream", false);
        body.put("system", KOREAN_ONLY_SYSTEM_PROMPT);
        body.put("keep_alive", "10m");

        Map<String, Object> ollamaOptions = new HashMap<>();
        ollamaOptions.put("temperature", options != null && options.temperature() != null ? options.temperature() : 0.2);
        ollamaOptions.put("top_p", 0.9);
        ollamaOptions.put("num_ctx", 2048);
        ollamaOptions.put("num_predict", options != null && options.maxTokens() != null ? options.maxTokens() : 300);
        body.put("options", ollamaOptions);

        try (Response response = httpClientService.postJson(url, body)) {
            if (!response.isSuccessful()) {
                throw new IOException("Ollama API error: " + response.code() + " " + response.message());
            }
            // ??? 繹먥뫁彛?獄쎻뫗?: ?臾먮뼗 獄쏅뗄??紐? UTF-8嚥???곴퐤 (Ollama揶쎛 charset 沃섎챷?????疫꿸퀡??첎誘れ몵嚥?繹먥뫁彛?????됱벉)
            byte[] bytes = response.body().bytes();
            String responseBody = new String(bytes, StandardCharsets.UTF_8);
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            if (jsonNode.has("response")) {
                String raw = jsonNode.get("response").asText();
                return sanitizeKoreanOnly(raw);
            }
            throw new IOException("Unexpected response format from Ollama API");
        }
    }

    private String sanitizeKoreanOnly(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String sanitized = HANJA_PATTERN.matcher(raw).replaceAll("");
        sanitized = sanitized.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        sanitized = sanitized.replaceAll("\\n{3,}", "\n\n");
        return sanitized.trim();
    }
    
    /**
     * Hugging Face Inference API????????臾먮뼗 ??밴쉐
     */
    private String generateHuggingFaceResponse(String prompt) throws IOException {
        RagProperties.Llm.HuggingFace config = ragProperties.getLlm().getHuggingface();
        String apiKey = config.getApiKey();
        String model = config.getModel();
        
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Hugging Face API key is not configured. Set HUGGINGFACE_API_KEY environment variable.");
        }
        
        String url = "https://api-inference.huggingface.co/models/" + model;
        
        Map<String, Object> body = new HashMap<>();
        body.put("inputs", prompt);
        body.put("parameters", Map.of(
            "max_new_tokens", 500,
            "return_full_text", false
        ));
        
        Headers headers = new Headers.Builder()
                .add("Authorization", "Bearer " + apiKey)
                .build();
        
        try (Response response = httpClientService.postJson(url, body, headers)) {
            if (!response.isSuccessful()) {
                throw new IOException("Hugging Face API error: " + response.code() + " " + response.message());
            }
            
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            if (jsonNode.isArray() && jsonNode.size() > 0) {
                JsonNode first = jsonNode.get(0);
                if (first.has("generated_text")) {
                    return first.get("generated_text").asText();
                }
            }
            throw new IOException("Unexpected response format from Hugging Face API");
        }
    }

    public record GenerationOptions(Integer maxTokens, Double temperature) {}
}

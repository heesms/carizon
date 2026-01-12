package com.carizon.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 관련 설정 프로퍼티
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {
    private Chroma chroma = new Chroma();
    private Embedding embedding = new Embedding();
    private Llm llm = new Llm();
    
    @Data
    public static class Chroma {
        private String baseUrl = "http://localhost:8000";
        private String collectionName = "car_listings";
    }
    
    @Data
    public static class Embedding {
        private String provider = "huggingface"; // huggingface 또는 ollama
        private HuggingFace huggingface = new HuggingFace();
        private Ollama ollama = new Ollama();
        
        @Data
        public static class HuggingFace {
            private String apiKey;
            private String model = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2";
        }
        
        @Data
        public static class Ollama {
            private String baseUrl = "http://localhost:11434";
            private String model = "nomic-embed-text";
        }
    }
    
    @Data
    public static class Llm {
        private String provider = "ollama"; // ollama 또는 huggingface
        private Ollama ollama = new Ollama();
        private HuggingFace huggingface = new HuggingFace();
        
        @Data
        public static class Ollama {
            private String baseUrl = "http://localhost:11434";
            private String model = "llama3.1:8b";
        }
        
        @Data
        public static class HuggingFace {
            private String apiKey;
            private String model = "meta-llama/Llama-3.1-8B-Instruct";
        }
    }
}

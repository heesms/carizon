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
    private Recommendation recommendation = new Recommendation();
    
    /** 모델 임베딩 소스 TSV 파일 경로. 설정 시 앱 기동 시 해당 파일을 읽어 cz_model_embedding_source에 INSERT. (비어 있으면 스킵) */
    private String modelEmbeddingSourceFile = "";

    @Data
    public static class Chroma {
        private String baseUrl = "http://localhost:8000";
        private String tenant = "default_tenant";
        private String database = "default_database";
        private String collectionName = "car_listings";
        /** 모델코드별 설명 임베딩용 컬렉션 (AI 추천 시 활용) */
        private String modelCollectionName = "model_descriptions";
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
        /** 모델 설명 텍스트 자동생성 전용 Ollama 모델명. 비어있으면 ollama.model 사용. (application-local.yaml에서 로컬 강력 모델로 오버라이드) */
        private String modelTextGenModel = "";
        /** 모델 설명 텍스트 자동생성 전용 Ollama baseUrl. 비어있으면 ollama.base-url 사용. */
        private String modelTextGenBaseUrl = "";
        
        @Data
        public static class Ollama {
            private String baseUrl = "http://localhost:11434";
            private String model = "qwen2.5:1.5b";
        }
        
        @Data
        public static class HuggingFace {
            private String apiKey;
            private String model = "meta-llama/Llama-3.1-8B-Instruct";
        }
    }
    
    @Data
    public static class Recommendation {
        /** 평가 사유를 Ollama 등 LLM으로 자연스럽게 다듬을지 여부 (false면 조합 문구 그대로 반환) */
        private boolean reasonPolishEnabled = true;
        private Parser parser = new Parser();
        private Explainer explainer = new Explainer();
        private Reason reason = new Reason();
        private Prompt prompt = new Prompt();

        @Data
        public static class Parser {
            /** 자연어 → 슬롯 파서 단계의 Ollama 타임아웃(ms) */
            private int timeoutMs = 3500;
            /** 연속 실패 횟수 임계값 도달 시 파서 LLM을 일시 비활성화 */
            private int circuitFailThreshold = 3;
            /** 서킷 오픈 유지 시간(ms) */
            private int circuitOpenMs = 120000;
            /** confidence가 이 값보다 낮으면 하드필터를 보수적으로 적용 */
            private double lowConfidenceThreshold = 0.45;
        }

        @Data
        public static class Explainer {
            /** 추천 설명 LLM 단계 활성화 여부 */
            private boolean enabled = true;
            /** 추천 설명 LLM 단계 타임아웃(ms) */
            private int timeoutMs = 8000;
        }
        
        @Data
        public static class Reason {
            private Double highSimilarityThreshold = 0.7;
            private String highSimilarityMessage = "Carizon 점수 ${score}점으로 요구사항과 잘 맞습니다. ";
            private String withinBudgetMessage = "예산 범위 내의 가격입니다. ";
            private Integer lowMileageThreshold = 50000;
            private String lowMileageMessage = "주행거리가 적어 상태가 양호할 가능성이 높습니다. ";
            private String defaultMessage = "검색 조건과 일치합니다.";
        }
        
        @Data
        public static class Prompt {
            private String intro = "사용자가 다음과 같은 요구사항으로 중고차를 찾고 있습니다:";
            private String priceRangeFormat = "가격 범위: ${minPrice}만원 이상 ${maxPrice}만원 이하";
            private String carListTitle = "검색된 차량 목록:";
            private String carFormat = "${index}. ${maker} ${model} ${trim} (${year}년식) 주행거리: ${mileage}km 가격: ${price}만원";
            private String instruction = "위 차량 목록을 바탕으로 사용자에게 친절하고 자연스러운 한국어로 추천 설명을 작성해주세요.\n각 차량의 특징과 사용자 요구사항과의 매칭 포인트를 설명해주세요.\n너무 길지 않게 3-5문장 정도로 간결하게 작성해주세요.";
            private String defaultRecommendation = "검색된 차량 중에서 요구사항에 맞는 차량을 추천드립니다.";
        }
    }
}

# LLM 모델 선택 및 파인튜닝 가이드

## 현재 설정

기본 모델: `llama3.1:8b` (application.yaml)

## 모델 비교

### 1. Llama 3.1 8B (현재 기본값) ✅ 추천

**장점:**
- Meta에서 공식 지원하는 최신 모델
- 영어 성능 우수
- 한국어도 어느 정도 지원 (프롬프트 엔지니어링으로 개선 가능)
- 안정적인 응답 품질
- Ollama에서 잘 최적화됨

**단점:**
- 한국어 성능이 Qwen보다 약간 낮을 수 있음
- 모델 크기가 큼 (약 8GB)

**사용 예시:**
```yaml
rag:
  llm:
    provider: ollama
    ollama:
      model: llama3.1:8b
```

### 2. Qwen2.5 7B

**장점:**
- 한국어 성능이 우수 (중국어 기반이라 한자 처리 좋음)
- 모델 크기가 작음 (약 7GB)
- 빠른 응답 속도

**단점:**
- 영어 성능이 Llama보다 낮을 수 있음
- 일부 사용자 경험에서 불안정한 응답
- 한국어 특화 프롬프트가 필요할 수 있음

**사용 예시:**
```yaml
rag:
  llm:
    provider: ollama
    ollama:
      model: qwen2.5:7b
```

### 3. 기타 모델 옵션

**Gemma 2 9B:**
- Google의 경량 모델
- 빠른 응답 속도
- 한국어 지원 제한적

**Mistral 7B:**
- 프랑스 Mistral AI 모델
- 영어 성능 우수
- 한국어 지원 제한적

## 모델 변경 방법

### 1. Ollama 모델 다운로드

```bash
# Llama 3.1 8B (권장)
ollama pull llama3.1:8b

# Qwen 2.5 7B
ollama pull qwen2.5:7b

# Gemma 2 9B
ollama pull gemma2:9b
```

### 2. application.yaml 수정

```yaml
rag:
  llm:
    provider: ollama
    ollama:
      base-url: http://localhost:11434
      model: llama3.1:8b  # 여기서 모델명 변경
```

### 3. 애플리케이션 재시작

변경 후 애플리케이션을 재시작하면 새 모델이 적용됩니다.

## 파인튜닝 (Fine-tuning)

### Ollama의 파인튜닝 지원

**현재 상태:**
- Ollama는 **직접적인 파인튜닝을 지원하지 않습니다**
- Ollama는 사전 학습된 모델을 실행하는 도구입니다

### 파인튜닝을 위한 대안

#### 1. 프롬프트 엔지니어링 (권장) ✅

**가장 실용적인 방법:**
- 프롬프트 템플릿을 최적화하여 원하는 결과 얻기
- DB에서 프롬프트 관리 (`llm_prompt_config` 테이블)
- 예시, Few-shot learning 등 활용

**예시:**
```yaml
# application.yaml 또는 DB
instruction: |
  당신은 중고차 추천 전문가입니다.
  다음 차량 목록을 보고 사용자에게 친절하고 자연스러운 한국어로 추천 설명을 작성해주세요.
  
  규칙:
  1. 각 차량의 특징을 간결하게 설명
  2. 사용자 요구사항과의 매칭 포인트 강조
  3. 3-5문장으로 간결하게 작성
  4. 친근하고 전문적인 톤 사용
```

#### 2. Modelfile 사용 (Ollama 커스텀 모델)

Ollama에서 Modelfile을 사용하여 모델 동작을 커스터마이징:

```bash
# Modelfile 생성
cat > Modelfile << EOF
FROM llama3.1:8b

# 시스템 프롬프트 설정
SYSTEM """
당신은 중고차 추천 전문가입니다.
한국어로 친절하고 자연스럽게 답변해주세요.
"""

# 파라미터 조정
PARAMETER temperature 0.7
PARAMETER top_p 0.9
EOF

# 커스텀 모델 생성
ollama create carizon-llm -f Modelfile

# 사용
ollama run carizon-llm
```

**application.yaml에서 사용:**
```yaml
rag:
  llm:
    ollama:
      model: carizon-llm  # 커스텀 모델명
```

#### 3. 실제 파인튜닝 (고급)

Ollama가 아닌 원본 모델을 직접 파인튜닝:

**필요한 도구:**
- PyTorch / Transformers
- LoRA (Low-Rank Adaptation) 또는 QLoRA
- 학습 데이터셋

**과정:**
1. 원본 모델 다운로드 (Hugging Face)
2. 학습 데이터 준비 (JSONL 형식)
3. LoRA 파인튜닝 실행
4. 파인튜닝된 모델을 Ollama 형식으로 변환

**예시 (Python):**
```python
from transformers import AutoModelForCausalLM, AutoTokenizer
from peft import LoraConfig, get_peft_model

# 모델 로드
model = AutoModelForCausalLM.from_pretrained("meta-llama/Llama-3.1-8B-Instruct")
tokenizer = AutoTokenizer.from_pretrained("meta-llama/Llama-3.1-8B-Instruct")

# LoRA 설정
lora_config = LoraConfig(
    r=16,
    lora_alpha=32,
    target_modules=["q_proj", "v_proj"],
    lora_dropout=0.05,
)

# LoRA 적용
model = get_peft_model(model, lora_config)

# 학습 실행...
# (학습 코드 생략)

# Ollama 형식으로 변환
# (변환 스크립트 필요)
```

**주의사항:**
- 상당한 컴퓨팅 리소스 필요 (GPU 권장)
- 학습 데이터 준비 및 품질 관리 필요
- 시간과 비용이 많이 소요

## 추천 접근 방법

### 단기 (즉시 적용 가능)

1. **Llama 3.1 8B 사용** (현재 설정 유지)
2. **프롬프트 엔지니어링**으로 성능 개선
   - DB에서 프롬프트 관리 (`/admin/config/llm/prompts`)
   - Few-shot 예시 추가
   - 시스템 프롬프트 최적화

### 중기 (1-2주)

1. **Modelfile로 커스텀 모델 생성**
   - 시스템 프롬프트 내장
   - 파라미터 최적화
   - 테스트 및 반복 개선

### 장기 (필요시)

1. **실제 파인튜닝 고려**
   - 충분한 학습 데이터 확보 후
   - 전문가 도움 받기
   - 비용/효과 분석

## 모델 성능 테스트

### 테스트 방법

```bash
# Ollama에서 직접 테스트
ollama run llama3.1:8b "중고차 추천 설명을 작성해주세요: 현대 싼타페 2020년식, 3000만원 이하"

# 다른 모델과 비교
ollama run qwen2.5:7b "중고차 추천 설명을 작성해주세요: 현대 싼타페 2020년식, 3000만원 이하"
```

### API로 테스트

```bash
# 추천 API 호출
curl -X POST http://localhost:8080/api/recommend \
  -H "Content-Type: application/json" \
  -d '{
    "query": "싼타페 3000만원 이하",
    "maxPrice": 3000,
    "maxResults": 5
  }'
```

## 결론

1. **현재 Llama 3.1 8B 유지 권장** ✅
   - 안정적이고 성능 좋음
   - Qwen보다 전반적으로 우수

2. **파인튜닝은 프롬프트 엔지니어링으로 시작**
   - DB에서 프롬프트 관리
   - Modelfile로 커스텀 모델 생성
   - 실제 파인튜닝은 나중에 고려

3. **모델 변경은 쉽게 가능**
   - application.yaml만 수정
   - Ollama에서 모델 다운로드만 하면 됨

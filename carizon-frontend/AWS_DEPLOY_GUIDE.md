# Carizon AWS 배포 가이드

## 아키텍처 개요

```
Internet
   │
   ▼
[Route 53]  ──  도메인 연결 (선택)
   │
   ▼
[ALB (Application Load Balancer)]
   ├── /        → carizon-frontend  (ECS Fargate, Port 80)
   ├── /api/*   → carizon-backend   (ECS Fargate, Port 8080)
   └── /admin/* → carizon-backend   (ECS Fargate, Port 8080)
        │
        ▼
   [ECS Cluster: carizon-cluster]
        ├── Frontend Service  (carizon-frontend-service)
        ├── Backend Service   (carizon-backend-service)
        ├── Admin Service     (carizon-admin-service)
        └── Ollama Service    (carizon-ollama-service)  ← EC2 GPU 권장

[RDS MySQL 8.0]          ← 관리형 DB
[ElastiCache Redis]      ← 캐시
[OpenSearch]             ← 검색 (ES 호환)
[S3 + CloudFront]        ← 정적 에셋 (선택)
[Chroma on EC2]          ← 벡터 DB (관리형 없음)
```

---

## 1단계: ECR 레포지토리 생성

```bash
# AWS CLI 설정 먼저
aws configure

# ECR 레포 생성 (서울 리전)
aws ecr create-repository --repository-name carizon-frontend --region ap-northeast-2
aws ecr create-repository --repository-name carizon-backend  --region ap-northeast-2
aws ecr create-repository --repository-name carizon-admin    --region ap-northeast-2
```

---

## 2단계: 수동 첫 배포 (로컬 → ECR → ECS)

```bash
# ECR 로그인
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
aws ecr get-login-password --region ap-northeast-2 | \
  docker login --username AWS --password-stdin \
  $AWS_ACCOUNT_ID.dkr.ecr.ap-northeast-2.amazonaws.com

# 프론트엔드 빌드 & 푸시
cd carizon-frontend
docker build -t carizon-frontend .
docker tag  carizon-frontend:latest \
  $AWS_ACCOUNT_ID.dkr.ecr.ap-northeast-2.amazonaws.com/carizon-frontend:latest
docker push $AWS_ACCOUNT_ID.dkr.ecr.ap-northeast-2.amazonaws.com/carizon-frontend:latest

# 백엔드
cd ../backend
docker build -t carizon-backend .
docker tag  carizon-backend:latest \
  $AWS_ACCOUNT_ID.dkr.ecr.ap-northeast-2.amazonaws.com/carizon-backend:latest
docker push $AWS_ACCOUNT_ID.dkr.ecr.ap-northeast-2.amazonaws.com/carizon-backend:latest
```

---

## 3단계: ECS 클러스터 생성

```bash
# Fargate 클러스터 생성
aws ecs create-cluster \
  --cluster-name carizon-cluster \
  --capacity-providers FARGATE FARGATE_SPOT \
  --region ap-northeast-2
```

---

## 4단계: Task Definition (frontend)

`ecs-task-definition-frontend.json` 파일 생성:

```json
{
  "family": "carizon-frontend",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256",
  "memory": "512",
  "executionRoleArn": "arn:aws:iam::ACCOUNT_ID:role/ecsTaskExecutionRole",
  "containerDefinitions": [
    {
      "name": "carizon-frontend",
      "image": "ACCOUNT_ID.dkr.ecr.ap-northeast-2.amazonaws.com/carizon-frontend:latest",
      "portMappings": [{ "containerPort": 80, "protocol": "tcp" }],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/carizon-frontend",
          "awslogs-region": "ap-northeast-2",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": ["CMD-SHELL", "wget -qO- http://localhost/ || exit 1"],
        "interval": 15, "timeout": 5, "retries": 3, "startPeriod": 10
      }
    }
  ]
}
```

```bash
aws ecs register-task-definition \
  --cli-input-json file://ecs-task-definition-frontend.json \
  --region ap-northeast-2
```

---

## 5단계: ECS 서비스 생성 (ALB 연동)

```bash
aws ecs create-service \
  --cluster carizon-cluster \
  --service-name carizon-frontend-service \
  --task-definition carizon-frontend \
  --desired-count 2 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={
    subnets=[subnet-XXXXXXXX,subnet-YYYYYYYY],
    securityGroups=[sg-ZZZZZZZZ],
    assignPublicIp=ENABLED
  }" \
  --load-balancers "targetGroupArn=arn:aws:elasticloadbalancing:...,
                   containerName=carizon-frontend,containerPort=80" \
  --region ap-northeast-2
```

---

## 6단계: GitHub Secrets 등록

GitHub 레포 → Settings → Secrets and variables → Actions:

| Secret 이름           | 값                                              |
|----------------------|------------------------------------------------|
| `AWS_ROLE_ARN`       | `arn:aws:iam::ACCOUNT_ID:role/github-actions-role` |
| `AWS_ACCESS_KEY_ID`  | (Access Key 방식 사용 시)                        |
| `AWS_SECRET_ACCESS_KEY` | (Access Key 방식 사용 시)                     |

---

## 7단계: Ollama 배포 (EC2 권장)

Ollama는 GPU가 필요하므로 EC2 g4dn.xlarge 권장:

```bash
# EC2 인스턴스에서
curl -fsSL https://ollama.com/install.sh | sh
ollama pull llama3          # LLM 모델
ollama pull nomic-embed-text # 임베딩 모델

# 서비스 등록
systemctl enable ollama
systemctl start ollama

# 방화벽: 보안그룹에서 ECS → Ollama EC2 11434 포트 허용
```

백엔드 환경변수에서:
```
RAG_LLM_OLLAMA_BASE_URL=http://OLLAMA_EC2_PRIVATE_IP:11434
RAG_EMBEDDING_OLLAMA_BASE_URL=http://OLLAMA_EC2_PRIVATE_IP:11434
```

---

## 8단계: 환경변수 관리 (AWS Secrets Manager)

```bash
# DB 비밀번호 등 민감 정보를 Secrets Manager에 저장
aws secretsmanager create-secret \
  --name carizon/production \
  --secret-string '{
    "MYSQL_PASSWORD": "실제비밀번호",
    "REDIS_PASSWORD": "실제비밀번호"
  }' \
  --region ap-northeast-2
```

Task Definition에서 `secrets` 배열로 참조.

---

## 운영 체크리스트

- [ ] ECR 레포 3개 생성 (frontend, backend, admin)
- [ ] ECS 클러스터 생성
- [ ] Task Definition 등록 (frontend, backend, admin)
- [ ] ALB + Target Group 설정
- [ ] RDS MySQL 8.0 생성
- [ ] ElastiCache Redis 생성
- [ ] OpenSearch 도메인 생성 (또는 EC2에 ES)
- [ ] EC2 Ollama 서버 구성
- [ ] GitHub Secrets 등록 (AWS_ROLE_ARN 또는 Key)
- [ ] GitHub Actions 워크플로우 첫 실행 확인
- [ ] Route 53 도메인 연결 (선택)
- [ ] ACM SSL 인증서 + ALB HTTPS 리스너 (선택)

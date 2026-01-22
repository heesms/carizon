# Docker 서비스 가이드

## 서비스 목록

- **MySQL**: 포트 3306
- **Redis**: 포트 6379
- **Meilisearch**: 포트 7700 (검색 엔진)
- **ChromaDB**: 포트 8000 (벡터 DB)

## 시작하기

```bash
# 모든 서비스 시작
docker-compose up -d

# 특정 서비스만 시작
docker-compose up -d mysql redis meilisearch chroma

# 서비스 상태 확인
docker-compose ps

# 로그 확인
docker-compose logs -f mysql
```

## 중지하기

```bash
# 모든 서비스 중지
docker-compose down

# 볼륨까지 삭제 (데이터 삭제됨)
docker-compose down -v
```

## 데이터 백업/복원

### MySQL
```bash
# 백업
docker exec carizon-mysql mysqldump -u carizon -pcarizon!1 carizon > backup.sql

# 복원
docker exec -i carizon-mysql mysql -u carizon -pcarizon!1 carizon < backup.sql
```

### Redis
```bash
# 백업
docker exec carizon-redis redis-cli SAVE
docker cp carizon-redis:/data/dump.rdb ./redis-backup.rdb
```

## MySQL 초기 스크립트

`docker/mysql/init/` 디렉토리에 `.sql` 파일을 넣으면 컨테이너 최초 실행 시 자동으로 실행됩니다.

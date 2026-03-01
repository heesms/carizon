모델 임베딩 소스 TSV 파일을 이 폴더에 두세요.

- 파일명: model_embedding_source.txt (application.yaml 기본 경로)
- 형식: DDD.txt와 동일 (탭 구분, UTF-8)
  첫 줄 헤더: model_code  model_name  임베딩용 요약텍스트  임베딩용 요약 텍스트2  임베딩용 요약 텍스트3
  이후 데이터 행

DDD.txt 내용을 model_embedding_source.txt 로 복사해 두면 앱 기동 시 자동으로 cz_model_embedding_source 테이블에 INSERT 됩니다.

# Carizon Backend - Docker build & push to OCI Container Registry
# 사용 전 아래 변수를 본인 OCI 환경에 맞게 수정하세요.

param(
    [string]$Tag = "latest"
)

# === OCI 환경에 맞게 수정 ===
$OCI_REGION    = "ap-seoul-1"           # 리전 (ap-seoul-1, ap-chuncheon-1 등)
$OCI_NAMESPACE = "YOUR_TENANCY_NAMESPACE"  # 테넌시 네임스페이스 (컨테이너 레지스트리 화면에서 확인)
$OCI_REPO      = "carizon/backend"      # 저장소 경로 (미리 OCI에서 생성해 두기)
# =============================

$IMAGE_LOCAL = "carizon-backend:$Tag"
$IMAGE_OCI  = "${OCI_REGION}.ocir.io/${OCI_NAMESPACE}/${OCI_REPO}:$Tag"

$backendDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $backendDir

Write-Host "Building: $IMAGE_LOCAL" -ForegroundColor Cyan
docker build -t $IMAGE_LOCAL .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Tagging: $IMAGE_OCI" -ForegroundColor Cyan
docker tag $IMAGE_LOCAL $IMAGE_OCI

Write-Host "Push (login first: docker login ${OCI_REGION}.ocir.io): $IMAGE_OCI" -ForegroundColor Yellow
docker push $IMAGE_OCI
if ($LASTEXITCODE -ne 0) {
    Write-Host "Push failed. Run: docker login ${OCI_REGION}.ocir.io -u <namespace>/<oci-username>" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Done. Image: $IMAGE_OCI" -ForegroundColor Green

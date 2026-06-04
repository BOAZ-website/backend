#!/bin/bash
# =====================================================
# season-down.sh — 모집 시즌 종료
# 실행: ./season-down.sh
# 사전 조건: AWS CLI + tf 프로파일, conda boaz-ops 환경 활성화
# =====================================================
set -eo pipefail

export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AWS_REGION="ap-northeast-2"
AWS_PROFILE="${AWS_PROFILE:-tf}"

# Python 인터프리터 탐색 (WindowsApps 스텁 제외)
PYTHON=""
for cmd in py python3 python; do
  _path=$(command -v "$cmd" 2>/dev/null || true)
  if [ -n "$_path" ] && ! echo "$_path" | grep -qi "WindowsApps"; then
    PYTHON="$cmd"
    break
  fi
done
if [ -z "$PYTHON" ]; then
  echo "❌ Python을 찾지 못함. conda activate boaz-ops 후 재실행."
  exit 1
fi

echo "=========================================="
echo "  BOAZ 모집 시즌 종료 (season-down)"
echo "=========================================="

# --------------------------------------------------
# 0. SSM 설정 로드
# --------------------------------------------------
echo ""
echo "[0/5] SSM Parameter Store에서 설정 로드 중..."

ssm_get() {
  aws ssm get-parameter --name "/boaz/infra/$1" \
    --region "$AWS_REGION" --profile "$AWS_PROFILE" \
    --query "Parameter.Value" --output text
}

EC2_B_ID=$(ssm_get "EC2_B_ID")
EC2_A_DOMAIN=$(ssm_get "EC2_A_DOMAIN")
EC2_A_PORT=$(ssm_get "EC2_A_PORT")
CLOUDFRONT_DIST_ID=$(ssm_get "CLOUDFRONT_DIST_ID")
RDS_IDENTIFIER=$(ssm_get "RDS_IDENTIFIER")
TARGET_GROUP_ARN=$(ssm_get "TARGET_GROUP_ARN")

echo "  ✅ 설정 로드 완료"

# --------------------------------------------------
# 1. CloudFront origin을 EC2-A 직결로 복귀
# --------------------------------------------------
echo ""
echo "[1/5] CloudFront origin을 EC2-A로 복귀 중..."

CF_SCRIPT=$(cygpath -w "$SCRIPT_DIR/cf_set_origin.py")
ETAG=$(aws cloudfront get-distribution-config --id "$CLOUDFRONT_DIST_ID" \
  --profile "$AWS_PROFILE" --query 'ETag' --output text)
DIST_CONFIG=$(aws cloudfront get-distribution-config --id "$CLOUDFRONT_DIST_ID" \
  --profile "$AWS_PROFILE" --query 'DistributionConfig' --output json)

NEW_CONFIG=$(NEW_DOMAIN="$EC2_A_DOMAIN" NEW_HTTP_PORT="$EC2_A_PORT" \
  "$PYTHON" "$CF_SCRIPT" <<< "$DIST_CONFIG")

aws cloudfront update-distribution --id "$CLOUDFRONT_DIST_ID" \
  --distribution-config "$NEW_CONFIG" \
  --if-match "$ETAG" \
  --profile "$AWS_PROFILE" > /dev/null
echo "  ✅ CloudFront origin 복귀 트리거 완료 (전파 5~15분)"

# --------------------------------------------------
# 2. CloudFront 전파 대기 (Deployed 상태 확인)
# --------------------------------------------------
echo ""
echo "[2/5] CloudFront Deployed 상태 대기 중..."
while true; do
  status=$(aws cloudfront get-distribution --id "$CLOUDFRONT_DIST_ID" \
    --profile "$AWS_PROFILE" --query 'Distribution.Status' --output text)
  if [ "$status" = "Deployed" ]; then
    break
  fi
  echo "  → 현재 status: $status (30초 후 재확인)"
  sleep 30
done
echo "  ✅ CloudFront 전파 완료"

# --------------------------------------------------
# 3. ALB 삭제 (Listener는 ALB와 함께 삭제됨)
# --------------------------------------------------
echo ""
echo "[3/5] ALB 삭제 중..."
ALB_ARN=$(aws elbv2 describe-load-balancers --names "boaz-alb" \
  --region "$AWS_REGION" --profile "$AWS_PROFILE" \
  --query 'LoadBalancers[0].LoadBalancerArn' --output text 2>/dev/null || echo "")

if [ -z "$ALB_ARN" ] || [ "$ALB_ARN" = "None" ]; then
  echo "  ⚠️  ALB 'boaz-alb' 없음. 이미 삭제됐을 가능성. 건너뜀."
else
  aws elbv2 delete-load-balancer --load-balancer-arn "$ALB_ARN" \
    --region "$AWS_REGION" --profile "$AWS_PROFILE"
  echo "  ✅ ALB 삭제 완료"
fi

# --------------------------------------------------
# 4. EC2-B를 Target Group에서 deregister + 태그 제거 + stop
#    중지 전에 CodeDeploy 대상 태그(app=boaz-api)를 제거해야
#    평시(EC2-A 단독) CodeDeploy 배포가 중지된 EC2-B를 대상으로 잡아
#    실패하는 것을 막는다.
# --------------------------------------------------
echo ""
echo "[4/5] EC2-B Target Group 제거 + 태그 제거 + 중지 중..."

aws elbv2 deregister-targets --target-group-arn "$TARGET_GROUP_ARN" \
  --targets "Id=$EC2_B_ID" \
  --region "$AWS_REGION" --profile "$AWS_PROFILE" 2>/dev/null || true

echo "  → CodeDeploy 대상 태그 제거 (app=boaz-api)..."
aws ec2 delete-tags --resources "$EC2_B_ID" \
  --tags "Key=app,Value=boaz-api" \
  --region "$AWS_REGION" --profile "$AWS_PROFILE"

aws ec2 stop-instances --instance-ids "$EC2_B_ID" \
  --region "$AWS_REGION" --profile "$AWS_PROFILE" > /dev/null
echo "  ✅ EC2-B stop 요청 완료 (인스턴스 ID 유지)"

# --------------------------------------------------
# 5. RDS Multi-AZ OFF (트리거만)
# --------------------------------------------------
echo ""
echo "[5/5] RDS Multi-AZ 비활성화 트리거 중..."
aws rds modify-db-instance --db-instance-identifier "$RDS_IDENTIFIER" \
  --no-multi-az --apply-immediately \
  --region "$AWS_REGION" --profile "$AWS_PROFILE" > /dev/null
echo "  ✅ RDS Multi-AZ 비활성화 트리거 완료 (실제 완료는 수십분, 콘솔 확인)"

echo ""
echo "=========================================="
echo "  ✅ season-down 완료"
echo ""
echo "  수동 확인:"
echo "  - https://api.bigdataboaz.com/actuator/health 정상 응답"
echo "  - Target Group에 EC2-A만 healthy"
echo "  - RDS Multi-AZ 비활성화 완료 (콘솔)"
echo "  - EC2-B stopped"
echo "=========================================="

#!/bin/bash
# =====================================================
# season-up.sh — 모집 시즌 시작
# 실행: ./season-up.sh
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
echo "  BOAZ 모집 시즌 시작 (season-up)"
echo "=========================================="

# --------------------------------------------------
# 0. SSM 설정 로드
# --------------------------------------------------
echo ""
echo "[0/7] SSM Parameter Store에서 설정 로드 중..."

ssm_get() {
  aws ssm get-parameter --name "/boaz/infra/$1" \
    --region "$AWS_REGION" --profile "$AWS_PROFILE" \
    --query "Parameter.Value" --output text
}

EC2_B_ID=$(ssm_get "EC2_B_ID")
EC2_A_DOMAIN=$(ssm_get "EC2_A_DOMAIN")
CLOUDFRONT_DIST_ID=$(ssm_get "CLOUDFRONT_DIST_ID")
RDS_IDENTIFIER=$(ssm_get "RDS_IDENTIFIER")
CODEDEPLOY_APP=$(ssm_get "CODEDEPLOY_APP")
CODEDEPLOY_GROUP=$(ssm_get "CODEDEPLOY_GROUP")
TARGET_GROUP_ARN=$(ssm_get "TARGET_GROUP_ARN")
ALB_SG_ID=$(ssm_get "ALB_SG_ID")
ALB_SUBNETS=$(ssm_get "ALB_SUBNETS")
ALB_PORT=$(ssm_get "ALB_PORT")

echo "  ✅ 설정 로드 완료"

# --------------------------------------------------
# 1. EC2-B 태그 부착 + 시작
#    부팅 전에 CodeDeploy 대상 태그(app=boaz-api)를 먼저 부착해야
#    이후 CodeDeploy 재배포(2단계)가 EC2-B까지 대상에 포함한다.
#    (태그는 중지 상태 인스턴스에도 부착 가능)
# --------------------------------------------------
echo ""
echo "[1/7] EC2-B 태그 부착 + 시작 중... ($EC2_B_ID)"
echo "  → CodeDeploy 대상 태그 부착 (app=boaz-api)..."
aws ec2 create-tags --resources "$EC2_B_ID" \
  --tags "Key=app,Value=boaz-api" \
  --region "$AWS_REGION" --profile "$AWS_PROFILE"

aws ec2 start-instances --instance-ids "$EC2_B_ID" \
  --region "$AWS_REGION" --profile "$AWS_PROFILE" > /dev/null
aws ec2 wait instance-running --instance-ids "$EC2_B_ID" \
  --region "$AWS_REGION" --profile "$AWS_PROFILE"
echo "  ✅ EC2-B running"

# --------------------------------------------------
# 2. CodeDeploy 배포 (마지막 성공 배포 재실행 → EC2-A/B 모두 최신화)
# --------------------------------------------------
echo ""
echo "[2/7] CodeDeploy 배포 트리거 중..."

LAST_DEPLOY_ID=$(aws deploy list-deployments \
  --application-name "$CODEDEPLOY_APP" \
  --deployment-group-name "$CODEDEPLOY_GROUP" \
  --include-only-statuses Succeeded \
  --region "$AWS_REGION" --profile "$AWS_PROFILE" \
  --query 'deployments[0]' --output text)

BUNDLE_BUCKET=$(aws deploy get-deployment --deployment-id "$LAST_DEPLOY_ID" \
  --region "$AWS_REGION" --profile "$AWS_PROFILE" \
  --query 'deploymentInfo.revision.s3Location.bucket' --output text)
BUNDLE_KEY=$(aws deploy get-deployment --deployment-id "$LAST_DEPLOY_ID" \
  --region "$AWS_REGION" --profile "$AWS_PROFILE" \
  --query 'deploymentInfo.revision.s3Location.key' --output text)

echo "  → 번들: s3://$BUNDLE_BUCKET/$BUNDLE_KEY"

DEPLOYMENT_ID=$(aws deploy create-deployment \
  --application-name "$CODEDEPLOY_APP" \
  --deployment-group-name "$CODEDEPLOY_GROUP" \
  --deployment-config-name CodeDeployDefault.OneAtATime \
  --s3-location "bucket=$BUNDLE_BUCKET,key=$BUNDLE_KEY,bundleType=zip" \
  --region "$AWS_REGION" --profile "$AWS_PROFILE" \
  --query 'deploymentId' --output text)

echo "  → 배포 ID: $DEPLOYMENT_ID (대기 중, 수분 소요)"
aws deploy wait deployment-successful --deployment-id "$DEPLOYMENT_ID" \
  --region "$AWS_REGION" --profile "$AWS_PROFILE"
echo "  ✅ CodeDeploy 배포 완료"

# --------------------------------------------------
# 3. ALB 생성 + Listener 추가 (Target Group 연결)
#    Target은 ALB가 연결되어야 health check가 시작됨
# --------------------------------------------------
echo ""
echo "[3/7] ALB 생성 중..."

SUBNET_ARGS=$(echo "$ALB_SUBNETS" | tr ',' ' ')
ALB_ARN=$(aws elbv2 create-load-balancer \
  --name "boaz-alb" \
  --type application \
  --scheme internet-facing \
  --ip-address-type ipv4 \
  --subnets $SUBNET_ARGS \
  --security-groups "$ALB_SG_ID" \
  --region "$AWS_REGION" --profile "$AWS_PROFILE" \
  --query 'LoadBalancers[0].LoadBalancerArn' --output text)

echo "  → ALB ARN: $ALB_ARN"
echo "  → active 상태 대기 중..."
aws elbv2 wait load-balancer-available --load-balancer-arns "$ALB_ARN" \
  --region "$AWS_REGION" --profile "$AWS_PROFILE"

ALB_DNS=$(aws elbv2 describe-load-balancers --load-balancer-arns "$ALB_ARN" \
  --region "$AWS_REGION" --profile "$AWS_PROFILE" \
  --query 'LoadBalancers[0].DNSName' --output text)
echo "  → ALB DNS: $ALB_DNS"

echo "  → Listener 추가 중 (HTTP $ALB_PORT → Target Group)..."
aws elbv2 create-listener --load-balancer-arn "$ALB_ARN" \
  --protocol HTTP --port "$ALB_PORT" \
  --default-actions "Type=forward,TargetGroupArn=$TARGET_GROUP_ARN" \
  --region "$AWS_REGION" --profile "$AWS_PROFILE" > /dev/null
echo "  ✅ ALB 준비 완료"

# --------------------------------------------------
# 4. EC2-B를 Target Group에 등록 + healthy 대기
#    EC2-A는 영구 등록되어 있어 ALB 붙는 즉시 healthy
# --------------------------------------------------
echo ""
echo "[4/7] EC2-B를 Target Group에 등록 중..."
aws elbv2 register-targets --target-group-arn "$TARGET_GROUP_ARN" \
  --targets "Id=$EC2_B_ID" \
  --region "$AWS_REGION" --profile "$AWS_PROFILE"

echo "  → EC2-A/B 모두 healthy 대기 중 (~2분)..."
aws elbv2 wait target-in-service --target-group-arn "$TARGET_GROUP_ARN" \
  --region "$AWS_REGION" --profile "$AWS_PROFILE"
echo "  ✅ Target 모두 healthy"

# --------------------------------------------------
# 5. CloudFront origin 교체 (EC2-A → ALB)
# --------------------------------------------------
echo ""
echo "[5/7] CloudFront origin을 ALB로 교체 중..."

CF_SCRIPT=$(cygpath -w "$SCRIPT_DIR/cf_set_origin.py")
ETAG=$(aws cloudfront get-distribution-config --id "$CLOUDFRONT_DIST_ID" \
  --profile "$AWS_PROFILE" --query 'ETag' --output text)
DIST_CONFIG=$(aws cloudfront get-distribution-config --id "$CLOUDFRONT_DIST_ID" \
  --profile "$AWS_PROFILE" --query 'DistributionConfig' --output json)

NEW_CONFIG=$(NEW_DOMAIN="$ALB_DNS" NEW_HTTP_PORT="$ALB_PORT" \
  "$PYTHON" "$CF_SCRIPT" <<< "$DIST_CONFIG")

aws cloudfront update-distribution --id "$CLOUDFRONT_DIST_ID" \
  --distribution-config "$NEW_CONFIG" \
  --if-match "$ETAG" \
  --profile "$AWS_PROFILE" > /dev/null
echo "  ✅ CloudFront origin 교체 완료 (전파 5~15분)"

# --------------------------------------------------
# 6. RDS Multi-AZ ON (트리거만)
# --------------------------------------------------
echo ""
echo "[6/7] RDS Multi-AZ 활성화 트리거 중..."
aws rds modify-db-instance --db-instance-identifier "$RDS_IDENTIFIER" \
  --multi-az --apply-immediately \
  --region "$AWS_REGION" --profile "$AWS_PROFILE" > /dev/null
echo "  ✅ RDS Multi-AZ 트리거 완료 (실제 완료는 수십분, 콘솔 확인)"

# --------------------------------------------------
# 7. 헬스체크 (직접 ALB 호출은 SG 차단됨 → CloudFront 통해 확인)
# --------------------------------------------------
echo ""
echo "[7/7] 전파 후 헬스체크 안내"
echo "  CloudFront 전파(5~15분) 후 직접 확인:"
echo "    curl https://api.bigdataboaz.com/actuator/health"

echo ""
echo "=========================================="
echo "  ✅ season-up 완료"
echo ""
echo "  수동 확인:"
echo "  - CloudFront 전파 완료 후 https://api.bigdataboaz.com 동작"
echo "  - Target Group에 EC2-A/B 모두 healthy"
echo "  - RDS Multi-AZ 전환 (콘솔)"
echo "=========================================="

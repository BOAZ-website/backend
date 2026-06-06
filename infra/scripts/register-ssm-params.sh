#!/bin/bash
# =====================================================
# register-ssm-params.sh — SSM Parameter Store 등록
# 최초 1회 실행. 값 변경 시 --overwrite 추가 후 재실행
# 실행: ./register-ssm-params.sh  (Git Bash 또는 Linux/Mac 터미널)
# 주의: Windows Git Bash에서 실행 시 경로 자동 변환 방지
# =====================================================

# Windows Git Bash(MSYS)가 /boaz/... 경로를 Windows 경로로 변환하는 것 방지
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"

AWS_REGION="ap-northeast-2"

aws ssm put-parameter \
  --name "/boaz/infra/EC2_B_ID" \
  --value "i-05405847d3897364a" \
  --type String \
  --region "$AWS_REGION" \
  --profile tf

aws ssm put-parameter \
  --name "/boaz/infra/CLOUDFRONT_DIST_ID" \
  --value "E2SER81QYNPRO9" \
  --type String \
  --region "$AWS_REGION" \
  --profile tf

aws ssm put-parameter \
  --name "/boaz/infra/EC2_A_DOMAIN" \
  --value "ec2-15-165-102-5.ap-northeast-2.compute.amazonaws.com" \
  --type String \
  --region "$AWS_REGION" \
  --profile tf

aws ssm put-parameter \
  --name "/boaz/infra/RDS_IDENTIFIER" \
  --value "boaz-prod-db" \
  --type String \
  --region "$AWS_REGION" \
  --profile tf

aws ssm put-parameter \
  --name "/boaz/infra/CODEDEPLOY_APP" \
  --value "boaz-backend" \
  --type String \
  --region "$AWS_REGION" \
  --profile tf

aws ssm put-parameter \
  --name "/boaz/infra/CODEDEPLOY_GROUP" \
  --value "codedeploy-prod" \
  --type String \
  --region "$AWS_REGION" \
  --profile tf

aws ssm put-parameter \
  --name "/boaz/infra/S3_BUCKET" \
  --value "boaz-codedeploy-bucket" \
  --type String \
  --region "$AWS_REGION" \
  --profile tf

# ===== ALB / Target Group (시즌 전환용) =====

aws ssm put-parameter \
  --name "/boaz/infra/TARGET_GROUP_ARN" \
  --value "arn:aws:elasticloadbalancing:ap-northeast-2:156312218841:targetgroup/boaz-api-tg/d42747d4f4ff49e6" \
  --type String \
  --region "$AWS_REGION" \
  --profile tf

aws ssm put-parameter \
  --name "/boaz/infra/ALB_SG_ID" \
  --value "sg-0b98cad292282ebe5" \
  --type String \
  --region "$AWS_REGION" \
  --profile tf

aws ssm put-parameter \
  --name "/boaz/infra/ALB_SUBNETS" \
  --value "subnet-02fd34391da42563b,subnet-092734d1357224a25" \
  --type String \
  --region "$AWS_REGION" \
  --profile tf

aws ssm put-parameter \
  --name "/boaz/infra/EC2_A_PORT" \
  --value "8080" \
  --type String \
  --region "$AWS_REGION" \
  --profile tf

aws ssm put-parameter \
  --name "/boaz/infra/ALB_PORT" \
  --value "80" \
  --type String \
  --region "$AWS_REGION" \
  --profile tf

echo ""
echo "등록 완료. 확인:"
aws ssm get-parameters-by-path \
  --path "/boaz/infra/" \
  --region "$AWS_REGION" \
  --query "Parameters[*].{Name:Name,Value:Value}" \
  --output table \
  --profile tf

#!/bin/bash
set -e

mkdir -p /run/boaz
> /run/boaz/app.env

PARAMS=(
    DB_URL
    DB_USERNAME
    DB_PASSWORD
    JWT_SECRET
    S3_RECRUITMENT_BUCKET_NAME
    S3_ARCHIVING_BUCKET_NAME
    KAKAO_CLIENT_ID
    KAKAO_CLIENT_SECRET
    GOOGLE_CLIENT_ID
    GOOGLE_CLIENT_SECRET
    NAVER_CLIENT_ID
    NAVER_CLIENT_SECRET
)

for param in "${PARAMS[@]}"; do
    value=$(aws ssm get-parameter \
        --name "$param" \
        --with-decryption \
        --region ap-northeast-2 \
        --query "Parameter.Value" \
        --output text)
    echo "${param}=${value}" >> /run/boaz/app.env
done

chown boaz:boaz /run/boaz/app.env
chmod 600 /run/boaz/app.env

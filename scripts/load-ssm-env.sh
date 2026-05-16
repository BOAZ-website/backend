#!/bin/bash
set -e

mkdir -p /run/boaz

aws ssm get-parameters-by-path \
    --path /boaz/prod \
    --with-decryption \
    --region ap-northeast-2 \
    --query "Parameters[*].[Name,Value]" \
    --output text | \
    awk '{n=$1; sub(".*/","",n); $1=""; sub(/^ /,""); gsub(/\047/, "\047\\\047\047"); print toupper(n)"=\047"$0"\047"}' \
    > /run/boaz/app.env

chown boaz:boaz /run/boaz/app.env
chmod 600 /run/boaz/app.env

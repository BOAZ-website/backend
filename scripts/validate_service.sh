#!/bin/bash
set -e

sleep 30

for i in {1..10}; do
    if curl -sf http://localhost:8080/actuator/health > /dev/null; then
        echo "헬스체크 성공"
        exit 0
    fi
    echo "시도 $i 실패, 10초 후 재시도..."
    sleep 10
done

echo "헬스체크 실패 — 로그:"
journalctl -u boaz.service -n 50
exit 1

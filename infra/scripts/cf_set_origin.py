"""
CloudFront 단일 origin의 DomainName/HTTPPort 교체
stdin:  DistributionConfig JSON
env:    NEW_DOMAIN, NEW_HTTP_PORT
stdout: 수정된 config JSON

사용처: season-up/down에서 origin을 EC2-A ↔ ALB 사이로 교체
가정:   distribution에 origin이 1개만 있음 (boaz API 서버)
"""
import json
import os
import sys

config = json.load(sys.stdin)
new_domain = os.environ["NEW_DOMAIN"]
new_port = int(os.environ["NEW_HTTP_PORT"])

if config["Origins"]["Quantity"] != 1:
    print(
        f"ERROR: expected exactly 1 origin, found {config['Origins']['Quantity']}",
        file=sys.stderr,
    )
    sys.exit(1)

origin = config["Origins"]["Items"][0]
origin["DomainName"] = new_domain
origin["CustomOriginConfig"]["HTTPPort"] = new_port

print(json.dumps(config))

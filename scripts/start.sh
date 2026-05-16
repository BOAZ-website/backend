#!/bin/bash
set -e

/opt/boaz/scripts/load-ssm-env.sh

set -a
source /run/boaz/app.env
set +a

exec /usr/bin/java \
    -Xms512m -Xmx1g \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -Dfile.encoding=UTF-8 \
    -Dspring.profiles.active=prod \
    -jar /opt/boaz/app.jar

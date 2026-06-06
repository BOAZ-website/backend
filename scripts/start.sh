#!/bin/bash
set -e

exec /usr/bin/java \
    -Xms512m -Xmx1g \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -Dfile.encoding=UTF-8 \
    -Dspring.profiles.active=prod \
    -jar /opt/boaz/app.jar

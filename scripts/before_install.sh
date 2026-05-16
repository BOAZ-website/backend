#!/bin/bash
set -e

if systemctl is-active --quiet boaz.service; then
    systemctl stop boaz.service
fi

id boaz >/dev/null 2>&1 || useradd --system --home /opt/boaz --shell /usr/sbin/nologin boaz

mkdir -p /opt/boaz/logs /opt/boaz/scripts /run/boaz /var/log/boaz
chown -R boaz:boaz /opt/boaz /run/boaz /var/log/boaz

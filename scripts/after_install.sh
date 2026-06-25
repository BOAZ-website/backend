#!/bin/bash
set -e

chown boaz:boaz /opt/boaz/app.jar
chmod 644 /opt/boaz/app.jar
chmod 755 /opt/boaz/scripts/*.sh

systemctl daemon-reload
systemd-tmpfiles --create /etc/tmpfiles.d/boaz-tmpfiles.conf

# journald 로그 상한 적용
systemctl restart systemd-journald

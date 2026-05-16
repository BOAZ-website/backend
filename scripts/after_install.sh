#!/bin/bash
set -e

chown boaz:boaz /opt/boaz/app.jar
chmod 644 /opt/boaz/app.jar
chmod 755 /opt/boaz/scripts/*.sh

systemctl daemon-reload

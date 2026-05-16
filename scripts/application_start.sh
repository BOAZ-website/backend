#!/bin/bash
set -e

systemctl enable boaz.service
systemctl restart boaz.service
systemctl --no-pager status boaz.service

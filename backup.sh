#!/bin/bash
# Daily MySQL dump, run by launchd on the Mac host / cron on Linux (installed by deploy.sh).
# Keeps 7 days in ~/mirattic-flow-backups.
set -euo pipefail
export PATH=/opt/homebrew/bin:/usr/local/bin:$HOME/.orbstack/bin:$PATH
umask 077
dir=$HOME/mirattic-flow-backups
mkdir -p "$dir"
# Prune first, so a failing dump never keeps old dumps (and deleted accounts) past the retention.
find "$dir" -name 'mirattic-flow-*.sql.gz*' -mtime +6 -delete
file=$dir/mirattic-flow-$(date +%Y%m%d-%H%M).sql.gz
trap 'rm -f "$file.tmp"' EXIT
docker exec mirattic-flow-prod-mysql-1 sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --no-tablespaces "$MYSQL_DATABASE"' \
  | gzip > "$file.tmp"
mv "$file.tmp" "$file"
echo "backup $file"

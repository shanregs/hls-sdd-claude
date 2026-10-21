#!/usr/bin/env bash
# Restores a database backup from S3 into a separate database so it can be checked without touching live data.
#
#   scripts/backup/restore.sh db/hls-2026-10-25.dump            restore into hls_restore
#   scripts/backup/restore.sh db/hls-2026-10-25.dump hls_check  restore into the named database
#
# To replace the live database, stop the backend, drop and recreate `hls`, then restore into it by hand. Uploaded
# files: aws s3 cp s3://$BACKUP_BUCKET/files/<file>.tgz - | docker compose exec -T backend tar xzf - -C /app
# Same configuration as backup.sh.
set -euo pipefail

[ -f /etc/hls/backup.env ] && . /etc/hls/backup.env
: "${BACKUP_BUCKET:?set BACKUP_BUCKET}"
COMPOSE_FILE=${COMPOSE_FILE:-/opt/hls/docker-compose.prod.yml}
ENV_FILE=${ENV_FILE:-/opt/hls/.env.prod}
key=${1:?usage: restore.sh <s3 key, e.g. db/hls-2026-10-25.dump> [target database]}
target=${2:-hls_restore}
[ "$target" != "hls" ] || { echo "refusing to restore over the live database 'hls'" >&2; exit 1; }

dc() { docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"; }

dc exec -T postgres psql -U hls -d postgres -v ON_ERROR_STOP=1 \
  -c "DROP DATABASE IF EXISTS \"$target\"" -c "CREATE DATABASE \"$target\""
aws s3 cp "s3://$BACKUP_BUCKET/$key" - \
  | dc exec -T postgres pg_restore -U hls -d "$target" --no-owner --exit-on-error
echo "restored $key into database $target; sample check:"
dc exec -T postgres psql -U hls -d "$target" -c "SELECT count(*) AS tables FROM information_schema.tables WHERE table_schema = 'public'"

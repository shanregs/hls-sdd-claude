#!/usr/bin/env bash
# Backs up the production database and uploaded files to S3. Run on the VM from cron, as a user in the docker group
# whose instance role may s3:PutObject on the bucket (docs/aws-deployment-and-ddos-protection.md, section 5.6).
#
#   /etc/cron.d/hls-backup, with the server in Asia/Kolkata (weekly, Sunday 00:00):
#     0 0 * * 0 ubuntu /opt/hls/scripts/backup/backup.sh >> /var/log/hls-backup.log 2>&1
#   Daily gives a 24 h recovery point for the same cost:  0 0 * * * ...
#
# Configuration (environment, or /etc/hls/backup.env which is sourced when present):
#   BACKUP_BUCKET   required, S3 bucket name
#   COMPOSE_FILE    default /opt/hls/docker-compose.prod.yml
#   ENV_FILE        default /opt/hls/.env.prod
set -euo pipefail

[ -f /etc/hls/backup.env ] && . /etc/hls/backup.env
: "${BACKUP_BUCKET:?set BACKUP_BUCKET}"
COMPOSE_FILE=${COMPOSE_FILE:-/opt/hls/docker-compose.prod.yml}
ENV_FILE=${ENV_FILE:-/opt/hls/.env.prod}

# One run at a time (a slow upload must not overlap the next schedule).
exec 9>/tmp/hls-backup.lock
flock -n 9 || { echo "$(date -Is) another backup is still running, skipping"; exit 0; }

dc() { docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"; }
stamp=$(date +%F)
db_key="db/hls-$stamp.dump"
files_key="files/hls-files-$stamp.tgz"

# Fails (and so does the script) if pg_dump, tar or the upload fails, because of pipefail.
echo "$(date -Is) dumping database to s3://$BACKUP_BUCKET/$db_key"
dc exec -T postgres pg_dump -U hls -Fc hls \
  | aws s3 cp - "s3://$BACKUP_BUCKET/$db_key" --sse AES256

echo "$(date -Is) archiving uploaded files to s3://$BACKUP_BUCKET/$files_key"
dc exec -T backend tar czf - -C /app data \
  | aws s3 cp - "s3://$BACKUP_BUCKET/$files_key" --sse AES256

# A zero-byte object means the stream broke without an error code; fail loudly so cron mail / monitoring notices.
for key in "$db_key" "$files_key"; do
  size=$(aws s3api head-object --bucket "$BACKUP_BUCKET" --key "$key" --query ContentLength --output text)
  [ "$size" -gt 0 ] || { echo "$(date -Is) backup $key is empty" >&2; exit 1; }
  echo "$(date -Is) ok $key ($size bytes)"
done

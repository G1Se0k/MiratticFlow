#!/bin/sh
# Runs on the deploy host after CI has loaded mirattic-flow-backend / mirattic-flow-frontend:<tag>
# (.github/workflows/deploy.yml): sh deploy.sh <tag>
# Switches :current to the new images and rolls back if the stack doesn't come up.
set -eu
export PATH=/opt/homebrew/bin:/usr/local/bin:$PATH
cd "$(dirname "$0")"
test -f .env.prod
compose() { docker compose -f docker-compose.prod.yml --env-file .env.prod "$@"; }

# Daily DB backup (backup.sh) at 04:15, refreshed on every deploy: launchd on the Mac host, cron on Linux.
# Fails the deploy before the image switch: a deploy must not report success with backups stopped.
mkdir -p "$HOME/mirattic-flow-backups" && chmod 700 "$HOME/mirattic-flow-backups"
if [ "$(uname)" = Darwin ]; then
  # launchd jobs can't read TCC-protected folders like ~/Documents: the script runs from Application Support.
  support="$HOME/Library/Application Support/mirattic-flow"
  agent="$HOME/Library/LaunchAgents/com.mirattic.flow.backup.plist"
  mkdir -p "$support" "$HOME/Library/LaunchAgents"
  cp backup.sh "$support/backup.sh"
  cat > "$agent" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>com.mirattic.flow.backup</string>
  <key>ProgramArguments</key><array><string>/bin/bash</string><string>$support/backup.sh</string></array>
  <key>StartCalendarInterval</key><dict><key>Hour</key><integer>4</integer><key>Minute</key><integer>15</integer></dict>
  <key>StandardOutPath</key><string>$HOME/mirattic-flow-backups/backup.log</string>
  <key>StandardErrorPath</key><string>$HOME/mirattic-flow-backups/backup.log</string>
</dict></plist>
PLIST
  launchctl bootout "gui/$(id -u)/com.mirattic.flow.backup" 2>/dev/null || true
  launchctl bootstrap "gui/$(id -u)" "$agent"
  launchctl print "gui/$(id -u)/com.mirattic.flow.backup" >/dev/null
else
  job="15 4 * * * /bin/bash $PWD/backup.sh >> $HOME/mirattic-flow-backups/backup.log 2>&1 # mirattic-flow-backup"
  systemctl is-active --quiet cron
  # Other services' jobs share this crontab: an unreadable one is an error, never an empty one to overwrite.
  current=$(crontab -l 2>&1) || case "$current" in "no crontab for"*) current= ;; *) echo "$current" >&2; exit 1 ;; esac
  { printf '%s\n' "$current" | grep -vF "# mirattic-flow-backup" | grep . || true; echo "$job"; } | crontab -
  crontab -l | grep -qF "$job"
fi

# The same checks as .github/workflows/uptime.yml, on the loopback ports.
healthy() {
  for _ in $(seq 60); do
    [ "$(curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/users/me)" = 401 ] \
      && [ "$(curl -s --max-time 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:3001/login)" = 200 ] && return 0
    sleep 2
  done
  return 1
}

# The running containers' images are the previous release (also before the first CI deploy, when no :current
# exists yet). tr: a failed inspect still prints a newline.
previous_backend=$(docker inspect -f '{{.Image}}' mirattic-flow-prod-backend-1 2>/dev/null | tr -d '\n')
previous_frontend=$(docker inspect -f '{{.Image}}' mirattic-flow-prod-frontend-1 2>/dev/null | tr -d '\n')
docker tag "mirattic-flow-backend:$1" mirattic-flow-backend:current
docker tag "mirattic-flow-frontend:$1" mirattic-flow-frontend:current

# `if` keeps set -e from exiting on a failed `compose up`: every failure goes through the rollback below.
if compose up -d && healthy; then
  echo "deployed $1"
  # Keep the five newest tagged images of each for manual rollback.
  for image in mirattic-flow-backend mirattic-flow-frontend; do
    docker images "$image" --format '{{.Tag}}' | grep -v '^current$' | tail -n +6 | xargs -I{} docker rmi "$image:{}" >/dev/null 2>&1 || true
  done
  exit 0
fi

echo "deploy of $1 failed" >&2
compose logs --tail 80 backend frontend >&2 || true
if [ -n "$previous_backend" ] && [ -n "$previous_frontend" ]; then
  docker tag "$previous_backend" mirattic-flow-backend:current
  docker tag "$previous_frontend" mirattic-flow-frontend:current
  if compose up -d && healthy; then
    echo "rolled back to the previous images" >&2
  else
    echo "rollback failed too" >&2
  fi
fi
exit 1

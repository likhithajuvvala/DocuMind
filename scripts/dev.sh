#!/usr/bin/env bash
#
# Local dev orchestrator: infra runs in Docker (postgres, redis, kafka, qdrant, minio,
# prometheus, grafana), the four Spring services run via gradlew bootRun, and the frontend
# via npm run dev — each backgrounded natively so you get hot-reload instead of a Docker
# rebuild per change. Every app service's application.yml already defaults to localhost for
# every infra host/port, so no env overrides are needed for native mode to talk to the
# Docker-published infra ports.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.dev"
PID_DIR="$RUN_DIR/pids"
LOG_DIR="$RUN_DIR/logs"
mkdir -p "$PID_DIR" "$LOG_DIR"

INFRA_SERVICES=(postgres minio kafka qdrant redis prometheus grafana)
INFRA_HEALTHCHECKED=(postgres minio kafka qdrant redis)
APP_SERVICES=(gateway document ingestion query frontend)
ALL_SERVICES=(infra "${APP_SERVICES[@]}")

if [[ -f "$ROOT_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
  set +a
fi

# .env is written for the full Docker Compose network (container hostnames, and secrets left
# blank for local use). In native mode that's wrong on two counts: an exported-but-empty
# JWT_SECRET beats application.yml's non-empty default (Spring only falls back on an *unset*
# var, not an empty one), which crashes every service with a 0-bit HMAC key; and DATABASE_URL /
# KAFKA_BOOTSTRAP_SERVERS / S3_ENDPOINT point at container hostnames that don't resolve from the
# host. Blank out the former and force the latter to localhost, matching the ports Docker
# publishes.
[[ -z "${JWT_SECRET:-}" ]] && unset JWT_SECRET
export DATABASE_URL="jdbc:postgresql://localhost:5432/documind"
export KAFKA_BOOTSTRAP_SERVERS="localhost:29092"
export S3_ENDPOINT="http://localhost:9000"
export QDRANT_HOST="localhost"
export REDIS_HOST="localhost"
# application.yml defaults this to false (compose is the only place it defaults to true) — set it
# explicitly so native dev gets the same seeded demo@documind.test login docker-compose gives you.
export DEMO_SEED_ENABLED="${DEMO_SEED_ENABLED:-true}"

gradle_module_for() {
  case "$1" in
    gateway) echo "gateway-service" ;;
    document) echo "document-service" ;;
    ingestion) echo "ingestion-worker" ;;
    query) echo "query-service" ;;
  esac
}

health_url_for() {
  case "$1" in
    gateway) echo "http://localhost:8080/actuator/health" ;;
    document) echo "http://localhost:8081/actuator/health" ;;
    ingestion) echo "http://localhost:8082/actuator/health" ;;
    query) echo "http://localhost:8083/actuator/health" ;;
    frontend) echo "http://localhost:3000/login" ;;
  esac
}

pid_file() { echo "$PID_DIR/$1.pid"; }
log_file() { echo "$LOG_DIR/$1.log"; }

is_valid_service() {
  local svc="$1" s
  for s in "${ALL_SERVICES[@]}"; do [[ "$s" == "$svc" ]] && return 0; done
  return 1
}

is_running() {
  local pf; pf="$(pid_file "$1")"
  [[ -f "$pf" ]] && kill -0 "$(cat "$pf")" 2>/dev/null
}

start_infra() {
  echo "infra      starting (${INFRA_SERVICES[*]})..."
  (cd "$ROOT_DIR" && docker compose up -d "${INFRA_SERVICES[@]}")

  echo "infra      waiting for health checks..."
  local timeout=180 elapsed=0
  while (( elapsed < timeout )); do
    local all_healthy=true svc cid status
    for svc in "${INFRA_HEALTHCHECKED[@]}"; do
      cid=$(cd "$ROOT_DIR" && docker compose ps -q "$svc" 2>/dev/null)
      status=$([[ -n "$cid" ]] && docker inspect -f '{{.State.Health.Status}}' "$cid" 2>/dev/null || echo "missing")
      if [[ "$status" != "healthy" ]]; then
        all_healthy=false
        break
      fi
    done
    if $all_healthy; then
      echo "infra      all services healthy"
      return 0
    fi
    sleep 3
    elapsed=$((elapsed + 3))
  done
  echo "infra      timed out waiting for health checks — check 'scripts/dev.sh logs infra'" >&2
  return 1
}

stop_infra() {
  echo "infra      stopping..."
  (cd "$ROOT_DIR" && docker compose stop "${INFRA_SERVICES[@]}")
}

start_service() {
  local svc="$1"
  if is_running "$svc"; then
    echo "$svc  already running (pid $(cat "$(pid_file "$svc")"))"
    return
  fi

  local run_cmd
  case "$svc" in
    gateway|document|ingestion|query)
      local module; module="$(gradle_module_for "$svc")"
      run_cmd="cd '$ROOT_DIR/backend' && exec ./gradlew --console=plain ':${module}:bootRun'"
      ;;
    frontend)
      run_cmd="cd '$ROOT_DIR/frontend' && exec npm run dev"
      ;;
    *)
      echo "$svc  unknown app service" >&2
      return 1
      ;;
  esac

  echo "$svc  starting..."
  # setsid puts the process in its own group so stop_service can kill the whole subtree
  # (gradlew forks a JVM; a plain `kill $!` would only kill the wrapper shell).
  setsid bash -c "$run_cmd" > "$(log_file "$svc")" 2>&1 < /dev/null &
  echo "$!" > "$(pid_file "$svc")"
  disown
}

stop_service() {
  local svc="$1"
  if ! is_running "$svc"; then
    echo "$svc  not running"
    rm -f "$(pid_file "$svc")"
    return
  fi

  local pid; pid=$(cat "$(pid_file "$svc")")
  echo "$svc  stopping (pid $pid)..."
  kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true

  local waited=0
  while kill -0 "$pid" 2>/dev/null && (( waited < 30 )); do
    sleep 1
    waited=$((waited + 1))
  done
  if kill -0 "$pid" 2>/dev/null; then
    echo "$svc  still up after 30s, force killing"
    kill -KILL -- "-$pid" 2>/dev/null || kill -KILL "$pid" 2>/dev/null || true
  fi
  rm -f "$(pid_file "$svc")"
}

status_service() {
  local svc="$1"
  if is_running "$svc"; then
    local pid health httpstat
    pid=$(cat "$(pid_file "$svc")")
    health="$(health_url_for "$svc")"
    httpstat=$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 "$health" 2>/dev/null || echo "down")
    printf "%-10s running   pid=%-7s http=%s\n" "$svc" "$pid" "$httpstat"
  else
    printf "%-10s stopped\n" "$svc"
  fi
}

status_infra() {
  echo "infra:"
  (cd "$ROOT_DIR" && docker compose ps "${INFRA_SERVICES[@]}" --format 'table {{.Name}}\t{{.Status}}' 2>/dev/null) \
    || echo "  not running"
}

logs_service() {
  local svc="$1" follow="${2:-}"
  if [[ "$svc" == "infra" ]]; then
    (cd "$ROOT_DIR" && docker compose logs ${follow:+-f} "${INFRA_SERVICES[@]}")
    return
  fi
  local lf; lf="$(log_file "$svc")"
  if [[ ! -f "$lf" ]]; then
    echo "no logs for $svc yet"
    return
  fi
  if [[ "$follow" == "-f" ]]; then tail -f "$lf"; else tail -n 100 "$lf"; fi
}

usage() {
  cat <<EOF
Usage: scripts/dev.sh <command> [service...]

Commands:
  start [service...]    Start infra (docker) + app services natively. Default: all.
  stop  [service...]    Stop services. Default: app services first, then infra.
  restart [service...]  stop then start.
  status                Show running state of every service.
  logs <service> [-f]   Tail a service's log (add -f to follow).

Services: infra gateway document ingestion query frontend
EOF
}

cmd="${1:-}"
[[ $# -gt 0 ]] && shift

case "$cmd" in
  start)
    services=("$@")
    [[ ${#services[@]} -eq 0 ]] && services=("${ALL_SERVICES[@]}")
    for s in "${services[@]}"; do
      is_valid_service "$s" || { echo "unknown service: $s" >&2; exit 1; }
    done
    for s in "${services[@]}"; do
      if [[ "$s" == "infra" ]]; then
        start_infra || true
      else
        start_service "$s"
      fi
    done
    ;;
  stop)
    services=("$@")
    if [[ ${#services[@]} -eq 0 ]]; then
      services=("${APP_SERVICES[@]}" infra)
    fi
    for s in "${services[@]}"; do
      is_valid_service "$s" || { echo "unknown service: $s" >&2; exit 1; }
    done
    for s in "${services[@]}"; do
      if [[ "$s" == "infra" ]]; then stop_infra; else stop_service "$s"; fi
    done
    ;;
  restart)
    services=("$@")
    [[ ${#services[@]} -eq 0 ]] && services=("${ALL_SERVICES[@]}")
    for s in "${services[@]}"; do
      is_valid_service "$s" || { echo "unknown service: $s" >&2; exit 1; }
    done
    for s in "${services[@]}"; do
      if [[ "$s" == "infra" ]]; then stop_infra; else stop_service "$s"; fi
    done
    for s in "${services[@]}"; do
      if [[ "$s" == "infra" ]]; then start_infra || true; else start_service "$s"; fi
    done
    ;;
  status)
    status_infra
    for s in "${APP_SERVICES[@]}"; do status_service "$s"; done
    ;;
  logs)
    [[ $# -ge 1 ]] || { echo "usage: scripts/dev.sh logs <service> [-f]" >&2; exit 1; }
    is_valid_service "$1" || { echo "unknown service: $1" >&2; exit 1; }
    logs_service "$1" "${2:-}"
    ;;
  ""|-h|--help|help)
    usage
    ;;
  *)
    echo "unknown command: $cmd" >&2
    usage
    exit 1
    ;;
esac

#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="circleguard-dev"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORT_DIR="${SCRIPT_DIR}/reports"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_FILE="${REPORT_DIR}/report_${TIMESTAMP}.html"

SERVICES=(
  "svc/circleguard-auth-service:8180"
  "svc/circleguard-identity-service:8083"
  "svc/circleguard-form-service:8086"
  "svc/circleguard-promotion-service:8088"
  "svc/circleguard-dashboard-service:8084"
)

PF_PIDS=()

cleanup() {
  echo ""
  echo "=== Cleaning up port-forwards ==="
  for pid in "${PF_PIDS[@]}"; do
    if kill -0 "$pid" 2>/dev/null; then
      echo "  Stopping port-forward (PID: $pid)"
      kill "$pid" 2>/dev/null || true
    fi
  done
  echo "Done."
}
trap cleanup EXIT

echo "=== Starting port-forwards (namespace: ${NAMESPACE}) ==="
for svc_entry in "${SERVICES[@]}"; do
  IFS=":" read -r svc local_port <<< "$svc_entry"
  echo "  Forwarding ${svc} -> localhost:${local_port}"
  kubectl port-forward -n "${NAMESPACE}" "${svc}" "${local_port}:${local_port}" >/dev/null 2>&1 &
  PF_PIDS+=($!)
done

echo ""
echo "=== Waiting for port-forwards to establish ==="
sleep 5

echo ""
echo "=== Verifying port-forwards ==="
for svc_entry in "${SERVICES[@]}"; do
  IFS=":" read -r _ local_port <<< "$svc_entry"
  success=false
  for i in $(seq 1 12); do
    if curl -s -o /dev/null --max-time 5 "http://localhost:${local_port}"; then
      echo "  localhost:${local_port} OK"
      success=true
      break
    fi
    if [ "$i" -eq 12 ]; then
      echo "  localhost:${local_port} FAILED (not reachable after 60s)"
    fi
    sleep 5
  done
  if [ "$success" = false ]; then
    echo "ERROR: Port-forward verification failed. Aborting."
    exit 1
  fi
done

echo ""
echo "=== Running E2E tests ==="
mkdir -p "${REPORT_DIR}"
set +e
cd "${SCRIPT_DIR}"
python -m pytest test_flows.py \
  --html="${REPORT_FILE}" \
  --self-contained-html \
  -v \
  --tb=long
EXIT_CODE=$?
set -e

echo ""
echo "=== Report: ${REPORT_FILE} ==="
exit $EXIT_CODE

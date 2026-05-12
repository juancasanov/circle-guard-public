#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORT_DIR="${SCRIPT_DIR}/reports"
CSV_DIR="${SCRIPT_DIR}/csv"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

mkdir -p "${REPORT_DIR}" "${CSV_DIR}"

LOCUST_FILE="${SCRIPT_DIR}/locustfile.py"

run_scenario() {
    local name="$1"
    local users="$2"
    local spawn_rate="$3"
    local run_time="$4"  # formato: 2m, 3m, 5m

    local html_out="${REPORT_DIR}/${name}_${TIMESTAMP}.html"
    local csv_prefix="${CSV_DIR}/${name}_${TIMESTAMP}"

    echo ""
    echo "======================================================================"
    echo "  ESCENARIO: ${name}"
    echo "  Usuarios: ${users} | Spawn rate: ${spawn_rate}/s | Duración: ${run_time}"
    echo "======================================================================"

    set +e
    locust --headless \
        -f "${LOCUST_FILE}" \
        -u "${users}" \
        -r "${spawn_rate}" \
        -t "${run_time}" \
        --html="${html_out}" \
        --csv="${csv_prefix}" \
        --host="http://localhost:8180" \
        --stop-timeout=10 \
        --print-stats
    local exit_code=$?
    set -e

    if [ $exit_code -ne 0 ]; then
        echo "WARNING: Scenario '${name}' finished with exit code ${exit_code}"
    fi

    echo "  Reporte HTML: ${html_out}"
    echo "  CSV export:   ${csv_prefix}_*"
    echo ""
    return $exit_code
}

echo ""
echo "========================================="
echo "  INICIO DE PRUEBAS DE RENDIMIENTO"
echo "  Timestamp: ${TIMESTAMP}"
echo "========================================="

# ─────────────────────────────────────────────
# ESCENARIO 1: WARM-UP
#  Objetivo: Calentar los servicios y verificar
#  que responden correctamente con baja carga.
#  10 usuarios, 2/s, 2 minutos
# ─────────────────────────────────────────────
run_scenario "warmup" 10 2 "2m"

# ─────────────────────────────────────────────
# ESCENARIO 2: PICO
#  Objetivo: Medir el comportamiento bajo carga
#  máxima simulada. Identificar cuellos de botella.
#  50 usuarios, 10/s, 3 minutos
# ─────────────────────────────────────────────
run_scenario "pico" 50 10 "3m"

# ─────────────────────────────────────────────
# ESCENARIO 3: SOSTENIDO
#  Objetivo: Evaluar estabilidad y degradación
#  bajo carga constante durante tiempo prolongado.
#  30 usuarios, 5/s, 5 minutos
# ─────────────────────────────────────────────
run_scenario "sostenido" 30 5 "5m"

echo ""
echo "========================================="
echo "  PRUEBAS COMPLETADAS"
echo "  Reportes: ${REPORT_DIR}"
echo "  CSV:      ${CSV_DIR}"
echo "========================================="
echo ""
echo "═══════════════════════════════════════════════════════════════════════════"
echo "  TABLA DE ANÁLISIS (completar con resultados de los reportes HTML)"
echo "═══════════════════════════════════════════════════════════════════════════"
echo ""
printf "  %-15s | %-8s | %-8s | %-8s | %-8s | %-7s\n" "Escenario" "Usuarios" "Avg(ms)" "P95(ms)" "RPS" "Error%"
printf "  %-15s-|-%-8s-|-%-8s-|-%-8s-|-%-8s-|-%-7s\n" "---------------" "--------" "--------" "--------" "--------" "-------"
printf "  %-15s | %-8s | %-8s | %-8s | %-8s | %-7s\n" "Warm-up" "10" "" "" "" ""
printf "  %-15s | %-8s | %-8s | %-8s | %-8s | %-7s\n" "Pico" "50" "" "" "" ""
printf "  %-15s | %-8s | %-8s | %-8s | %-8s | %-7s\n" "Sostenido" "30" "" "" "" ""
echo ""
echo "  Instrucciones:"
echo "    1. Revisar cada reporte HTML en ${REPORT_DIR}/"
echo "    2. Para cada escenario, anotar:"
echo "       - Tiempo de respuesta promedio (Avg)"
echo "       - Percentil 95 (P95)"
echo "       - Requests por segundo (RPS)"
echo "       - Porcentaje de error (Error%)"
echo "    3. Si Error%% > 5%%, el test falló (exit code 1)"
echo ""
echo "═══════════════════════════════════════════════════════════════════════════"

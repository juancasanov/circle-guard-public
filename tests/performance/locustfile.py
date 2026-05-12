import time
import logging
from locust import HttpUser, task, between, events

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("locust")

TEST_USER = "super_admin"
TEST_PASS = "password"

SERVICE_PORTS = {
    "auth": 8180,
    "identity": 8083,
    "form": 8086,
    "promotion": 8088,
    "dashboard": 8084,
}

SURVEY_PAYLOAD = {
    "hasFever": False,
    "hasCough": False,
    "otherSymptoms": "",
    "exposureDate": None,
    "responses": {},
    "validationStatus": "PENDING",
}


def url_for(service: str, path: str) -> str:
    return f"http://localhost:{SERVICE_PORTS[service]}{path}"


class UsuarioEstandar(HttpUser):
    """
    Usuario estándar (weight=3):
      on_start → login (guarda JWT)
      tareas   → consultar formularios (GET /api/v1/questionnaires)
               → consultar dashboard   (GET /api/v1/analytics/summary)
    Simula un usuario normal que revisa formularios y su dashboard.
    """
    weight = 3
    wait_time = between(1, 3)
    host = f"http://localhost:{SERVICE_PORTS['auth']}"

    def on_start(self):
        resp = self.client.post(
            "/api/v1/auth/login",
            json={"username": TEST_USER, "password": TEST_PASS},
        )
        if resp.status_code != 200:
            logger.error("Login failed for Estandar: %s %s", resp.status_code, resp.text)
            self.token = None
        else:
            data = resp.json()
            self.token = data.get("token")
            self.anonymous_id = data.get("anonymousId")

    @task(2)
    def consultar_formularios(self):
        if not self.token:
            return
        self.client.get(
            url_for("form", "/api/v1/questionnaires"),
            headers={"Authorization": f"Bearer {self.token}"},
            name="/api/v1/questionnaires",
        )

    @task(1)
    def consultar_dashboard(self):
        if not self.token:
            return
        self.client.get(
            url_for("dashboard", "/api/v1/analytics/summary"),
            headers={"Authorization": f"Bearer {self.token}"},
            name="/api/v1/analytics/summary",
        )


class UsuarioAdmin(HttpUser):
    """
    Usuario administrador (weight=1):
      on_start → login (guarda JWT)
      tareas   → crear formulario  (POST /api/v1/surveys)
               → consultar promociones (GET /api/v1/health-status/stats)
    Simula un admin que crea formularios y monitorea promociones.
    """
    weight = 1
    wait_time = between(1, 3)
    host = f"http://localhost:{SERVICE_PORTS['auth']}"

    def on_start(self):
        resp = self.client.post(
            "/api/v1/auth/login",
            json={"username": TEST_USER, "password": TEST_PASS},
        )
        if resp.status_code != 200:
            logger.error("Login failed for Admin: %s %s", resp.status_code, resp.text)
            self.token = None
        else:
            data = resp.json()
            self.token = data.get("token")
            self.anonymous_id = data.get("anonymousId")

    @task(1)
    def crear_formulario(self):
        if not self.token:
            return
        payload = {**SURVEY_PAYLOAD}
        if self.anonymous_id:
            payload["anonymousId"] = self.anonymous_id
        self.client.post(
            url_for("form", "/api/v1/surveys"),
            json=payload,
            headers={"Authorization": f"Bearer {self.token}"},
            name="/api/v1/surveys",
        )

    @task(1)
    def consultar_promociones(self):
        if not self.token:
            return
        self.client.get(
            url_for("promotion", "/api/v1/health-status/stats"),
            headers={"Authorization": f"Bearer {self.token}"},
            name="/api/v1/health-status/stats",
        )


@events.quitting.add_listener
def check_error_threshold(environment, **kw):
    """
    Valida al finalizar la prueba que la tasa de error no supere el 5%.
    Si error_rate > 5%, el script retorna exit code 1.
    """
    total = environment.stats.total
    num_requests = total.num_requests
    num_failures = total.num_failures
    if num_requests == 0:
        return
    error_rate = num_failures / num_requests
    logger.info(
        "=== FINAL STATS: %d requests, %d failures, error rate = %.2f%% ===",
        num_requests, num_failures, error_rate * 100,
    )
    for name, entry in sorted(environment.stats.entries.items()):
        logger.info(
            "  %s: avg=%.1fms p50=%.1fms p95=%.1fms p99=%.1fms rps=%.1f errors=%d",
            name[1] if name[1] else entry.name,
            entry.avg_response_time,
            entry.get_response_time_percentile(0.50) or 0,
            entry.get_response_time_percentile(0.95) or 0,
            entry.get_response_time_percentile(0.99) or 0,
            entry.current_rps,
            entry.num_failures,
        )
    if error_rate > 0.05:
        logger.error(
            "ERROR: Error rate %.2f%% exceeds 5%% threshold. Exiting with code 1.",
            error_rate * 100,
        )
        environment.process_exit_code = 1

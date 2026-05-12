import pytest
import requests
import time
import logging

AUTH_URL = "http://localhost:8180"
IDENTITY_URL = "http://localhost:8083"
FORM_URL = "http://localhost:8086"
PROMOTION_URL = "http://localhost:8088"
DASHBOARD_URL = "http://localhost:8084"

TEST_USER = "super_admin"
TEST_PASS = "password"

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("e2e")

ENDPOINTS = [
    ("Auth", "POST", f"{AUTH_URL}/api/v1/auth/login", {"username": "x", "password": "x"}),
    ("Identity", "POST", f"{IDENTITY_URL}/api/v1/identities/map", {"realIdentity": "health-check"}),
    ("Form", "GET", f"{FORM_URL}/api/v1/questionnaires", None),
    ("Promotion", "GET", f"{PROMOTION_URL}/api/v1/health-status/stats", None),
    ("Dashboard", "GET", f"{DASHBOARD_URL}/api/v1/analytics/summary", None),
]


def _wait_for_service(name, method, url, body, timeout=60, interval=3):
    start = time.time()
    while time.time() - start < timeout:
        try:
            if method == "POST":
                resp = requests.post(url, json=body, timeout=5)
            else:
                resp = requests.get(url, timeout=5)
            if resp.status_code < 500:
                logger.info("  %s OK (%d)", name, resp.status_code)
                return True
            logger.info("  %s unhealthy (%d)", name, resp.status_code)
        except requests.ConnectionError:
            logger.info("  %s waiting... (%ds)", name, int(time.time() - start))
        time.sleep(interval)
    logger.error("  %s FAILED after %ds", name, timeout)
    return False


def pytest_sessionstart(session):
    logger.info("Waiting for services to be healthy...")
    all_healthy = True
    for name, method, url, body in ENDPOINTS:
        if not _wait_for_service(name, method, url, body):
            all_healthy = False
    if not all_healthy:
        pytest.exit("One or more services unreachable. Aborting.", returncode=1)
    logger.info("All services healthy.")


@pytest.fixture(scope="session")
def auth_token():
    resp = requests.post(
        f"{AUTH_URL}/api/v1/auth/login",
        json={"username": TEST_USER, "password": TEST_PASS},
        timeout=10,
    )
    assert resp.status_code == 200, "Login failed: %s" % resp.text
    data = resp.json()
    assert "token" in data
    return data["token"]


@pytest.fixture(scope="session")
def anonymous_id():
    resp = requests.post(
        f"{AUTH_URL}/api/v1/auth/login",
        json={"username": TEST_USER, "password": TEST_PASS},
        timeout=10,
    )
    assert resp.status_code == 200
    return resp.json()["anonymousId"]


@pytest.fixture(scope="session")
def headers(auth_token):
    return {
        "Authorization": "Bearer %s" % auth_token,
        "Content-Type": "application/json",
    }

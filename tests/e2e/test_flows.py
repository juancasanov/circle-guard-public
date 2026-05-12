import uuid
import pytest
import requests
from conftest import AUTH_URL, IDENTITY_URL, FORM_URL, PROMOTION_URL, DASHBOARD_URL, logger


class TestFlujo1RegistroYLogin:
    """Flujo 1: Registro (visitor) y login de usuario con validacion de JWT."""

    def test_login_success_returns_jwt(self):
        """POST /api/v1/auth/login con credenciales validas → JWT."""
        resp = requests.post(
            f"{AUTH_URL}/api/v1/auth/login",
            json={"username": "super_admin", "password": "password"},
            timeout=10,
        )
        assert resp.status_code == 200
        data = resp.json()
        assert "token" in data
        assert "type" in data
        assert "anonymousId" in data
        assert data["type"] == "Bearer"
        assert isinstance(data["token"], str)
        assert len(data["token"]) > 20
        assert isinstance(data["anonymousId"], str)
        parts = data["token"].split(".")
        assert len(parts) == 3, "token does not look like a JWT (expected 3 parts)"

    def test_login_invalid_credentials_returns_401(self):
        """POST /api/v1/auth/login con credenciales invalidas → 401."""
        resp = requests.post(
            f"{AUTH_URL}/api/v1/auth/login",
            json={"username": "no_existe", "password": "mala"},
            timeout=10,
        )
        assert resp.status_code == 401

    def test_visitor_registration_and_handoff(self):
        """Flujo de registro visitante: identity + handoff → token JWT."""
        visitor_resp = requests.post(
            f"{IDENTITY_URL}/api/v1/identities/visitor",
            json={
                "name": "E2E Test Visitor",
                "email": "visitor_%s@test.com" % uuid.uuid4().hex[:8],
                "reason_for_visit": "E2E testing",
            },
            timeout=10,
        )
        assert visitor_resp.status_code == 200
        visitor_data = visitor_resp.json()
        assert "anonymousId" in visitor_data
        anon_id = visitor_data["anonymousId"]

        handoff_resp = requests.post(
            f"{AUTH_URL}/api/v1/auth/visitor/handoff",
            json={"anonymousId": anon_id},
            timeout=10,
        )
        assert handoff_resp.status_code == 200
        handoff_data = handoff_resp.json()
        assert "token" in handoff_data
        assert "handoffPayload" in handoff_data
        assert isinstance(handoff_data["token"], str)
        assert len(handoff_data["token"]) > 20


class TestFlujo2LoginYFormularios:
    """Flujo 2: Login y acceso a listado de formularios."""

    def test_list_questionnaires_with_jwt(self, headers):
        """GET /api/v1/questionnaires con JWT → lista de formularios."""
        resp = requests.get(
            f"{FORM_URL}/api/v1/questionnaires",
            headers=headers,
            timeout=10,
        )
        assert resp.status_code == 200
        data = resp.json()
        assert isinstance(data, list)

    def test_active_questionnaire_structure(self, headers):
        """GET /api/v1/questionnaires/active con JWT."""
        resp = requests.get(
            f"{FORM_URL}/api/v1/questionnaires/active",
            headers=headers,
            timeout=10,
        )
        assert resp.status_code in (200, 404)
        if resp.status_code == 200:
            data = resp.json()
            assert "id" in data
            assert "title" in data
            assert "version" in data
            assert isinstance(data["id"], str)
            assert isinstance(data["title"], str)
            assert isinstance(data["version"], int)

    def test_questionnaires_requires_auth(self):
        """GET /api/v1/questionnaires sin JWT → 401/403."""
        resp = requests.get(
            f"{FORM_URL}/api/v1/questionnaires",
            timeout=10,
        )
        assert resp.status_code in (401, 403)


class TestFlujo3CrearFormularioYDashboard:
    """Flujo 3: Crear health survey y verificar en dashboard."""

    _created_survey_ids = []

    def test_submit_health_survey(self, headers, anonymous_id):
        """POST /api/v1/surveys → crear health survey."""
        payload = {
            "anonymousId": anonymous_id,
            "hasFever": False,
            "hasCough": False,
            "otherSymptoms": "",
            "responses": {
                "sintomas": "ninguno",
                "contacto": "no",
            },
        }
        resp = requests.post(
            f"{FORM_URL}/api/v1/surveys",
            json=payload,
            headers=headers,
            timeout=10,
        )
        assert resp.status_code == 200
        data = resp.json()
        assert "id" in data
        assert "anonymousId" in data
        assert data["anonymousId"] == anonymous_id
        assert isinstance(data["id"], str)
        uuid.UUID(data["id"])
        assert isinstance(data["hasFever"], bool)
        assert data["hasFever"] is False
        self.__class__._created_survey_ids.append(data["id"])

    def test_dashboard_summary_structure(self, headers):
        """GET /api/v1/analytics/summary → validar estructura."""
        resp = requests.get(
            f"{DASHBOARD_URL}/api/v1/analytics/summary",
            headers=headers,
            timeout=10,
        )
        assert resp.status_code == 200
        data = resp.json()
        assert isinstance(data, dict)

    def test_dashboard_health_board_structure(self, headers):
        """GET /api/v1/analytics/health-board → validar estructura."""
        resp = requests.get(
            f"{DASHBOARD_URL}/api/v1/analytics/health-board",
            headers=headers,
            timeout=10,
        )
        assert resp.status_code == 200
        data = resp.json()
        assert isinstance(data, dict)

    def teardown_method(self, method):
        if self.__class__._created_survey_ids:
            for sid in self.__class__._created_survey_ids:
                logger.info("  [CLEANUP] Survey %s - no DELETE endpoint, log for manual cleanup", sid)
            self.__class__._created_survey_ids.clear()


class TestFlujo4LoginYPromociones:
    """Flujo 4: Login y consulta de health-status (promociones)."""

    def test_health_status_stats_structure(self, headers):
        """GET /api/v1/health-status/stats → validar campos raiz."""
        resp = requests.get(
            f"{PROMOTION_URL}/api/v1/health-status/stats",
            headers=headers,
            timeout=10,
        )
        assert resp.status_code == 200
        data = resp.json()
        assert "totalUsers" in data
        assert "timestamp" in data
        assert isinstance(data["totalUsers"], int)
        assert data["totalUsers"] >= 0
        assert isinstance(data["timestamp"], str)

    def test_health_status_stats_data_types(self, headers):
        """Validar tipos de datos en health-status/stats."""
        resp = requests.get(
            f"{PROMOTION_URL}/api/v1/health-status/stats",
            headers=headers,
            timeout=10,
        )
        data = resp.json()
        for key, value in data.items():
            if key.endswith("Count"):
                assert isinstance(value, int), "%s should be int, got %s" % (key, type(value).__name__)
                assert value >= 0, "%s should be >= 0, got %d" % (key, value)

    def test_promotion_requires_auth(self):
        """Endpoint de promocion sin JWT → 401/403."""
        resp = requests.get(
            f"{PROMOTION_URL}/api/v1/health-status/stats",
            timeout=10,
        )
        assert resp.status_code in (401, 403)


class TestFlujo5Identidad:
    """Flujo 5: Verificacion de identidad via identity service."""

    _created_identities = []

    def test_map_identity_returns_anonymous_id(self, headers):
        """POST /api/v1/identities/map → anonymousId."""
        real = "e2e_map_%s@test.com" % uuid.uuid4().hex[:8]
        resp = requests.post(
            f"{IDENTITY_URL}/api/v1/identities/map",
            json={"realIdentity": real},
            headers=headers,
            timeout=10,
        )
        assert resp.status_code == 200
        data = resp.json()
        assert "anonymousId" in data
        assert isinstance(data["anonymousId"], str)
        parsed = uuid.UUID(data["anonymousId"])
        assert str(parsed) == data["anonymousId"]
        self.__class__._created_identities.append((real, data["anonymousId"]))

    def test_map_identity_is_idempotent(self, headers):
        """Misma identidad → mismo anonymousId."""
        real = "e2e_idem_%s@test.com" % uuid.uuid4().hex[:8]
        resp1 = requests.post(
            f"{IDENTITY_URL}/api/v1/identities/map",
            json={"realIdentity": real},
            headers=headers,
            timeout=10,
        )
        resp2 = requests.post(
            f"{IDENTITY_URL}/api/v1/identities/map",
            json={"realIdentity": real},
            headers=headers,
            timeout=10,
        )
        assert resp1.status_code == 200
        assert resp2.status_code == 200
        assert resp1.json()["anonymousId"] == resp2.json()["anonymousId"]

    def test_register_visitor_returns_anonymous_id(self, headers):
        """POST /api/v1/identities/visitor → anonymousId."""
        resp = requests.post(
            f"{IDENTITY_URL}/api/v1/identities/visitor",
            json={
                "name": "E2E Visitor",
                "email": "e2e_visitor_%s@test.com" % uuid.uuid4().hex[:8],
                "reason_for_visit": "testing",
            },
            headers=headers,
            timeout=10,
        )
        assert resp.status_code == 200
        data = resp.json()
        assert "anonymousId" in data
        parsed = uuid.UUID(data["anonymousId"])
        assert str(parsed) == data["anonymousId"]
        self.__class__._created_identities.append(("visitor", data["anonymousId"]))

    def test_map_identity_missing_field(self, headers):
        """POST /api/v1/identities/map sin body valido."""
        resp = requests.post(
            f"{IDENTITY_URL}/api/v1/identities/map",
            json={},
            headers=headers,
            timeout=10,
        )
        assert resp.status_code in (200, 400)

    def teardown_method(self, method):
        if self.__class__._created_identities:
            logger.info("  [CLEANUP] %d identities created (no DELETE endpoint)", len(self.__class__._created_identities))
            self.__class__._created_identities.clear()

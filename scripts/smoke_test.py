#!/usr/bin/env python3
"""End-to-end smoke test using only Python's standard library."""

from __future__ import annotations

import hashlib
import hmac
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE_URL = os.getenv("BASE_URL", "http://localhost:8080").rstrip("/")
API_SECRET = os.getenv(
    "API_HMAC_SECRET",
    "local-api-secret-change-me-32-characters-minimum",
)
SMS_SECRET = os.getenv(
    "SMS_FORWARDER_HMAC_SECRET",
    "local-sms-secret-change-me-32-characters-minimum",
)


def hmac_hex(secret: str, payload: bytes) -> str:
    return hmac.new(secret.encode(), payload, hashlib.sha256).hexdigest()


def api_headers(method: str, target: str, body: bytes) -> dict[str, str]:
    timestamp = str(int(time.time()))
    nonce = str(uuid.uuid4())
    body_hash = hashlib.sha256(body).hexdigest()
    canonical = f"{method.upper()}\n{target}\n{timestamp}\n{nonce}\n{body_hash}".encode()
    return {
        "Accept": "application/json",
        "X-Timestamp": timestamp,
        "X-Nonce": nonce,
        "X-Signature": hmac_hex(API_SECRET, canonical),
    }


def request(method: str, target: str, body: bytes = b"", raw_sms: bool = False):
    headers = {"Accept": "application/json"}
    if body:
        headers["Content-Type"] = "application/json"
    if raw_sms:
        headers["X-Signature"] = hmac_hex(SMS_SECRET, body)
    else:
        headers.update(api_headers(method, target, body))

    req = urllib.request.Request(
        BASE_URL + target,
        data=body if body else None,
        headers=headers,
        method=method,
    )
    with urllib.request.urlopen(req, timeout=10) as response:
        response_body = response.read()
        return response.status, json.loads(response_body) if response_body else None


def wait_until_ready() -> None:
    # There is intentionally no unsigned health endpoint. A signed request that
    # reaches the API proves the app is ready to accept signed requests.
    deadline = time.time() + 120
    while time.time() < deadline:
        try:
            status, payload = request(
                "POST",
                "/api/v1/verifications",
                b'{"phoneNumber":"081234567890"}',
            )
            if status == 201:
                run_flow(payload)
                return
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            time.sleep(2)
    raise RuntimeError("Application did not become ready within 120 seconds")


def run_flow(created: dict) -> None:
    first_verification_id = created["verificationId"]
    assert created["status"] == "PENDING", created

    # A replacement for the same phone must expire the previous PENDING row.
    create_body = b'{"phoneNumber":"081234567890"}'
    status, replacement = request(
        "POST",
        "/api/v1/verifications",
        create_body,
    )
    assert status == 201, replacement
    assert replacement["verificationId"] != first_verification_id, replacement

    old_status_target = f"/api/v1/verifications/{first_verification_id}/status"
    status, old_status = request("GET", old_status_target)
    assert status == 200, old_status
    assert old_status["status"] == "EXPIRED", old_status

    verification_id = replacement["verificationId"]
    code = replacement["code"]
    assert replacement["status"] == "PENDING", replacement

    sms_payload = json.dumps(
        {
            "from": "+6281234567890",
            "text": f"VERIF {code}",
            "sentStamp": int(time.time() * 1000),
            "receivedStamp": int(time.time() * 1000),
            "sim": "CI-SIM",
        },
        separators=(",", ":"),
    ).encode()
    status, incoming = request(
        "POST",
        "/internal/sms/incoming",
        sms_payload,
        raw_sms=True,
    )
    assert status == 200, incoming
    assert incoming["matchStatus"] == "MATCHED", incoming
    assert incoming["duplicate"] is False, incoming

    # Android retries must be idempotent for an identical payload.
    status, duplicate = request(
        "POST",
        "/internal/sms/incoming",
        sms_payload,
        raw_sms=True,
    )
    assert status == 200, duplicate
    assert duplicate["duplicate"] is True, duplicate
    assert duplicate["smsId"] == incoming["smsId"], duplicate

    target = f"/api/v1/verifications/{verification_id}/status"
    status, checked = request("GET", target)
    assert status == 200, checked
    assert checked["status"] == "VERIFIED", checked

    target = f"/api/v1/verifications/{verification_id}/sms"
    status, sms_list = request("GET", target)
    assert status == 200, sms_list
    assert sms_list["count"] == 1, sms_list
    assert sms_list["items"][0]["matchStatus"] == "MATCHED", sms_list

    debug_target = "/api/v1/debug/storage"
    status, debug_state = request("GET", debug_target)
    assert status == 200, debug_state
    assert debug_state["verificationsCount"] >= 2, debug_state
    assert debug_state["incomingSmsCount"] == 1, debug_state

    print("Smoke test passed: create -> inbound SMS -> status -> SMS list -> debug storage")


if __name__ == "__main__":
    try:
        wait_until_ready()
    except Exception as exc:  # noqa: BLE001 - CLI entry point
        print(f"Smoke test failed: {exc}", file=sys.stderr)
        sys.exit(1)

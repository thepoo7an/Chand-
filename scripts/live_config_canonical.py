#!/usr/bin/env python3
"""Canonical form of `chand-live-config.json`, shared by the signing and verification tools.

The Android verifier (`LiveConfigSignature.canonicalPayload`) implements exactly these rules, so
BOTH sides must stay in sync:

1. Parse the JSON text. Numbers keep their literal source text (`4.0` stays `4.0`, never `4`).
2. Drop the top-level `signature` and `keyId` members — they carry the signature itself.
3. Emit compact JSON: object keys sorted (ASCII keys, ascending code unit), no whitespace,
   strings escaped minimally (`"`, `\\`, and C0 controls only), every other character as raw UTF-8.
4. UTF-8 encode. Those bytes are the signed payload.

Formatting of the source file (indentation, key order, escaped vs raw unicode) therefore does not
change the signature, while any semantic edit does.

Only the Python standard library plus the `openssl` binary are required.
"""

from __future__ import annotations

import json
import subprocess
from typing import Any

SIGNATURE_FIELD = "signature"
KEY_ID_FIELD = "keyId"
RESERVED_FIELDS = (SIGNATURE_FIELD, KEY_ID_FIELD)


class _RawNumber(str):
    """A JSON number kept as its literal source text."""


def _load(text: str) -> Any:
    return json.loads(text, parse_float=_RawNumber, parse_int=_RawNumber)


_ESCAPES = {
    '"': '\\"',
    "\\": "\\\\",
    "\b": "\\b",
    "\f": "\\f",
    "\n": "\\n",
    "\r": "\\r",
    "\t": "\\t",
}


def _encode_string(value: str) -> str:
    out = ['"']
    for ch in value:
        escape = _ESCAPES.get(ch)
        if escape is not None:
            out.append(escape)
        elif ord(ch) < 0x20:
            out.append("\\u%04x" % ord(ch))
        else:
            out.append(ch)
    out.append('"')
    return "".join(out)


def _encode(value: Any) -> str:
    if isinstance(value, dict):
        items = sorted(value.items(), key=lambda item: item[0])
        return "{" + ",".join(_encode_string(k) + ":" + _encode(v) for k, v in items) + "}"
    if isinstance(value, list):
        return "[" + ",".join(_encode(item) for item in value) + "]"
    if isinstance(value, _RawNumber):
        return str(value)
    if value is True:
        return "true"
    if value is False:
        return "false"
    if value is None:
        return "null"
    if isinstance(value, str):
        return _encode_string(value)
    raise ValueError(f"unsupported JSON value: {type(value)!r}")


def canonical_payload(raw_text: str) -> bytes:
    """Return the exact bytes that must be signed/verified for this config text."""
    root = _load(raw_text)
    if not isinstance(root, dict):
        raise ValueError("live config must be a JSON object")
    stripped = {k: v for k, v in root.items() if k not in RESERVED_FIELDS}
    return _encode(stripped).encode("utf-8")


def read_signature(raw_text: str) -> tuple[str | None, str | None]:
    """Return `(key_id, signature_base64)` from the config text, if present."""
    root = _load(raw_text)
    if not isinstance(root, dict):
        return None, None
    key_id = root.get(KEY_ID_FIELD)
    signature = root.get(SIGNATURE_FIELD)
    if not isinstance(key_id, str) or not isinstance(signature, str):
        return None, None
    key_id = key_id.strip()
    signature = signature.strip()
    if not key_id or not signature:
        return None, None
    return key_id, signature


def sign_payload(payload: bytes, private_key_pem: str) -> bytes:
    """ECDSA P-256 / SHA-256 signature (DER encoded), produced by openssl."""
    result = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", private_key_pem],
        input=payload,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(
            "openssl failed to sign the payload: "
            + result.stderr.decode("utf-8", "replace").strip()
        )
    return result.stdout


def verify_with_files(
    payload: bytes,
    signature_der_path: str,
    public_key_der_path: str,
) -> bool:
    result = subprocess.run(
        ["openssl", "dgst", "-sha256", "-verify", public_key_der_path, "-signature", signature_der_path],
        input=payload,
        capture_output=True,
        check=False,
    )
    return result.returncode == 0


def public_key_der_from_pem(public_key_pem: str) -> bytes:
    result = subprocess.run(
        ["openssl", "pkey", "-pubin", "-outform", "DER"],
        input=public_key_pem.encode("utf-8"),
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(
            "openssl failed to read the public key: "
            + result.stderr.decode("utf-8", "replace").strip()
        )
    return result.stdout


def dump_config(config: dict, *, indent: int = 2) -> str:
    """Deterministic, human friendly serialization used for the committed config file."""
    return json.dumps(config, indent=indent, ensure_ascii=False) + "\n"


def load_config(raw_text: str) -> dict:
    root = json.loads(raw_text)
    if not isinstance(root, dict):
        raise ValueError("live config must be a JSON object")
    return root

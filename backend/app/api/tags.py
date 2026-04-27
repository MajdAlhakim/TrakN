"""
backend/app/api/tags.py

Tag management endpoints.

GET    /api/v1/tags                  → list all registered tags
GET    /api/v1/tags/{tag_id}         → get a single tag's info
PATCH  /api/v1/tags/{tag_id}/name    → rename a tag (e.g. "Ahmad's Bag")
GET    /api/v1/tags/{tag_id}/qr      → return a QR code PNG for this tag

The QR code encodes the deep-link:  trakn://track/<tag_id>
When a parent scans it, the TRAKN app opens directly on the tracking screen
for that tag — no manual ID entry needed.
"""

import io
import os
from typing import Any

import qrcode
import qrcode.image.pil
from fastapi import APIRouter, Header, HTTPException, status
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

from ..fusion.tag_registry import registry

router = APIRouter()


def _check_key(x_api_key: str | None) -> None:
    expected = os.environ.get("GATEWAY_API_KEY", "")
    if not x_api_key or x_api_key != expected:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing X-API-Key",
        )


class RenameRequest(BaseModel):
    name: str


# ── Endpoints ─────────────────────────────────────────────────────────────────

@router.get("/api/v1/tags", status_code=status.HTTP_200_OK)
async def list_tags(
    x_api_key: str | None = Header(default=None),
) -> dict[str, Any]:
    """Return all registered tags."""
    _check_key(x_api_key)
    return {"tags": registry.all()}


@router.get("/api/v1/tags/{tag_id}", status_code=status.HTTP_200_OK)
async def get_tag(
    tag_id: str,
    x_api_key: str | None = Header(default=None),
) -> dict[str, Any]:
    """Return info for a single tag."""
    _check_key(x_api_key)
    rec = registry.get(tag_id)
    if rec is None:
        raise HTTPException(status_code=404, detail=f"Tag {tag_id!r} not found")
    return rec


@router.patch("/api/v1/tags/{tag_id}/name", status_code=status.HTTP_200_OK)
async def rename_tag(
    tag_id: str,
    body: RenameRequest,
    x_api_key: str | None = Header(default=None),
) -> dict[str, str]:
    """Give a tag a human-readable name (e.g. 'Ahmad's Bag')."""
    _check_key(x_api_key)
    if not registry.set_name(tag_id, body.name.strip()):
        raise HTTPException(status_code=404, detail=f"Tag {tag_id!r} not found")
    return {"status": "ok", "tag_id": tag_id, "name": body.name.strip()}


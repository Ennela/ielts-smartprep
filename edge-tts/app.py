import logging
import os
import re
import threading
import time
from collections import defaultdict, deque

import edge_tts
from fastapi import FastAPI, Query, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse


logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("edge-tts-api")


def get_int_env(name: str, default: int) -> int:
    value = int(os.getenv(name, str(default)))
    if value <= 0:
        raise RuntimeError(f"{name} must be greater than zero")
    return value


ENVIRONMENT = os.getenv("EDGE_TTS_ENV", "development").strip().lower()
MAX_TEXT_LENGTH = get_int_env("EDGE_TTS_MAX_TEXT_LENGTH", 5000)
MAX_REQUEST_BYTES = get_int_env("EDGE_TTS_MAX_REQUEST_BYTES", 20000)
RATE_LIMIT_REQUESTS = get_int_env("EDGE_TTS_RATE_LIMIT_REQUESTS", 30)
RATE_LIMIT_WINDOW_SECONDS = get_int_env("EDGE_TTS_RATE_LIMIT_WINDOW_SECONDS", 60)

configured_origins = os.getenv("EDGE_TTS_CORS_ALLOWED_ORIGINS", "").strip()
if configured_origins:
    ALLOWED_ORIGINS = [origin.strip() for origin in configured_origins.split(",") if origin.strip()]
elif ENVIRONMENT == "production":
    ALLOWED_ORIGINS = []
else:
    ALLOWED_ORIGINS = ["http://localhost", "http://localhost:3000", "http://localhost:5173"]

if ENVIRONMENT == "production" and "*" in ALLOWED_ORIGINS:
    raise RuntimeError("EDGE_TTS_CORS_ALLOWED_ORIGINS cannot contain '*' in production")

configured_voices = os.getenv("EDGE_TTS_ALLOWED_VOICES", "").strip()
ALLOWED_VOICES = {
    voice.strip() for voice in configured_voices.split(",") if voice.strip()
}
VOICE_PATTERN = re.compile(r"^[a-z]{2,3}-[A-Z]{2}-[A-Za-z0-9]+Neural$")
RATE_PATTERN = re.compile(r"^([+-])(\d{1,3})%$")

app = FastAPI(title="Edge TTS API Wrapper")

request_history = defaultdict(deque)
request_history_lock = threading.Lock()


def error_response(status_code: int, code: str, message: str, **headers) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={"error": {"code": code, "message": message}},
        headers=headers or None,
    )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(_request: Request, exc: RequestValidationError):
    details = [
        {
            "field": ".".join(str(part) for part in error["loc"] if part not in {"query", "body"}),
            "message": error["msg"],
        }
        for error in exc.errors()
    ]
    return JSONResponse(
        status_code=422,
        content={
            "error": {
                "code": "invalid_request",
                "message": "One or more request parameters are invalid",
                "details": details,
            }
        },
    )


@app.middleware("http")
async def protect_service(request: Request, call_next):
    query_size = len(request.scope.get("query_string", b""))
    content_length = request.headers.get("content-length")
    if query_size > MAX_REQUEST_BYTES or (
        content_length is not None and content_length.isdigit() and int(content_length) > MAX_REQUEST_BYTES
    ):
        return error_response(
            413,
            "request_too_large",
            f"Request must not exceed {MAX_REQUEST_BYTES} bytes",
        )

    client_ip = request.client.host if request.client else "unknown"
    now = time.monotonic()
    cutoff = now - RATE_LIMIT_WINDOW_SECONDS
    with request_history_lock:
        history = request_history[client_ip]
        while history and history[0] <= cutoff:
            history.popleft()
        if len(history) >= RATE_LIMIT_REQUESTS:
            retry_after = max(1, int(history[0] + RATE_LIMIT_WINDOW_SECONDS - now) + 1)
            return error_response(
                429,
                "rate_limit_exceeded",
                "Too many requests; please try again later",
                **{"Retry-After": str(retry_after)},
            )
        history.append(now)

    return await call_next(request)


app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=False,
    allow_methods=["GET"],
    allow_headers=["Accept", "Content-Type"],
)


@app.get("/synthesize")
async def synthesize(
    text: str = Query(..., min_length=1, max_length=MAX_TEXT_LENGTH, description="Text to synthesize"),
    voice: str = Query("en-US-AriaNeural", max_length=100, description="Voice to use"),
    rate: str = Query("+0%", max_length=5, description="Rate adjustment e.g. +10% or -10%"),
):
    if not text.strip():
        return error_response(400, "invalid_text", "Text must not be blank")
    if not VOICE_PATTERN.fullmatch(voice):
        return error_response(400, "invalid_voice", "Voice has an invalid format")
    if ALLOWED_VOICES and voice not in ALLOWED_VOICES:
        return error_response(400, "invalid_voice", "Voice is not allowed")

    rate_match = RATE_PATTERN.fullmatch(rate)
    if not rate_match:
        return error_response(400, "invalid_rate", "Rate must use a format such as +10% or -10%")
    rate_value = int(rate_match.group(2)) * (1 if rate_match.group(1) == "+" else -1)
    if not -50 <= rate_value <= 50:
        return error_response(400, "invalid_rate", "Rate must be between -50% and +50%")

    logger.info("Synthesis requested: voice=%s, rate=%s, text_length=%d", voice, rate, len(text))
    try:
        communicate = edge_tts.Communicate(text, voice, rate=rate)
        mp3_data = bytearray()
        async for chunk in communicate.stream():
            if chunk["type"] == "audio":
                mp3_data.extend(chunk["data"])

        logger.info("Synthesis successful: output_size=%d bytes", len(mp3_data))
        return Response(content=bytes(mp3_data), media_type="audio/mpeg")
    except Exception as exc:
        logger.error("Synthesis failed: error_type=%s", type(exc).__name__)
        return error_response(502, "synthesis_failed", "Text-to-speech synthesis failed")

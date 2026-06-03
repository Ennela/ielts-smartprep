from fastapi import FastAPI, Query, Response
from fastapi.middleware.cors import CORSMiddleware
import edge_tts
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("edge-tts-api")

app = FastAPI(title="Edge TTS API Wrapper")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/synthesize")
async def synthesize(
    text: str = Query(..., description="Text to synthesize"),
    voice: str = Query("en-US-AriaNeural", description="Voice to use"),
    rate: str = Query("+0%", description="Rate adjustment e.g. +10% or -10%")
):
    logger.info(f"Received request: voice={voice}, rate={rate}, text_len={len(text)}")
    try:
        communicate = edge_tts.Communicate(text, voice, rate=rate)
        mp3_data = bytearray()
        async for chunk in communicate.stream():
            if chunk["type"] == "audio":
                mp3_data.extend(chunk["data"])
        
        logger.info(f"Synthesis successful. Output size: {len(mp3_data)} bytes")
        return Response(content=bytes(mp3_data), media_type="audio/mpeg")
    except Exception as e:
        logger.error(f"Synthesis failed: {e}")
        return Response(content=str(e), status_code=500, media_type="text/plain")

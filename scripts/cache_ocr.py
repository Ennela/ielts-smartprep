import fitz
import asyncio
import json
import os
import winrt.windows.graphics.imaging as imaging
import winrt.windows.media.ocr as ocr
import winrt.windows.storage.streams as streams
from PIL import Image
import io

pdf_path = r"d:\sources\repos\proj\IELST\IETLS 19 cam\CAMBRIDGE 19\Cambridge 19.pdf"
cache_path = r"d:\sources\repos\proj\IELST\ielts-smartprep\scripts\cam19_ocr_cache.json"

def pillow_image_to_software_bitmap(pil_img):
    rgba_img = pil_img.convert("RGBA")
    width, height = rgba_img.size
    writer = streams.DataWriter()
    writer.write_bytes(rgba_img.tobytes())
    bitmap = imaging.SoftwareBitmap(
        imaging.BitmapPixelFormat.RGBA8,
        width,
        height
    )
    bitmap.copy_from_buffer(writer.detach_buffer())
    return bitmap

async def ocr_page(page, engine):
    try:
        pix = page.get_pixmap(dpi=150)
        img_data = pix.tobytes("png")
        pil_img = Image.open(io.BytesIO(img_data))
        bitmap = pillow_image_to_software_bitmap(pil_img)
        result = await engine.recognize_async(bitmap)
        return result.text
    except Exception as e:
        print(f"Error on page {page.number + 1}: {e}")
        return ""

async def main():
    if os.path.exists(cache_path):
        print(f"Cache file already exists at {cache_path}.")
        return

    doc = fitz.open(pdf_path)
    print(f"Opened PDF with {len(doc)} pages.")
    
    engine = ocr.OcrEngine.try_create_from_user_profile_languages()
    if not engine:
        print("Failed to create OCR engine.")
        return
        
    print("OCR Engine created successfully. Starting OCR on all pages...")
    
    cache_data = {}
    for i in range(len(doc)):
        page = doc.load_page(i)
        text = await ocr_page(page, engine)
        cache_data[str(i + 1)] = text
        if (i + 1) % 10 == 0 or (i + 1) == len(doc):
            print(f"Processed page {i + 1}/{len(doc)}")
            
    with open(cache_path, "w", encoding="utf-8") as f:
        json.dump(cache_data, f, ensure_ascii=False, indent=2)
        
    print(f"Saved OCR text for all {len(doc)} pages to {cache_path}")

if __name__ == "__main__":
    asyncio.run(main())

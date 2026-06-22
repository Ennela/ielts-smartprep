import fitz
import asyncio
import winrt.windows.graphics.imaging as imaging
import winrt.windows.media.ocr as ocr
import winrt.windows.storage.streams as streams
from PIL import Image
import io

pdf_path = r"d:\sources\repos\proj\IELST\IETLS 19 cam\CAMBRIDGE 19\Cambridge 19.pdf"

def pillow_image_to_software_bitmap(pil_img):
    # Convert Pillow image to RGBA
    rgba_img = pil_img.convert("RGBA")
    width, height = rgba_img.size
    
    # Create DataWriter
    writer = streams.DataWriter()
    writer.write_bytes(rgba_img.tobytes())
    
    # Create SoftwareBitmap
    bitmap = imaging.SoftwareBitmap(
        imaging.BitmapPixelFormat.RGBA8,
        width,
        height
    )
    bitmap.copy_from_buffer(writer.detach_buffer())
    return bitmap

async def ocr_page(page, engine):
    # Render page to image bytes using PyMuPDF (fitz)
    pix = page.get_pixmap(dpi=150)
    img_data = pix.tobytes("png")
    
    # Load into Pillow
    pil_img = Image.open(io.BytesIO(img_data))
    
    # Convert to software bitmap
    bitmap = pillow_image_to_software_bitmap(pil_img)
    
    # Perform OCR
    result = await engine.recognize_async(bitmap)
    return result.text

async def main():
    doc = fitz.open(pdf_path)
    print(f"Opened PDF with {len(doc)} pages.")
    
    engine = ocr.OcrEngine.try_create_from_user_profile_languages()
    if not engine:
        print("Failed to create OCR engine.")
        return
        
    print("OCR Engine created successfully.")
    
    # OCR the first 15 pages as a test and look for headers
    for i in range(min(15, len(doc))):
        page = doc.load_page(i)
        text = await ocr_page(page, engine)
        print(f"=== Page {i+1} (Length: {len(text)}) ===")
        # Print first 3 lines
        lines = [l.strip() for l in text.split("\n") if l.strip()]
        if lines:
            print("  Header lines:")
            for line in lines[:3]:
                print(f"    {line}")
        else:
            print("  Empty page")

if __name__ == "__main__":
    asyncio.run(main())

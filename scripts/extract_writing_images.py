import fitz
import io
from PIL import Image
import os

pdf_path = r"d:\sources\repos\proj\IELST\IETLS 19 cam\CAMBRIDGE 19\Cambridge 19.pdf"
output_dir = r"d:\sources\repos\proj\IELST\ielts-smartprep\scripts\extracted_images"
os.makedirs(output_dir, exist_ok=True)

doc = fitz.open(pdf_path)

writing_task1_pages = {
    1: 31,
    2: 55,
    3: 75,
    4: 95
}

for test_num, page_num in writing_task1_pages.items():
    page_idx = page_num - 1
    page = doc.load_page(page_idx)
    image_list = page.get_images(full=True)
    print(f"Test {test_num} (Page {page_num}) has {len(image_list)} images.")
    
    for img_idx, img_info in enumerate(image_list):
        xref = img_info[0]
        base_image = doc.extract_image(xref)
        image_bytes = base_image["image"]
        image_ext = base_image["ext"]
        
        pil_img = Image.open(io.BytesIO(image_bytes))
        out_path = os.path.join(output_dir, f"test{test_num}_task1_img{img_idx+1}.{image_ext}")
        pil_img.save(out_path)
        print(f"  Saved image {img_idx+1} to {out_path} (size: {pil_img.size})")

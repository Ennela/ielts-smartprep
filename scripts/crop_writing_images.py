import fitz
import io
from PIL import Image
import os

pdf_path = r"d:\sources\repos\proj\IELST\IETLS 19 cam\CAMBRIDGE 19\Cambridge 19.pdf"
output_dir = r"d:\sources\repos\proj\IELST\ielts-smartprep\scripts\extracted_images"
os.makedirs(output_dir, exist_ok=True)

doc = fitz.open(pdf_path)

# 1-based page numbers for Writing Task 1
writing_task1_pages = {
    1: 29,
    2: 51,
    3: 74,
    4: 95
}

# Standard crop boxes for 1433 x 2024 scanned page images
# We want to remove top header/prompt and bottom footer page number.
# format: (left, top, right, bottom)
crop_boxes = {
    1: (40, 380, 1390, 1900),  # Test 1 Graph
    2: (40, 380, 1390, 1900),  # Test 2 Maps
    3: (40, 380, 1390, 1900),  # Test 3 Diagram
    4: (40, 380, 1390, 1900)   # Test 4 Pie chart
}

for test_num, page_num in writing_task1_pages.items():
    page_idx = page_num - 1
    page = doc.load_page(page_idx)
    
    # Since these are scanned pages, let's render the page at 150 DPI for high quality
    pix = page.get_pixmap(dpi=150)
    img_data = pix.tobytes("png")
    pil_img = Image.open(io.BytesIO(img_data))
    
    width, height = pil_img.size
    print(f"Test {test_num} Page size: {width} x {height}")
    
    # Scale the crop box based on rendered size (since the template crop box is for 1433 x 2024)
    # Scale factors:
    scale_x = width / 1433.0
    scale_y = height / 2024.0
    
    box_1433 = crop_boxes[test_num]
    scaled_box = (
        int(box_1433[0] * scale_x),
        int(box_1433[1] * scale_y),
        int(box_1433[2] * scale_x),
        int(box_1433[3] * scale_y)
    )
    
    cropped_img = pil_img.crop(scaled_box)
    out_path = os.path.join(output_dir, f"cam19_test{test_num}_task1.jpeg")
    
    # Save as JPEG with high quality
    cropped_img.convert("RGB").save(out_path, "JPEG", quality=90)
    print(f"  Cropped and saved to {out_path} (size: {cropped_img.size})")

doc.close()

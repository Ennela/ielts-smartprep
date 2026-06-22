import fitz

pdf_path = r"d:\sources\repos\proj\IELST\IETLS 19 cam\CAMBRIDGE 19\Cambridge 19.pdf"
doc = fitz.open(pdf_path)

print("Page text length check:")
has_text = False
for i in range(min(10, len(doc))):
    page = doc.load_page(i)
    text = page.get_text()
    print(f"Page {i+1}: text length = {len(text)}")
    if len(text) > 0:
        has_text = True
        print(f"Sample text from page {i+1}:")
        print(repr(text[:300]))
        print("-" * 40)

if not has_text:
    print("Warning: No text found in first 10 pages. Checking if there are images on these pages.")
    for i in range(min(5, len(doc))):
        page = doc.load_page(i)
        image_list = page.get_images()
        print(f"Page {i+1} has {len(image_list)} images.")

import os

pdf_path = r"d:\sources\repos\proj\IELST\IETLS 19 cam\CAMBRIDGE 19\Cambridge 19.pdf"
audio_dir = r"d:\sources\repos\proj\IELST\IETLS 19 cam\CAMBRIDGE 19\Audio cam 19"
image_dir = r"d:\sources\repos\proj\IELST\ielts-smartprep\scripts\extracted_images"

print("==========================================================")
print("            CAMBRIDGE 19 RESOURCE SCAN REPORT             ")
print("==========================================================")

# Check PDF
pdf_exists = os.path.exists(pdf_path)
pdf_size = os.path.getsize(pdf_path) if pdf_exists else 0
print(f"PDF File: {os.path.basename(pdf_path)}")
print(f"  Path:   {pdf_path}")
print(f"  Status: {'FOUND' if pdf_exists else 'NOT FOUND'} ({pdf_size / (1024*1024):.2f} MB)")
print("-" * 58)

# Check Audios
print("Listening Audio Files:")
audio_status = []
for test in range(1, 5):
    for part in range(1, 5):
        filename = f"Test{test} Part{part}.mp3"
        path = os.path.join(audio_dir, filename)
        exists = os.path.exists(path)
        size = os.path.getsize(path) if exists else 0
        audio_status.append({
            "test": test,
            "part": part,
            "filename": filename,
            "status": "FOUND" if exists else "MISSING",
            "size_mb": size / (1024*1024)
        })

print("| Test | Part | Filename | Status | Size (MB) |")
print("|------|------|----------|--------|-----------|")
for a in audio_status:
    print(f"| Test {a['test']} | Part {a['part']} | {a['filename']} | {a['status']} | {a['size_mb']:.2f} |")
print("-" * 58)

# Check Images
print("Writing Task 1 Chart Images:")
images_status = []
for test in range(1, 5):
    filename = f"cam19_test{test}_task1.jpeg"
    path = os.path.join(image_dir, filename)
    exists = os.path.exists(path)
    size = os.path.getsize(path) if exists else 0
    images_status.append({
        "test": test,
        "filename": filename,
        "status": "FOUND" if exists else "MISSING",
        "size_kb": size / 1024
    })

print("| Test | Task | Filename | Status | Size (KB) |")
print("|------|------|----------|--------|-----------|")
for img in images_status:
    print(f"| Test {img['test']} | Task 1 | {img['filename']} | {img['status']} | {img['size_kb']:.1f} |")
print("==========================================================")

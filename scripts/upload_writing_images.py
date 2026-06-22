import subprocess
import os

container_name = "ielts-smartprep-minio-1"
image_dir = r"d:\sources\repos\proj\IELST\ielts-smartprep\scripts\extracted_images"
bucket = "listening-audio"

images = [
    "cam19_test1_task1.jpeg",
    "cam19_test2_task1.jpeg",
    "cam19_test3_task1.jpeg",
    "cam19_test4_task1.jpeg"
]

print("=== Uploading Writing Task 1 Images to MinIO ===")

# Create temp dir in container
subprocess.run(f"docker exec {container_name} mkdir -p /tmp/images", shell=True, check=True)

# Copy files
for img in images:
    local_path = os.path.join(image_dir, img)
    if not os.path.exists(local_path):
        print(f"File not found: {local_path}")
        continue
    print(f"Copying {img} to container...")
    subprocess.run(f"docker cp \"{local_path}\" {container_name}:/tmp/images/{img}", shell=True, check=True)

# Run mc cp
print("Importing images to MinIO bucket...")
subprocess.run(f"docker exec {container_name} mc cp --recursive /tmp/images/ local/{bucket}", shell=True, check=True)

# Clean up
print("Cleaning up temp files inside container...")
subprocess.run(f"docker exec {container_name} rm -rf /tmp/images", shell=True, check=True)

print("Done uploading Writing images!")

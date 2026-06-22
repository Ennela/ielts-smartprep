# ============================================================
# Upload Cambridge 19 Audio Files to MinIO via Docker Container
# ============================================================

$CONTAINER_NAME = "ielts-smartprep-minio-1"
$AUDIO_SOURCE = "d:\sources\repos\proj\IELST\IETLS 19 cam\CAMBRIDGE 19\Audio cam 19"
$BUCKET = "listening-audio"

# Mapping: original filename -> MinIO key
$fileMapping = @{
    "Test1 Part1.mp3" = "cam19_test1_part1.mp3"
    "Test1 Part2.mp3" = "cam19_test1_part2.mp3"
    "Test1 Part3.mp3" = "cam19_test1_part3.mp3"
    "Test1 Part4.mp3" = "cam19_test1_part4.mp3"
    "Test2 Part1.mp3" = "cam19_test2_part1.mp3"
    "Test2 Part2.mp3" = "cam19_test2_part2.mp3"
    "Test2 Part3.mp3" = "cam19_test2_part3.mp3"
    "Test2 Part4.mp3" = "cam19_test2_part4.mp3"
    "Test3 Part1.mp3" = "cam19_test3_part1.mp3"
    "Test3 Part2.mp3" = "cam19_test3_part2.mp3"
    "Test3 Part3.mp3" = "cam19_test3_part3.mp3"
    "Test3 Part4.mp3" = "cam19_test3_part4.mp3"
    "Test4 Part1.mp3" = "cam19_test4_part1.mp3"
    "Test4 Part2.mp3" = "cam19_test4_part2.mp3"
    "Test4 Part3.mp3" = "cam19_test4_part3.mp3"
    "Test4 Part4.mp3" = "cam19_test4_part4.mp3"
}

Write-Host "=== Cambridge 19 Audio Upload via Docker exec ===" -ForegroundColor Cyan
Write-Host "MinIO Container: $CONTAINER_NAME"
Write-Host "Target Bucket:    $BUCKET"
Write-Host ""

# Create temporary folder inside container
docker exec $CONTAINER_NAME mkdir -p /tmp/audio

$successCount = 0
$failCount = 0

foreach ($entry in $fileMapping.GetEnumerator()) {
    $sourcePath = Join-Path $AUDIO_SOURCE $entry.Key
    $objectKey = $entry.Value

    if (-not (Test-Path $sourcePath)) {
        Write-Host "[SKIP] $($entry.Key) - file not found locally" -ForegroundColor Yellow
        $failCount++
        continue
    }

    Write-Host "Copying $($entry.Key) to container as $objectKey..." -ForegroundColor Gray
    # Copy file to container's temp folder with the new name
    docker cp $sourcePath "${CONTAINER_NAME}:/tmp/audio/$objectKey"

    if ($LASTEXITCODE -eq 0) {
        $successCount++
    } else {
        Write-Host "[FAIL] Failed to copy $($entry.Key) to container" -ForegroundColor Red
        $failCount++
    }
}

if ($successCount -gt 0) {
    Write-Host "`nConfiguring MinIO client and importing to bucket..." -ForegroundColor Cyan
    
    # Configure mc client inside container
    docker exec $CONTAINER_NAME mc alias set local http://localhost:9000 minioadmin minioadmin
    
    # Ensure the bucket exists
    docker exec $CONTAINER_NAME mc mb -p local/$BUCKET
    
    # Copy the files into the bucket
    docker exec $CONTAINER_NAME mc cp --recursive /tmp/audio/ local/$BUCKET
    
    # Clean up temp folder inside container
    docker exec $CONTAINER_NAME rm -rf /tmp/audio
    
    Write-Host "`nImport completed." -ForegroundColor Green
} else {
    Write-Host "`nNo files were copied to container." -ForegroundColor Red
}

Write-Host "`n=== Upload Summary ===" -ForegroundColor Cyan
Write-Host "Copied successfully: $successCount"
if ($failCount -gt 0) {
    Write-Host "Failed/Skipped:       $failCount" -ForegroundColor Red
}

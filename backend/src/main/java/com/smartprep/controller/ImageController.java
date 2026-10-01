package com.smartprep.controller;

import com.smartprep.dto.response.ApiResponse;
import com.smartprep.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Images that belong to exam content: a question group's diagram, map or plan, and the
 * chart of a Writing Task 1 prompt.
 *
 * <p>Uploaded by admins and served from this site, because the pages allow images from this
 * origin only. Until now an admin could only paste a link to someone else's image, which the
 * browser then blocked.
 *
 * <p>Only keys this controller made are served. The avatar route serves any object in the
 * bucket, audio included; this one is limited to {@code img_<uuid>.<ext>}.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ImageController {

    static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final Pattern KEY = Pattern.compile("img_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|webp)");

    private final StorageService storageService;

    @PostMapping(value = "/api/v1/admin/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a content image", description = "PNG, JPEG or WebP, up to 5 MB. Returns its URL on this site.")
    public ResponseEntity<ApiResponse<Map<String, String>>> upload(@RequestParam("file") MultipartFile file)
            throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Image must be under 5 MB");
        }
        byte[] bytes = file.getBytes();
        // Decided from the bytes, not from the name or the declared type, both of which the
        // client chooses: a page saved as "chart.png" must not be served as an image.
        String extension = imageExtension(bytes);
        if (extension == null) {
            throw new IllegalArgumentException("Only PNG, JPEG or WebP images are allowed");
        }
        String key = "img_" + UUID.randomUUID() + "." + extension;
        String url = storageService.uploadContentImage(key, bytes, contentType(extension));
        return ResponseEntity.ok(ApiResponse.ok(Map.of("url", url), "Image uploaded"));
    }

    @GetMapping("/api/v1/images/{key}")
    @Operation(summary = "Serve a content image")
    public ResponseEntity<byte[]> serve(@PathVariable String key) {
        if (!KEY.matcher(key).matches()) {
            return ResponseEntity.notFound().build();
        }
        byte[] bytes;
        try {
            bytes = storageService.downloadAudio(key); // reads any object; the name predates images
        } catch (Exception e) {
            log.warn("Content image {} not found: {}", key, e.getMessage());
            return ResponseEntity.notFound().build();
        }
        String extension = key.substring(key.lastIndexOf('.') + 1);
        // The key is new for every upload, so the bytes behind it never change.
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType(extension)))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(bytes);
    }

    static String imageExtension(byte[] b) {
        if (startsWith(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return "png";
        if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) return "jpg";
        if (startsWith(b, 0, 'R', 'I', 'F', 'F') && startsWith(b, 8, 'W', 'E', 'B', 'P')) return "webp";
        return null;
    }

    private static boolean startsWith(byte[] b, int offset, int... expected) {
        if (b.length < offset + expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((b[offset + i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }

    private static String contentType(String extension) {
        return switch (extension) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }
}

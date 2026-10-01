package com.smartprep.service.util;

/**
 * Checks for image URLs an admin saves on content.
 *
 * <p>The pages are served with {@code img-src 'self'}, so an image linked from another site
 * is blocked in the learner's browser and the question or Task 1 prompt shows nothing.
 * That is how both live Task 1 prompts lost their charts. Images are uploaded instead
 * ({@code POST /api/v1/admin/images}) and saved as a path on this site.
 */
public final class ImageUrls {

    private ImageUrls() {
    }

    /** The URL, trimmed; null when blank. Refuses one that points at another site. */
    public static String sameOriginOrNull(String url) {
        if (url == null || url.isBlank()) return null;
        String trimmed = url.trim();
        if (!trimmed.startsWith("/") || trimmed.startsWith("//")) {
            throw new IllegalArgumentException(
                    "Images from other sites are blocked on this site. Upload the image instead.");
        }
        return trimmed;
    }
}

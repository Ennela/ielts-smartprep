package com.smartprep.service.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageUrlsTest {

    @Test
    @DisplayName("a path on this site is kept, trimmed")
    void keepsSameOrigin() {
        assertThat(ImageUrls.sameOriginOrNull(" /api/v1/images/img_x.png ")).isEqualTo("/api/v1/images/img_x.png");
    }

    @Test
    @DisplayName("blank means no image")
    void blankIsNull() {
        assertThat(ImageUrls.sameOriginOrNull(null)).isNull();
        assertThat(ImageUrls.sameOriginOrNull("  ")).isNull();
    }

    @Test
    @DisplayName("a link to another site is refused, since the browser would block it")
    void refusesOtherSites() {
        for (String url : new String[] {
                "https://ieltscity.vn/wp-content/uploads/2024/07/chart.jpg",
                "http://example.com/a.png",
                "//cdn.example.com/a.png"}) {
            assertThatThrownBy(() -> ImageUrls.sameOriginOrNull(url))
                    .as(url)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Upload the image");
        }
    }
}

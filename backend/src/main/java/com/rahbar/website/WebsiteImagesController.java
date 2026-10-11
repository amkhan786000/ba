package com.rahbar.website;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Website photos at the same URL the website uses ({@code /api/images/<file>}), so Rahbar's admin screens can show
 * the images stored in website content. Open to everyone (SecurityConfig), like on the website.
 */
@RestController
public class WebsiteImagesController {

    private final WebsiteImageStorage storage;

    public WebsiteImagesController(WebsiteImageStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/api/images/{fileName}")
    public ResponseEntity<Resource> image(@PathVariable String fileName) {
        return WebsitePublicController.serve(storage, fileName);
    }
}

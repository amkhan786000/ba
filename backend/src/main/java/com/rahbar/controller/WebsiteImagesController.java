package com.rahbar.controller;

import com.rahbar.service.WebsiteImageStorage;
import com.rahbar.controller.WebsiteImagesController;
import com.rahbar.controller.WebsitePublicController;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Website photos and PDFs at the same URLs the website uses ({@code /api/images/<file>}, {@code /api/documents/<file>}),
 * so Rahbar's admin screens can show what website content links to. Open to everyone (SecurityConfig), like on the website.
 */
@RestController
public class WebsiteImagesController {

    private final WebsiteImageStorage storage;

    public WebsiteImagesController(WebsiteImageStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/api/images/{fileName}")
    public ResponseEntity<Resource> image(@PathVariable String fileName) {
        return WebsitePublicController.serve(storage, fileName, false);
    }

    @GetMapping("/api/documents/{fileName}")
    public ResponseEntity<Resource> document(@PathVariable String fileName) {
        return WebsitePublicController.serve(storage, fileName, true);
    }
}

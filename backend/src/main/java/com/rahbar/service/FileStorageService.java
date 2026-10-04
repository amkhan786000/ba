package com.rahbar.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class FileStorageService {

    @Value("${app.upload-dir}")
    private String uploadDir;

    public String store(MultipartFile file, String desiredFilename) {
        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);
            String filename = StringUtils.cleanPath(desiredFilename);
            Path target = dir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return filename;
        } catch (IOException e) {
            throw new RuntimeException("Could not store file " + desiredFilename, e);
        }
    }

    public String sanitizeFilename(String original) {
        if (original == null) return "file";
        String cleaned = StringUtils.cleanPath(original);
        return cleaned.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

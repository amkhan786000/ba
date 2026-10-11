package com.rahbar.website;

import com.rahbar.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Website photos and PDFs, kept in {@code <upload-dir>/website/} apart from application documents (which are
 * private). Only JPEG, PNG, GIF, WebP and PDF are accepted, detected from the file's bytes; files get random names.
 */
@Component
public class WebsiteImageStorage {

    public record Stored(String fileName, String contentType, long size) {
    }

    private static final Pattern SAFE_NAME = Pattern.compile("[a-f0-9]{32}\\.(jpg|png|gif|webp|pdf)");

    private final Path dir;

    public WebsiteImageStorage(@Value("${app.upload-dir}") String uploadDir) {
        this.dir = Path.of(uploadDir == null || uploadDir.isBlank() ? "uploads" : uploadDir, "website").toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the website image folder " + dir, e);
        }
    }

    public Stored store(MultipartFile file, long maxBytes) {
        return store(file, maxBytes, false);
    }

    /** A PDF to download (research paper, report). */
    public Stored storeDocument(MultipartFile file, long maxBytes) {
        return store(file, maxBytes, true);
    }

    private Stored store(MultipartFile file, long maxBytes, boolean pdf) {
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, pdf ? "Please choose a PDF file." : "Please choose an image file.");
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The image is too large (maximum " + maxBytes / (1024 * 1024) + " MB).");
        }
        byte[] head = new byte[12];
        try (InputStream in = file.getInputStream()) {
            head = Arrays.copyOf(head, in.readNBytes(head, 0, head.length));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String ext = detect(head);
        if (pdf ? !"pdf".equals(ext) : (ext == null || "pdf".equals(ext))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, pdf ? "Only PDF files are allowed." : "Only JPEG, PNG, GIF or WebP images are allowed.");
        }
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, dir.resolve(name));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the image", e);
        }
        return new Stored(name, contentTypeOf(name), file.getSize());
    }

    /** The stored file, or null if the name is not one we could have generated. */
    public Path resolve(String fileName) {
        if (fileName == null || !SAFE_NAME.matcher(fileName).matches()) return null;
        Path p = dir.resolve(fileName).normalize();
        return p.startsWith(dir) && Files.isRegularFile(p) ? p : null;
    }

    public void delete(String fileName) {
        Path p = resolve(fileName);
        if (p == null) return;
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete image file", e);
        }
    }

    public static String contentTypeOf(String fileName) {
        return switch (fileName.substring(fileName.lastIndexOf('.') + 1)) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    static String detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "gif";
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') return "pdf";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        return null;
    }
}

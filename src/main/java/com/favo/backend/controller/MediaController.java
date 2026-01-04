package com.favo.backend.controller;

import com.favo.backend.Domain.review.Media;
import com.favo.backend.Domain.review.Repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Media Controller
 * Review'lara ait media dosyalarını (resimler) getirmek için endpoint'ler
 * Review'lar public olduğu için media'lar da public'tir
 */
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaRepository mediaRepository;

    /**
     * 📷 Media'yı ID'ye göre getir (image binary data)
     * GET /api/media/{id}
     * 
     * Response: 200 OK + image binary data (Content-Type: image/jpeg, image/png, vb.)
     * Error: 404 Not Found - Media bulunamazsa veya pasifse
     */
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> getMediaById(@PathVariable Long id) {
        Media media = mediaRepository.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new RuntimeException("Media not found with id: " + id));

        // MIME type'ı belirle (default: image/jpeg)
        String mimeType = media.getMimeType();
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = MediaType.IMAGE_JPEG_VALUE;
        }

        // Response headers'ı ayarla
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mimeType));
        headers.setContentLength(media.getImageData().length);
        
        // Cache için header ekle (opsiyonel)
        headers.setCacheControl("public, max-age=3600"); // 1 saat cache

        return new ResponseEntity<>(media.getImageData(), headers, HttpStatus.OK);
    }
}


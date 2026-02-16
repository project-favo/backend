package com.favo.backend.controller;

import com.favo.backend.Service.Product.RainforestImportService;
import com.favo.backend.Service.User.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin Controller
 * Geçici endpoint'ler için kullanılır - tek seferlik işlemler için
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final RainforestImportService rainforestImportService;

    /**
     * Aktif olmayan kullanıcıları ve bağlantılarını fiziksel olarak sil
     * DELETE /api/admin/cleanup/inactive-users
     * 
     * Geçici endpoint - admin tarafından kullanılacak
     * Authentication gerektirmez (SecurityConfig'de permitAll())
     * 
     * Siler:
     * - Aktif olmayan (isActive = false) SystemUser'lar
     * - Bu kullanıcıların Review'ları
     * - Bu kullanıcıların Media'ları (Review'a bağlı)
     * - Bu kullanıcıların ReviewInteraction'ları
     * - Bu kullanıcıların ProductInteraction'ları
     * - Bu kullanıcıların ProfilePhoto'ları
     * 
     * Response: 200 OK + { "deletedUsers": 5, "message": "Successfully deleted 5 inactive users and their connections" }
     */
    /**
     * Rainforest JSON'dan tag ve ürün import et.
     * Mevcut tag ve ürünlere soft delete atar, JSON'dan yeni verileri ekler.
     * POST /api/admin/import-rainforest
     * 
     * JWT gerektirmez (SecurityConfig permitAll).
     * Opsiyonel: ?json=path parametresi ile farklı JSON dosyası belirtilebilir.
     */
    @PostMapping("/import-rainforest")
    public ResponseEntity<ImportResponse> importRainforest(
            @RequestParam(required = false) String json) {
        String jsonPath = (json != null && !json.isBlank()) ? json : "rainforest_products_output.json";
        rainforestImportService.runImport(jsonPath);
        return ResponseEntity.ok(new ImportResponse("Rainforest import tamamlandı"));
    }

    @DeleteMapping("/cleanup/inactive-users")
    public ResponseEntity<CleanupResponse> deleteInactiveUsers() {
        int deletedCount = userService.deleteInactiveUsersAndConnections();
        return ResponseEntity.ok(new CleanupResponse(
            deletedCount,
            "Successfully deleted " + deletedCount + " inactive users and their connections"
        ));
    }

    /**
     * Cleanup response DTO
     */
    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CleanupResponse {
        private int deletedUsers;
        private String message;
    }

    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ImportResponse {
        private String message;
    }
}


package com.smartbillpro.backend.controller;

import com.smartbillpro.backend.dto.common.ApiResponse;
import com.smartbillpro.backend.dto.shop.ShopSettingsRequest;
import com.smartbillpro.backend.dto.shop.ShopSettingsResponse;
import com.smartbillpro.backend.service.ShopSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/shop-settings")
@RequiredArgsConstructor
public class ShopSettingsController {

    private final ShopSettingsService shopSettingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<ShopSettingsResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(shopSettingsService.getSettings()));
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
    public ResponseEntity<ApiResponse<ShopSettingsResponse>> updateSettings(
            @Valid @RequestBody ShopSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Shop settings updated", shopSettingsService.updateSettings(request)));
    }

    @PostMapping(value = "/qr-code", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
    public ResponseEntity<ApiResponse<ShopSettingsResponse>> uploadQrCode(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("QR code uploaded", shopSettingsService.uploadQrCode(file)));
    }
}

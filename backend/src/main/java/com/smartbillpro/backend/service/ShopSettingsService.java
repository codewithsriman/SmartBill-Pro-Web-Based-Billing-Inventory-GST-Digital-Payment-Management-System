package com.smartbillpro.backend.service;

import com.smartbillpro.backend.dto.shop.ShopSettingsRequest;
import com.smartbillpro.backend.dto.shop.ShopSettingsResponse;
import com.smartbillpro.backend.entity.ShopSettings;
import com.smartbillpro.backend.repository.ShopSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShopSettingsService {

    private final ShopSettingsRepository shopSettingsRepository;

    @Value("${app.upload.shop-qr-dir}")
    private String shopQrDir;

    /** This app has a single shop profile (not multi-tenant), so we always operate on the
     *  first row, creating it on first save if it doesn't exist yet. */
    @Transactional(readOnly = true)
    public ShopSettingsResponse getSettings() {
        ShopSettings settings = shopSettingsRepository.findAll().stream().findFirst().orElse(null);
        if (settings == null) {
            return ShopSettingsResponse.builder().build();
        }
        return toResponse(settings);
    }

    @Transactional
    public ShopSettingsResponse updateSettings(ShopSettingsRequest request) {
        ShopSettings settings = shopSettingsRepository.findAll().stream().findFirst()
                .orElseGet(ShopSettings::new);

        settings.setShopName(request.getShopName());
        settings.setShopAddress(request.getShopAddress());
        settings.setMobileNumber(request.getMobileNumber());
        settings.setGstNumber(request.getGstNumber());
        settings.setUpiId(request.getUpiId());

        return toResponse(shopSettingsRepository.save(settings));
    }

    @Transactional
    public ShopSettingsResponse uploadQrCode(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("QR code must be an image file (PNG, JPG, etc.)");
        }

        ShopSettings settings = shopSettingsRepository.findAll().stream().findFirst()
                .orElseGet(ShopSettings::new);
        if (settings.getShopName() == null) {
            // satisfy NOT NULL constraint if this is the very first save and QR is uploaded before the form
            settings.setShopName("My Shop");
        }

        try {
            Path dir = Path.of(shopQrDir);
            Files.createDirectories(dir);

            String extension = getExtension(file.getOriginalFilename());
            String filename = "shop_qr_" + UUID.randomUUID() + extension;
            Path filePath = dir.resolve(filename);
            file.transferTo(filePath);

            settings.setQrCodeImage("shop-qr/" + filename);
            settings = shopSettingsRepository.save(settings);

            return toResponse(settings);
        } catch (IOException e) {
            log.error("Failed to save shop QR code image", e);
            throw new RuntimeException("Failed to save QR code image", e);
        }
    }

    private String getExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) return ".png";
        return originalFilename.substring(originalFilename.lastIndexOf('.'));
    }

    private ShopSettingsResponse toResponse(ShopSettings settings) {
        return ShopSettingsResponse.builder()
                .id(settings.getId())
                .shopName(settings.getShopName())
                .shopAddress(settings.getShopAddress())
                .mobileNumber(settings.getMobileNumber())
                .gstNumber(settings.getGstNumber())
                .upiId(settings.getUpiId())
                .qrCodeImageUrl(settings.getQrCodeImage() != null ? "/uploads/" + settings.getQrCodeImage() : null)
                .build();
    }
}

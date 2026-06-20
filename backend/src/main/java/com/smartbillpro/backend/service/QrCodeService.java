package com.smartbillpro.backend.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

@Service
@Slf4j
public class QrCodeService {

    @Value("${app.upload.qr-code-dir}")
    private String qrCodeDir;

    private static final int QR_SIZE = 300;

    /**
     * Generates a QR code PNG for the given payload and saves it to disk.
     * Returns the relative file path (to be served via the static /uploads/** mapping).
     */
    public String generateAndSave(String payload, String filenamePrefix) {
        try {
            Path dir = Path.of(qrCodeDir);
            Files.createDirectories(dir);

            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(payload, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);

            String filename = filenamePrefix + "_" + System.currentTimeMillis() + ".png";
            Path filePath = dir.resolve(filename);
            MatrixToImageWriter.writeToPath(matrix, "PNG", filePath);

            return "qrcodes/" + filename;
        } catch (WriterException | IOException e) {
            log.error("Failed to generate QR code for payload '{}'", payload, e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }
}

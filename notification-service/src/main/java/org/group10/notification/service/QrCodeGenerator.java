package org.group10.notification.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Convierte el código de una entrada (el UUID {@code qrCode} del ticket-service) en la imagen PNG del QR que se escanea
 * en el ingreso.
 */
@Component
public class QrCodeGenerator {

    static final int SIZE = 240;
    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final Map<EncodeHintType, Object> HINTS = Map.of(
            EncodeHintType.MARGIN, 1,
            EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());

    public byte[] png(String content) {
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE, HINTS);
        } catch (WriterException e) {
            throw new IllegalStateException("No se pudo generar el QR", e);
        }
        BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        for (int x = 0; x < matrix.getWidth(); x++) {
            for (int y = 0; y < matrix.getHeight(); y++) {
                image.setRGB(x, y, matrix.get(x, y) ? BLACK : WHITE);
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", png);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir el PNG del QR", e);
        }
        return png.toByteArray();
    }
}

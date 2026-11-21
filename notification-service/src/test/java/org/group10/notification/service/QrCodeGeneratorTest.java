package org.group10.notification.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodeGeneratorTest {

    private final QrCodeGenerator generator = new QrCodeGenerator();

    @Test
    void png_isAQrThatReadsBackAsTheTicketCode() throws Exception {
        String code = "3f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f";

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(generator.png(code)));

        assertThat(image.getWidth()).isEqualTo(QrCodeGenerator.SIZE);
        assertThat(image.getHeight()).isEqualTo(QrCodeGenerator.SIZE);
        assertThat(read(image)).isEqualTo(code);
    }

    @Test
    void png_differentCodes_giveDifferentImages() {
        assertThat(generator.png("qr-uno")).isNotEqualTo(generator.png("qr-dos"));
    }

    /**
     * Lee el QR como lo haría el lector del ingreso.
     */
    private static String read(BufferedImage image) throws Exception {
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        RGBLuminanceSource source = new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels);
        return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source))).getText();
    }
}

package com.gallery.generator.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Utility class responsible for scaling and resizing images.
 */
public class ImageResizer {

    public static void resizeImage(File inputFile, File outputFile, double scalePercentage) throws IOException {
        BufferedImage originalImage = ImageIO.read(inputFile);
        if (originalImage == null) {
            throw new IOException("Unsupported image format or corrupted file: " + inputFile.getAbsolutePath());
        }

        double factor = scalePercentage / 100.0;
        int targetWidth = (int) Math.max(1, originalImage.getWidth() * factor);
        int targetHeight = (int) Math.max(1, originalImage.getHeight() * factor);

        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();

        String formatName = inputFile.getName().toLowerCase().endsWith(".png")? "png" : "jpg";

        ImageIO.write(resizedImage, formatName, outputFile);
    }
}


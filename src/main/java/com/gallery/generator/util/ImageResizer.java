package com.gallery.generator.util;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public final class ImageResizer {
    private ImageResizer() {
    }

    public static void resizeImagePct(File inputFile, File outputFile, double scalePercentage, boolean copyExif, boolean includeWatermark) throws IOException {
        BufferedImage source = readImage(inputFile);
        int orientation = getExifOrientation(inputFile);
        BufferedImage normalized = applyOrientation(source, orientation);

        double factor = scalePercentage / 100.0;
        int targetWidth = Math.max(1, (int) Math.round(normalized.getWidth() * factor));
        int targetHeight = Math.max(1, (int) Math.round(normalized.getHeight() * factor));

        renderAndWrite(normalized, targetWidth, targetHeight, inputFile, outputFile, copyExif, includeWatermark);
    }

    public static void resizeToMaxSide(File inputFile, File outputFile, int maxSidePx) throws IOException {
        BufferedImage source = readImage(inputFile);
        int orientation = getExifOrientation(inputFile);
        BufferedImage normalized = applyOrientation(source, orientation);

        int srcW = normalized.getWidth();
        int srcH = normalized.getHeight();

        int targetW = srcW;
        int targetH = srcH;

        if (srcW > maxSidePx || srcH > maxSidePx) {
            if (srcW >= srcH) {
                targetW = maxSidePx;
                targetH = Math.max(1, (int) Math.round(srcH * (maxSidePx / (double) srcW)));
            } else {
                targetH = maxSidePx;
                targetW = Math.max(1, (int) Math.round(srcW * (maxSidePx / (double) srcH)));
            }
        }

        renderAndWrite(normalized, targetW, targetH, inputFile, outputFile, false, false);
    }

    public static void copyFileAndTransferExif(File inputFile, File outputFile, boolean copyExif) throws IOException {
        if (!copyExif || !isJpeg(inputFile)) {
            Files.copy(inputFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        Files.copy(inputFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        clearExifOrientation(outputFile);
    }

    private static BufferedImage readImage(File file) throws IOException {
        try {
            BufferedImage image = Imaging.getBufferedImage(file);
            if (image == null) {
                throw new IOException("Unsupported image format or corrupted file: " + file.getAbsolutePath());
            }
            return image;
        } catch (Exception e) {
            throw new IOException("Failed to read image: " + file.getAbsolutePath() + " - " + e.getMessage(), e);
        }
    }

    private static void renderAndWrite(BufferedImage src, int width, int height, File inputFile, File outputFile, boolean copyExif, boolean includeWatermark) throws IOException {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = resized.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, width, height, null);
            
            if (includeWatermark) {
                drawWatermark(g, width, height);
            }
        } finally {
            g.dispose();
        }

        boolean jpeg = isJpeg(outputFile);
        if (copyExif && jpeg) {
            File temp = File.createTempFile("gallery-resize-", ".jpg");
            try {
                writeJpeg(resized, temp);
                writeJpegWithMetadataFromSource(inputFile, temp, outputFile);
            } finally {
                Files.deleteIfExists(temp.toPath());
            }
        } else {
            if (jpeg) {
                writeJpeg(resized, outputFile);
            } else {
                Imaging.writeImage(resized, outputFile, ImageFormats.PNG);
            }
        }
    }

    private static void writeJpeg(BufferedImage image, File outputFile) throws IOException {
        try {
            //Imaging.writeImage(image, outputFile, ImageFormats.JPEG);
            ImageIO.write(image, "jpg", outputFile);
        } catch (Exception e) {
            throw new IOException("Failed to write JPEG: " + outputFile.getAbsolutePath() + " - " + e.getMessage(), e);
        }
    }

    private static void writeJpegWithMetadataFromSource(File sourceJpeg, File resizedJpeg, File destinationJpeg) throws IOException {
        try {
            ImageMetadata metadata = Imaging.getMetadata(sourceJpeg);
            TiffOutputSet outputSet = null;

            if (metadata instanceof JpegImageMetadata jpegMetadata) {
                TiffImageMetadata exif = jpegMetadata.getExif();
                if (exif != null) {
                    outputSet = exif.getOutputSet();
                    if (outputSet != null) {
                        outputSet.removeField(TiffTagConstants.TIFF_TAG_ORIENTATION);
                    }
                }
            }

            if (outputSet == null) {
                Files.copy(resizedJpeg.toPath(), destinationJpeg.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            }

            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(destinationJpeg))) {
                new ExifRewriter().updateExifMetadataLossless(resizedJpeg, os, outputSet);
            }
        } catch (Exception e) {
            Files.copy(resizedJpeg.toPath(), destinationJpeg.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static int getExifOrientation(File srcFile) {
        try {
            ImageMetadata metadata = Imaging.getMetadata(srcFile);
            if (metadata instanceof JpegImageMetadata jpegMetadata) {
                var field = jpegMetadata.findExifValueWithExactMatch(TiffTagConstants.TIFF_TAG_ORIENTATION);
                if (field != null) {
                    return field.getIntValue();
                }
            }
        } catch (Exception ignored) {
        }
        return 1;
    }

    static BufferedImage applyOrientation(BufferedImage src, int orientation) {
        if (orientation == 1 || orientation == 0) {
            return src;
        }

        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dst;
        Graphics2D g;

        switch (orientation) {
            case 2:
                dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                g = dst.createGraphics();
                g.translate(w, 0);
                g.scale(-1, 1);
                break;
            case 3:
                dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                g = dst.createGraphics();
                g.translate(w, h);
                g.rotate(Math.PI);
                break;
            case 4:
                dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                g = dst.createGraphics();
                g.translate(0, h);
                g.scale(1, -1);
                break;
            case 5:
                dst = new BufferedImage(h, w, BufferedImage.TYPE_INT_RGB);
                g = dst.createGraphics();
                g.rotate(Math.PI / 2);
                g.scale(1, -1);
                break;
            case 6:
                dst = new BufferedImage(h, w, BufferedImage.TYPE_INT_RGB);
                g = dst.createGraphics();
                g.translate(h, 0);
                g.rotate(Math.PI / 2);
                break;
            case 7:
                dst = new BufferedImage(h, w, BufferedImage.TYPE_INT_RGB);
                g = dst.createGraphics();
                g.translate(h, w);
                g.rotate(Math.PI / 2);
                g.scale(-1, 1);
                break;
            case 8:
                dst = new BufferedImage(h, w, BufferedImage.TYPE_INT_RGB);
                g = dst.createGraphics();
                g.translate(0, w);
                g.rotate(-Math.PI / 2);
                break;
            default:
                return src;
        }

        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    private static void clearExifOrientation(File jpegFile) {
        try {
            ImageMetadata metadata = Imaging.getMetadata(jpegFile);
            if (!(metadata instanceof JpegImageMetadata jpegMetadata)) {
                return;
            }
            TiffImageMetadata exif = jpegMetadata.getExif();
            if (exif == null) {
                return;
            }
            TiffOutputSet outputSet = exif.getOutputSet();
            if (outputSet == null) {
                return;
            }
            outputSet.removeField(TiffTagConstants.TIFF_TAG_ORIENTATION);

            File temp = File.createTempFile("gallery-exif-", ".jpg");
            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(temp))) {
                new ExifRewriter().updateExifMetadataLossless(jpegFile, os, outputSet);
            }
            Files.move(temp.toPath(), jpegFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
        }
    }

    private static boolean isJpeg(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private static void drawWatermark(Graphics2D g, int width, int height) throws IOException {
        BufferedImage watermark = loadWatermarkFromResources();
        if (watermark == null) {
            return;
        }

        int padding = 10;
        int watermarkWidth = Math.min(width / (width > height ? 4 : 3), watermark.getWidth());
        int watermarkHeight = (int) ((double) watermarkWidth / watermark.getWidth() * watermark.getHeight());

        int x = width - watermarkWidth - padding;
        int y = height - watermarkHeight - padding;
        
        float alpha = 0.7f;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g.drawImage(watermark, x, y, watermarkWidth, watermarkHeight, null);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    }

    private static BufferedImage loadWatermarkFromResources() throws IOException {
        try (var input = ImageResizer.class.getClassLoader().getResourceAsStream("watermark.png")) {
            if (input == null) {
                System.err.println("WARNING: watermark.png not found in resources");
                return null;
            }
            return ImageIO.read(input);
        }
    }
}
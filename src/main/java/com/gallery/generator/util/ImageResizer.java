package com.gallery.generator.util;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;

public class ImageResizer {

    /**
     * Resizes an image based on a percentage value and normalizes orientation if needed.
     */
    public static void resizeImagePct(File inputFile, File outputFile, double scalePercentage, boolean copyExif) throws IOException {
        BufferedImage originalImage = ImageIO.read(inputFile);
        if (originalImage == null) {
            throw new IOException("Unsupported image format or corrupted file: " + inputFile.getAbsolutePath());
        }

        int orientation = getExifOrientation(inputFile);
        BufferedImage orientedImage = correctOrientation(originalImage, orientation);

        double factor = scalePercentage / 100.0;
        int targetWidth = (int) Math.max(1, orientedImage.getWidth() * factor);
        int targetHeight = (int) Math.max(1, orientedImage.getHeight() * factor);

        renderAndWrite(orientedImage, targetWidth, targetHeight, inputFile, outputFile, copyExif, true);
    }

    /**
     * Resizes an image constraining its longest side to a maximum pixel value and auto-rotates it.
     */
    public static void resizeToMaxSide(File inputFile, File outputFile, int maxSidePx) throws IOException {
        BufferedImage originalImage = ImageIO.read(inputFile);
        if (originalImage == null) {
            throw new IOException("Unsupported image format or corrupted file: " + inputFile.getAbsolutePath());
        }

        int orientation = getExifOrientation(inputFile);
        BufferedImage orientedImage = correctOrientation(originalImage, orientation);

        int origWidth = orientedImage.getWidth();
        int origHeight = orientedImage.getHeight();

        int targetWidth = origWidth;
        int targetHeight = origHeight;

        if (origWidth > maxSidePx || origHeight > maxSidePx) {
            if (origWidth >= origHeight) {
                targetWidth = maxSidePx;
                targetHeight = (int) Math.max(1, (origHeight * (double) maxSidePx) / origWidth);
            } else {
                targetHeight = maxSidePx;
                targetWidth = (int) Math.max(1, (origWidth * (double) maxSidePx) / origHeight);
            }
        }

        renderAndWrite(orientedImage, targetWidth, targetHeight, inputFile, outputFile, false, false);
    }

    /**
     * Copies a file exactly as it is, but preserves EXIF data if requested and available.
     */
    public static void copyFileAndTransferExif(File inputFile, File outputFile, boolean copyExif) throws IOException {
        String filenameLower = inputFile.getName().toLowerCase();

        if (copyExif && !filenameLower.endsWith(".png")) {
            copyResizedImageAndTransferExif(inputFile, inputFile, outputFile);
        } else {
            Files.copy(inputFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void renderAndWrite(BufferedImage originalImage, int width, int height,
                                       File inputFile, File outputFile, boolean copyExif, boolean highQuality) throws IOException {
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();

        if (highQuality) {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        } else {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        }

        g2d.drawImage(originalImage, 0, 0, width, height, null);
        g2d.dispose();

        String filenameLower = inputFile.getName().toLowerCase();
        String formatName = filenameLower.endsWith(".png") ? "png" : "jpg";

        if (copyExif && formatName.equals("jpg")) {
            File tempResizedFile = File.createTempFile("galaxy_resize_", ".jpg");
            try {
                ImageIO.write(resizedImage, "jpg", tempResizedFile);
                copyResizedImageAndTransferExif(inputFile, tempResizedFile, outputFile);
            } finally {
                if (!tempResizedFile.delete()) {
                    tempResizedFile.deleteOnExit();
                }
            }
        } else {
            ImageIO.write(resizedImage, formatName, outputFile);
        }
    }

    private static void copyResizedImageAndTransferExif(File sourceJpeg, File resizedJpeg, File destinationJpeg) {
        try {
            TiffOutputSet outputSet = null;
            ImageMetadata metadata = Imaging.getMetadata(sourceJpeg);

            if (metadata instanceof JpegImageMetadata jpegMetadata) {
                TiffImageMetadata exif = jpegMetadata.getExif();
                if (exif != null) {
                    outputSet = exif.getOutputSet();
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
            System.err.println("WARNING: Failed to preserve EXIF metadata tags on file " + sourceJpeg.getName() + ": " + e.getMessage());
            try {
                Files.copy(resizedJpeg.toPath(), destinationJpeg.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ioException) {
                System.err.println("ERROR: Fallback image recovery write routine failed: " + ioException.getMessage());
            }
        }
    }

    private static int getExifOrientation(File srcFile) {
        try {
            ImageMetadata metadata = Imaging.getMetadata(srcFile);
            if (metadata instanceof JpegImageMetadata jpegMetadata) {
                org.apache.commons.imaging.formats.tiff.TiffField field =
                        jpegMetadata.findExifValueWithExactMatch(TiffTagConstants.TIFF_TAG_ORIENTATION);
                if (field != null) {
                    return field.getIntValue();
                }
            }
        } catch (Exception e) {
            // Suppress and fallback gracefully
        }
        return 1;
    }

    /**
     * Physically rotates the pixel buffer layout using affine matrix calculations based on EXIF marker types.
     * Copies the exact source ColorModel and generates a compatible WritableRaster to preserve
     * advanced color profiles (preventing inverted yellow/red tints on CMYK/YCCK JPEGs).
     */
    private static BufferedImage correctOrientation(BufferedImage src, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return src; // No complex structural rotation mappings required
        }

        int w = src.getWidth();
        int h = src.getHeight();

        // Target canvas sizes must flip for 90 or 270 degree rotation turns
        int targetW = (orientation == 6 || orientation == 8) ? h : w;
        int targetH = (orientation == 6 || orientation == 8) ? w : h;

        // Fix: Dynamically create a color-compatible destination buffer using the source's native ColorModel.
        // This strictly prevents channel inversion bugs (red/yellow color casts) across exotic color profiles.
        java.awt.image.ColorModel cm = src.getColorModel();
        java.awt.image.WritableRaster raster = cm.createCompatibleWritableRaster(targetW, targetH);
        BufferedImage rotated = new BufferedImage(cm, raster, cm.isAlphaPremultiplied(), null);

        Graphics2D g2d = rotated.createGraphics();

        // High quality interpolation guarantees clean rotation boundaries
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        AffineTransform at = new AffineTransform();
        switch (orientation) {
            case 3 -> { // 180 degrees turn
                at.translate(w, h);
                at.rotate(Math.PI);
            }
            case 6 -> { // 90 degrees clockwise turn
                at.translate(h, 0);
                at.rotate(Math.PI / 2);
            }
            case 8 -> { // 270 degrees clockwise turn (90 degrees counter-clockwise)
                at.translate(0, w);
                at.rotate(-Math.PI / 2);
            }
            default -> {
                g2d.dispose();
                return src;
            }
        }

        g2d.transform(at);
        g2d.drawImage(src, 0, 0, null);
        g2d.dispose();

        return rotated;
    }
}

package com.gallery.generator.util;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

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
import javax.imageio.ImageIO;

public class ImageResizer {

    /**
     * Resizes an image based on a percentage value.
     */
    public static void resizeImagePct(File inputFile, File outputFile, double scalePercentage, boolean copyExif) throws IOException {
        BufferedImage originalImage = ImageIO.read(inputFile);
        if (originalImage == null) {
            throw new IOException("Unsupported image format or corrupted file: " + inputFile.getAbsolutePath());
        }

        double factor = scalePercentage / 100.0;
        int targetWidth = (int) Math.max(1, originalImage.getWidth() * factor);
        int targetHeight = (int) Math.max(1, originalImage.getHeight() * factor);

        renderAndWrite(originalImage, targetWidth, targetHeight, inputFile, outputFile, copyExif, true);
    }

    /**
     * Resizes an image constraining its longest side to a maximum pixel value.
     */
    public static void resizeToMaxSide(File inputFile, File outputFile, int maxSidePx) throws IOException {
        BufferedImage originalImage = ImageIO.read(inputFile);
        if (originalImage == null) {
            throw new IOException("Unsupported image format or corrupted file: " + inputFile.getAbsolutePath());
        }

        int origWidth = originalImage.getWidth();
        int origHeight = originalImage.getHeight();

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

        // Previews use false for copyExif and false for highQuality (nearest neighbor for speed)
        renderAndWrite(originalImage, targetWidth, targetHeight, inputFile, outputFile, false, false);
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

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, (highQuality) ? RenderingHints.VALUE_RENDER_QUALITY : RenderingHints.VALUE_RENDER_SPEED);

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
}

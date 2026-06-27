import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

class ResizeTest {

    @Test
    void resizeJpgTo25PercentAndNormalizeOrientation() throws Exception {
        //File input = new File("src/test/resources/oriented.jpg");
        File input = new File("/mnt/F/mess/hrady/Beistein/DSC_51918.jpg");
        Path outputPath = Paths.get("/mnt/F/mess/Gallery/Beistein_51918-resized.jpg");
        Files.deleteIfExists(outputPath);
        File output = Files.createFile(outputPath).toFile();

        BufferedImage result = resizeJpegKeepingOrientation(input, 0.25);
        ImageIO.write(result, "jpg", output);

        BufferedImage written = ImageIO.read(output);

        assertNotNull(written);
        assertEquals(result.getWidth(), written.getWidth());
        assertEquals(result.getHeight(), written.getHeight());

        // 25% resize; for a normal 4000x3000 image => 1000x750.
        // If EXIF orientation rotates 90/270, width and height are swapped before resizing.
        assertTrue(written.getWidth() > 0);
        assertTrue(written.getHeight() > 0);
    }

    static BufferedImage resizeJpegKeepingOrientation(File file, double scale) throws IOException {
        BufferedImage src = ImageIO.read(file);
        int orientation = readExifOrientation(file);

        BufferedImage normalized = applyOrientation(src, orientation);
        int targetW = Math.max(1, (int) Math.round(normalized.getWidth() * scale));
        int targetH = Math.max(1, (int) Math.round(normalized.getHeight() * scale));

        BufferedImage dst = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(normalized, 0, 0, targetW, targetH, null);
        } finally {
            g.dispose();
        }
        return dst;
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
            case 3:
                dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                g = dst.createGraphics();
                g.translate(w, h);
                g.rotate(Math.PI);
                break;
            case 6:
                dst = new BufferedImage(h, w, BufferedImage.TYPE_INT_RGB);
                g = dst.createGraphics();
                g.translate(h, 0);
                g.rotate(Math.PI / 2);
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

    static int readExifOrientation(File file) {
        try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
            Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return 1;
            }
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                javax.imageio.metadata.IIOMetadata metadata = reader.getImageMetadata(0);
                String[] names = metadata.getMetadataFormatNames();
                for (String name : names) {
                    try {
                        var tree = metadata.getAsTree(name);
                        int orientation = searchOrientation(tree);
                        if (orientation != -1) {
                            return orientation;
                        }
                    } catch (Exception ignored) {
                    }
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException ignored) {
        }
        return 1;
    }

    static int searchOrientation(org.w3c.dom.Node node) {
        if (node == null) return -1;
        if ("unknown".equalsIgnoreCase(node.getNodeName())) {
            var attrs = node.getAttributes();
            if (attrs != null) {
                var tag = attrs.getNamedItem("MarkerTag");
                if (tag != null && "225".equals(tag.getNodeValue())) {
                    String text = node.getTextContent();
                    if (text != null) {
                        if (text.contains("Rotate 90 CW")) return 6;
                        if (text.contains("Rotate 180")) return 3;
                        if (text.contains("Rotate 270 CW")) return 8;
                    }
                }
            }
        }
        for (org.w3c.dom.Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            int found = searchOrientation(child);
            if (found != -1) return found;
        }
        return -1;
    }
}
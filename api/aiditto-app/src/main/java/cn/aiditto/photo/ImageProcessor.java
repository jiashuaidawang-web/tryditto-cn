package cn.aiditto.photo;

import cn.aiditto.domain.common.DomainException;
import cn.aiditto.domain.common.ErrorCode;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Decode → downscale to a 1440 px long edge → re-encode as JPEG (技术方案 §6.5 finalize). Writing fresh pixels drops
 * EXIF, GPS and any other metadata. JPEG, PNG and WebP (TwelveMonkeys plugin) are accepted.
 */
final class ImageProcessor {
    static final int LONG_EDGE = 1440;
    static final long MAX_PIXELS = 50_000_000L;
    private static final float QUALITY = 0.85f;

    record Result(byte[] jpeg, int width, int height) {}

    static {
        ImageIO.scanForPlugins();
    }

    private ImageProcessor() {}

    /** Registers the WebP reader from the startup thread, whose class loader can see the nested jars. */
    static void warmUp() {}

    static Result toJpeg(Path file) {
        BufferedImage src = decode(file);
        int w = src.getWidth(), h = src.getHeight();
        double scale = Math.min(1.0, (double) LONG_EDGE / Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * scale)), th = Math.max(1, (int) Math.round(h * scale));
        BufferedImage img = src;
        // Halve step by step first: one big bicubic jump aliases badly.
        while (img.getWidth() / 2 >= tw && img.getHeight() / 2 >= th) img = draw(img, img.getWidth() / 2, img.getHeight() / 2);
        img = draw(img, tw, th);
        return new Result(encode(img), tw, th);
    }

    private static BufferedImage decode(Path file) {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) throw new DomainException(ErrorCode.PHOTO_TYPE);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new DomainException(ErrorCode.PHOTO_TYPE, "只支持 JPEG、PNG、WebP");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > MAX_PIXELS) throw new DomainException(ErrorCode.PHOTO_TOO_LARGE, "图片尺寸太大");
                BufferedImage img = reader.read(0);
                if (img == null) throw new DomainException(ErrorCode.PHOTO_TYPE);
                return img;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof DomainException d) throw d;
            throw new DomainException(ErrorCode.PHOTO_TYPE, "图片无法解码");
        }
    }

    private static BufferedImage draw(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] encode(BufferedImage img) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(img, null, null), p);
        } catch (IOException e) {
            throw new IllegalStateException("jpeg encode failed", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}

package com.jingxuan.plant.storage;

import com.jingxuan.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 本地图片存储测试（V3 §71 PlantMediaStorageTest）。 */
class PlantMediaStorageTest {

    private Path root;
    private LocalPlantPhotoStorageService storage;

    @BeforeEach
    void setUp() throws Exception {
        root = Files.createTempDirectory("plant-media-test");
        PlantStorageProperties properties = new PlantStorageProperties();
        properties.setRoot(root.toString());
        storage = new LocalPlantPhotoStorageService(properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        try (var stream = Files.walk(root)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // ignore
                }
            });
        }
    }

    private byte[] imageBytes(String format) throws Exception {
        BufferedImage image = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    @Test
    void saveWritesOriginalThumbnailAndUrls() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "ginkgo.jpg", "image/jpeg", imageBytes("jpg"));
        PlantPhotoStorageService.StoredPlantPhoto stored = storage.save(9281L, file);

        assertTrue(stored.originalUrl().startsWith("/media/plants/original/9281/"), stored.originalUrl());
        assertTrue(stored.originalUrl().endsWith(".jpg"));
        assertTrue(stored.thumbnailUrl().startsWith("/media/plants/thumbnail/9281/"), stored.thumbnailUrl());
        Path original = root.resolve("original/9281").resolve(stored.originalUrl().substring(stored.originalUrl().lastIndexOf("/") + 1));
        Path thumbnail = root.resolve("thumbnail/9281").resolve(stored.thumbnailUrl().substring(stored.thumbnailUrl().lastIndexOf("/") + 1));
        assertTrue(Files.isRegularFile(original), "原图应落盘");
        assertTrue(Files.isRegularFile(thumbnail), "缩略图应落盘");
        assertTrue(storage.existsByUrl(stored.originalUrl()));
        assertTrue(storage.existsByUrl(stored.thumbnailUrl()));
        assertEquals(1, storage.countOriginals(9281L));
    }

    @Test
    void rejectsInvalidExtensionAndMagicMismatch() throws Exception {
        MockMultipartFile badExt = new MockMultipartFile("file", "evil.exe", "application/octet-stream", imageBytes("jpg"));
        assertThrows(BusinessException.class, () -> storage.save(1L, badExt));

        // png 内容伪装成 jpg：魔数检测应拦截
        MockMultipartFile spoof = new MockMultipartFile("file", "fake.jpg", "image/jpeg", imageBytes("png"));
        assertThrows(BusinessException.class, () -> storage.save(1L, spoof));
    }

    @Test
    void rejectsOversizeImage() throws Exception {
        byte[] huge = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", huge);
        assertThrows(BusinessException.class, () -> storage.save(1L, file));
    }

    @Test
    void deleteFilesAndObservationCleanup() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "leaf.png", "image/png", imageBytes("png"));
        PlantPhotoStorageService.StoredPlantPhoto stored = storage.save(7L, file);
        assertTrue(storage.existsByUrl(stored.originalUrl()));

        String fileName = stored.originalUrl().substring(stored.originalUrl().lastIndexOf("/") + 1);
        storage.deleteFiles(7L, fileName);
        assertFalse(storage.existsByUrl(stored.originalUrl()));
        assertFalse(storage.existsByUrl(stored.thumbnailUrl()));
        assertFalse(Files.isDirectory(root.resolve("original/7")), "空目录应被清理");

        MockMultipartFile file2 = new MockMultipartFile("file", "a.jpg", "image/jpeg", imageBytes("jpg"));
        storage.save(8L, file2);
        storage.deleteObservationFiles(8L);
        assertEquals(0, storage.countOriginals(8L));
    }


    @Test
    void rejectsImageBombOversizedDimensions() throws Exception {
        // 伪造 PNG 头部：宽 30000 x 高 20000（IHDR），魔数仍是 PNG
        byte[] png = new byte[64];
        byte[] signature = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
        System.arraycopy(signature, 0, png, 0, 8);
        png[12] = 13; png[15] = 0x49; png[16] = 0x48; png[17] = 0x44; png[18] = 0x52; // IHDR
        putInt(png, 16 + 8, 30000);
        putInt(png, 16 + 12, 20000);
        MockMultipartFile bomb = new MockMultipartFile("file", "bomb.png", "image/png", png);
        assertThrows(BusinessException.class, () -> storage.save(1L, bomb), "超大尺寸图片应被拦截");
    }

    @Test
    void dimensionParserReadsRealPngHeader() throws Exception {
        byte[] bytes = imageBytes("png");
        int[] dims = LocalPlantPhotoStorageService.readImageDimensions(bytes, bytes.length, "png");
        assertTrue(dims != null && dims[0] == 64 && dims[1] == 48, "应解析出 64x48");
    }

    private static void putInt(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value >> 24);
        buffer[offset + 1] = (byte) (value >> 16);
        buffer[offset + 2] = (byte) (value >> 8);
        buffer[offset + 3] = (byte) value;
    }

    @Test
    void deleteMissingFilesIsIdempotent() {
        storage.deleteFiles(999L, "nope.jpg");
        storage.deleteObservationFiles(999L);
    }

    @Test
    void acceptsWebpAndJpegExtensions() throws Exception {
        // WebP（RIFF/WEBP + VP8X 头，1200x800）：旧域白名单曾拒绝 webp，V4 修复后应可上传
        byte[] webp = new byte[64];
        webp[0] = 'R'; webp[1] = 'I'; webp[2] = 'F'; webp[3] = 'F';
        webp[8] = 'W'; webp[9] = 'E'; webp[10] = 'B'; webp[11] = 'P';
        webp[12] = 'V'; webp[13] = 'P'; webp[14] = '8'; webp[15] = 'X';
        putIntLe24(webp, 24, 1200 - 1);
        putIntLe24(webp, 27, 800 - 1);
        MockMultipartFile webpFile = new MockMultipartFile("file", "photo.webp", "image/webp", webp);
        PlantPhotoStorageService.StoredPlantPhoto storedWebp = storage.save(31L, webpFile);
        assertTrue(storedWebp.originalUrl().endsWith(".webp"), storedWebp.originalUrl());
        assertEquals(storedWebp.originalUrl(), storedWebp.thumbnailUrl(), "webp 不生成缩略图时回退原图 URL");
        assertTrue(storage.existsByUrl(storedWebp.originalUrl()));

        // .jpeg 扩展名同样曾被旧白名单拒绝
        MockMultipartFile jpegFile = new MockMultipartFile("file", "leaf.jpeg", "image/jpeg", imageBytes("jpg"));
        PlantPhotoStorageService.StoredPlantPhoto storedJpeg = storage.save(32L, jpegFile);
        assertTrue(storedJpeg.originalUrl().endsWith(".jpeg"), storedJpeg.originalUrl());
        assertTrue(storage.existsByUrl(storedJpeg.thumbnailUrl()), "jpeg 应生成缩略图");
    }

    @Test
    void webpDimensionParserReadsVp8xHeader() {
        byte[] webp = new byte[64];
        webp[0] = 'R'; webp[1] = 'I'; webp[2] = 'F'; webp[3] = 'F';
        webp[8] = 'W'; webp[9] = 'E'; webp[10] = 'B'; webp[11] = 'P';
        webp[12] = 'V'; webp[13] = 'P'; webp[14] = '8'; webp[15] = 'X';
        putIntLe24(webp, 24, 1200 - 1);
        putIntLe24(webp, 27, 800 - 1);
        int[] dims = LocalPlantPhotoStorageService.readImageDimensions(webp, webp.length, "webp");
        assertTrue(dims != null && dims[0] == 1200 && dims[1] == 800,
                "应解析出 webp 1200x800，实际 " + (dims == null ? "null" : dims[0] + "x" + dims[1]));
    }

    private static void putIntLe24(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value & 0xff);
        buffer[offset + 1] = (byte) ((value >> 8) & 0xff);
        buffer[offset + 2] = (byte) ((value >> 16) & 0xff);
    }
}
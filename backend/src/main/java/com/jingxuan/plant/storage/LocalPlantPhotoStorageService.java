package com.jingxuan.plant.storage;

import com.jingxuan.exception.BusinessException;
import com.jingxuan.util.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 本地磁盘实现：uuid 文件 + 原子移动 + 缩略图（jpg/png 生成，其余回退原图）。 */
@Slf4j
@Service
public class LocalPlantPhotoStorageService implements PlantPhotoStorageService {

    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final long IMAGE_MAX_SIZE = 10L * 1024 * 1024;
    private static final int THUMBNAIL_EDGE = 320;
    private static final int MAX_IMAGE_DIMENSION = 10000;
    private static final long MAX_IMAGE_PIXELS = 50_000_000L;
    private static final int HEADER_BYTES = 4096;

    private final PlantStorageProperties properties;

    public LocalPlantPhotoStorageService(PlantStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredPlantPhoto save(Long observationId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("图片文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new BusinessException("文件名无效");
        }
        // 植物域使用自身图片白名单（jpg/jpeg/png/webp/gif），不复用旧域附件白名单（其不含 webp/jpeg）
        String ext = FileUtil.getExtension(originalName).toLowerCase();
        if (!IMAGE_EXTS.contains(ext)) {
            throw new BusinessException("仅支持 JPG/JPEG/PNG/WebP/GIF 图片");
        }
        if (file.getSize() > IMAGE_MAX_SIZE) {
            throw new BusinessException("图片文件不能超过10MB");
        }
        try (InputStream in = file.getInputStream()) {
            String realType = FileUtil.detectRealType(in);
            if (!imageTypeCompatible(realType, ext)) {
                throw new BusinessException("文件内容与扩展名不匹配，已拦截");
            }
        } catch (IOException e) {
            throw new BusinessException("图片读取失败，请重试");
        }

        Path originalDir = properties.originalPath().resolve(String.valueOf(observationId));
        Path thumbnailDir = properties.thumbnailPath().resolve(String.valueOf(observationId));
        Path tmpDir = properties.rootPath().resolve(".tmp");
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String fileName = uuid + "." + ext;

        Path tmpOriginal = null;
        try {
            Files.createDirectories(originalDir);
            Files.createDirectories(thumbnailDir);
            Files.createDirectories(tmpDir);

            // 1) 原图：临时文件 + 原子移动
            tmpOriginal = Files.createTempFile(tmpDir, "up-", ".tmp");
            file.transferTo(tmpOriginal);
            validateImageDimensions(tmpOriginal, ext);
            Path targetOriginal = originalDir.resolve(fileName);
            Files.move(tmpOriginal, targetOriginal, StandardCopyOption.ATOMIC_MOVE);
            makeWorldReadable(targetOriginal);

            // 2) 缩略图：jpg/png 可解码则生成；否则回退原图 URL
            String thumbName = uuid + ".jpg";
            String thumbnailUrl = publicUrl("thumbnail", observationId, thumbName);
            Path targetThumbnail = thumbnailDir.resolve(thumbName);
            boolean thumbWritten = false;
            if (("jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext))) {
                thumbWritten = writeThumbnail(targetOriginal, targetThumbnail);
            }
            if (!thumbWritten) {
                Files.deleteIfExists(targetThumbnail);
                thumbnailUrl = publicUrl("original", observationId, fileName);
            }

            String originalUrl = publicUrl("original", observationId, fileName);
            return new StoredPlantPhoto(originalUrl, thumbnailUrl, fileName);
        } catch (IOException e) {
            throw new BusinessException("图片保存失败: " + e.getMessage());
        } finally {
            if (tmpOriginal != null) {
                try {
                    Files.deleteIfExists(tmpOriginal);
                } catch (IOException ignored) {
                    // 已移动或不存在，忽略
                }
            }
        }
    }

    private boolean writeThumbnail(Path source, Path target) throws IOException {
        BufferedImage image = ImageIO.read(source.toFile());
        if (image == null) {
            return false;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return false;
        }
        double scale = Math.min(1.0, THUMBNAIL_EDGE / (double) Math.max(width, height));
        int tw = Math.max(1, (int) Math.round(width * scale));
        int th = Math.max(1, (int) Math.round(height * scale));
        BufferedImage thumb = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, tw, th, null);
        } finally {
            g.dispose();
        }
        Path tmpThumb = target.resolveSibling(target.getFileName() + ".tmp");
        boolean written = ImageIO.write(thumb, "jpg", tmpThumb.toFile());
        if (!written) {
            return false;
        }
        Files.move(tmpThumb, target, StandardCopyOption.ATOMIC_MOVE);
        makeWorldReadable(target);
        return true;
    }

    /** 图片域内的严格魔数匹配：jpg/jpeg 互为别名，其余必须与声明扩展名一致；无法识别时放行。 */
    private boolean imageTypeCompatible(String realType, String declaredExt) {
        if (realType == null) {
            return true;
        }
        String type = realType.toLowerCase();
        String ext = declaredExt.toLowerCase();
        if (("jpg".equals(type) && "jpeg".equals(ext)) || ("jpeg".equals(type) && "jpg".equals(ext))) {
            return true;
        }
        return type.equals(ext) || ("jfif".equals(type) && "jpg".equals(ext));
    }

    /** 图片炸弹防护（V4 §51）：只解析头部尺寸，不解码全图；超限直接拒绝。 */
    private void validateImageDimensions(Path file, String ext) throws IOException {
        byte[] head = new byte[HEADER_BYTES];
        int read = 0;
        try (var in = Files.newInputStream(file)) {
            read = in.read(head);
        }
        int[] dims = readImageDimensions(head, Math.max(0, read), ext);
        if (dims == null) {
            throw new BusinessException("无法识别图片尺寸，已拦截");
        }
        long pixels = (long) dims[0] * dims[1];
        if (dims[0] > MAX_IMAGE_DIMENSION || dims[1] > MAX_IMAGE_DIMENSION) {
            throw new BusinessException("图片尺寸过大，宽度/高度不能超过" + MAX_IMAGE_DIMENSION + "像素");
        }
        if (pixels > MAX_IMAGE_PIXELS) {
            throw new BusinessException("图片像素总量过大，请压缩后重试");
        }
    }

    static int[] readImageDimensions(byte[] head, int length, String ext) {
        if (length < 12) {
            return null;
        }
        String type = ext == null ? "" : ext.toLowerCase();
        try {
            if (type.equals("png") && length >= 24 && head[0] == (byte) 0x89 && head[1] == 0x50) {
                int w = ((head[16] & 0xff) << 24) | ((head[17] & 0xff) << 16) | ((head[18] & 0xff) << 8) | (head[19] & 0xff);
                int h = ((head[20] & 0xff) << 24) | ((head[21] & 0xff) << 16) | ((head[22] & 0xff) << 8) | (head[23] & 0xff);
                return w > 0 && h > 0 ? new int[]{w, h} : null;
            }
            if ((type.equals("jpg") || type.equals("jpeg")) && head[0] == (byte) 0xff && head[1] == (byte) 0xd8) {
                int idx = 2;
                while (idx + 9 < length) {
                    if (head[idx] != (byte) 0xff) {
                        idx++;
                        continue;
                    }
                    int marker = head[idx + 1] & 0xff;
                    if (marker >= 0xc0 && marker <= 0xcf && marker != 0xc4 && marker != 0xc8 && marker != 0xcc) {
                        int h = ((head[idx + 5] & 0xff) << 8) | (head[idx + 6] & 0xff);
                        int w = ((head[idx + 7] & 0xff) << 8) | (head[idx + 8] & 0xff);
                        return w > 0 && h > 0 ? new int[]{w, h} : null;
                    }
                    int segLen = ((head[idx + 2] & 0xff) << 8) | (head[idx + 3] & 0xff);
                    if (segLen < 2) {
                        return null;
                    }
                    idx += 2 + segLen;
                }
                return null;
            }
            if (type.equals("gif") && length >= 10) {
                int w = (head[6] & 0xff) | ((head[7] & 0xff) << 8);
                int h = (head[8] & 0xff) | ((head[9] & 0xff) << 8);
                return w > 0 && h > 0 ? new int[]{w, h} : null;
            }
            if (type.equals("webp") && length >= 30) {
                if (head[0] != 0x52 || head[1] != 0x49 || head[2] != 0x46 || head[3] != 0x46) {
                    return null;
                }
                int chunkType = ((head[12] & 0xff) << 24) | ((head[13] & 0xff) << 16) | ((head[14] & 0xff) << 8) | (head[15] & 0xff);
                if (chunkType == 0x56503858 && length >= 30) { // VP8X
                    int w = ((head[24] & 0xff) | ((head[25] & 0xff) << 8) | ((head[26] & 0xff) << 16)) + 1;
                    int h = ((head[27] & 0xff) | ((head[28] & 0xff) << 8) | ((head[29] & 0xff) << 16)) + 1;
                    return w > 0 && h > 0 ? new int[]{w, h} : null;
                }
                if (chunkType == 0x56503820 && length >= 30) { // VP8 lossy
                    int w = (head[26] & 0xff) | ((head[27] & 0xff) << 8);
                    int h = (head[28] & 0xff) | ((head[29] & 0xff) << 8);
                    w = w & 0x3fff;
                    h = h & 0x3fff;
                    return w > 0 && h > 0 ? new int[]{w, h} : null;
                }
                if (chunkType == 0x5650384c && length >= 25) { // VP8L
                    int bits = (head[21] & 0xff) | ((head[22] & 0xff) << 8) | ((head[23] & 0xff) << 16) | ((head[24] & 0xff) << 24);
                    int w = (bits & 0x3fff) + 1;
                    int h = ((bits >> 14) & 0x3fff) + 1;
                    return w > 0 && h > 0 ? new int[]{w, h} : null;
                }
                return null;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    /** 保证 nginx(其他 uid) 可读：显式 644。 */
    private void makeWorldReadable(Path path) {
        try {
            Files.setPosixFilePermissions(path,
                    java.util.EnumSet.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                            java.nio.file.attribute.PosixFilePermission.GROUP_READ,
                            java.nio.file.attribute.PosixFilePermission.OTHERS_READ));
        } catch (UnsupportedOperationException | java.io.IOException ignored) {
            path.toFile().setReadable(true, false);
        }
    }

    private String publicUrl(String kind, Long observationId, String fileName) {
        return properties.getPublicPrefix() + "/" + kind + "/" + observationId + "/" + fileName;
    }

    @Override
    public void deleteFiles(Long observationId, String storedFileName) {
        if (storedFileName == null) {
            return;
        }
        safeDelete(properties.originalPath().resolve(String.valueOf(observationId)).resolve(storedFileName));
        // 缩略图统一为 .jpg 文件名
        int dot = storedFileName.lastIndexOf('.');
        String thumbName = (dot >= 0 ? storedFileName.substring(0, dot) : storedFileName) + ".jpg";
        safeDelete(properties.thumbnailPath().resolve(String.valueOf(observationId)).resolve(thumbName));
        deleteEmptyDir(properties.originalPath().resolve(String.valueOf(observationId)));
        deleteEmptyDir(properties.thumbnailPath().resolve(String.valueOf(observationId)));
    }

    @Override
    public void deleteObservationFiles(Long observationId) {
        deleteRecursively(properties.originalPath().resolve(String.valueOf(observationId)));
        deleteRecursively(properties.thumbnailPath().resolve(String.valueOf(observationId)));
    }

    @Override
    public boolean existsByUrl(String publicUrl) {
        if (publicUrl == null || !publicUrl.startsWith(properties.getPublicPrefix())) {
            return false;
        }
        String relative = publicUrl.substring(properties.getPublicPrefix().length());
        return Files.isRegularFile(properties.rootPath().resolve(relative.startsWith("/") ? relative.substring(1) : relative));
    }

    @Override
    public long countOriginals(Long observationId) {
        Path dir = properties.originalPath().resolve(String.valueOf(observationId));
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            long count = 0;
            for (Path ignored : stream) {
                count++;
            }
            return count;
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public List<Long> listObservationIds() {
        List<Long> ids = new ArrayList<>();
        Path dir = properties.originalPath();
        if (!Files.isDirectory(dir)) {
            return ids;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                try {
                    ids.add(Long.valueOf(child.getFileName().toString()));
                } catch (NumberFormatException ignored) {
                    // 非观察目录，跳过
                }
            }
        } catch (IOException e) {
            log.warn("扫描图片目录失败: {}", e.getMessage());
        }
        return ids;
    }

    @Override
    public Path rootPath() {
        return properties.rootPath();
    }

    private void safeDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除图片失败: {}", path);
        }
    }

    private void deleteEmptyDir(Path dir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            if (!stream.iterator().hasNext()) {
                Files.deleteIfExists(dir);
            }
        } catch (IOException ignored) {
            // 目录不存在或非空
        }
    }

    private void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("清理图片目录失败: {}", p);
                }
            });
        } catch (IOException ignored) {
            // 忽略
        }
    }
}
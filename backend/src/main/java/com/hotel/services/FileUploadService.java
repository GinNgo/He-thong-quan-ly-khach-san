package com.hotel.services;

import com.hotel.entities.User;
import com.hotel.exceptions.AvatarUploadException;
import com.hotel.exceptions.PropertyMediaException;
import com.hotel.repositories.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FileUploadService {

    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);
    private static final String PUBLIC_UPLOAD_PREFIX = "/api/public/uploads/";
    private static final Pattern SAFE_FILENAME = Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9._-]{0,240}\\.(?:jpg|jpeg|png|webp)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MANAGED_URL = Pattern.compile(
            "^" + Pattern.quote(PUBLIC_UPLOAD_PREFIX)
                    + "([A-Za-z0-9][A-Za-z0-9._-]{0,240}\\.(?:jpg|jpeg|png|webp))$",
            Pattern.CASE_INSENSITIVE);

    private final UserRepository userRepository;
    private final String uploadPath;
    private final long maxImageSize;
    private final int maxWidth;
    private final int maxHeight;
    private final long maxPixels;
    private Path rootLocation;

    public FileUploadService(
            UserRepository userRepository,
            @Value("${upload.path:uploads}") String uploadPath,
            @Value("${upload.avatar.max-file-size-bytes:5242880}") long maxImageSize,
            @Value("${upload.avatar.max-width:4096}") int maxWidth,
            @Value("${upload.avatar.max-height:4096}") int maxHeight,
            @Value("${upload.avatar.max-pixels:16777216}") long maxPixels) {
        this.userRepository = userRepository;
        this.uploadPath = uploadPath;
        this.maxImageSize = maxImageSize;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.maxPixels = maxPixels;
    }

    @PostConstruct
    public void init() {
        if (uploadPath == null || uploadPath.isBlank()
                || maxImageSize <= 0 || maxWidth <= 0 || maxHeight <= 0 || maxPixels <= 0) {
            throw new IllegalStateException("Avatar storage configuration is invalid.");
        }
        rootLocation = Paths.get(uploadPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize avatar storage.", exception);
        }
    }

    @Transactional
    public StoredAvatar replaceAvatar(Long userId, MultipartFile file) {
        if (userId == null) {
            throw AvatarUploadException.userNotFound();
        }
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(AvatarUploadException::userNotFound);
        StoredAvatar storedAvatar = storeValidatedAvatar(userId, file);
        String previousAvatarUrl = user.getAvatarUrl();

        try {
            user.setAvatarUrl(storedAvatar.url());
            userRepository.saveAndFlush(user);
        } catch (RuntimeException exception) {
            deleteManagedAvatar(storedAvatar.url());
            throw exception;
        }

        scheduleLifecycleCleanup(previousAvatarUrl, storedAvatar.url());
        return storedAvatar;
    }

    public StoredImage storePropertyImage(Long propertyId, MultipartFile file) {
        if (propertyId == null) {
            throw new IllegalArgumentException("Property id is required.");
        }
        try {
            return storeValidatedImage(
                    "property-" + propertyId + "-",
                    ".property-",
                    file);
        } catch (AvatarUploadException exception) {
            throw PropertyMediaException.fromUpload(exception);
        }
    }

    public StoredImageResource loadImageResource(String filename) {
        Path imagePath = resolveSafeFilename(filename);
        if (!Files.isRegularFile(imagePath) || !Files.isReadable(imagePath)) {
            throw AvatarUploadException.fileNotFound();
        }
        try {
            ImageMetadata metadata = inspectImage(Files.readAllBytes(imagePath), null);
            return new StoredImageResource(
                    new FileSystemResource(imagePath),
                    metadata.contentType(),
                    metadata.width(),
                    metadata.height());
        } catch (AvatarUploadException exception) {
            throw exception;
        } catch (IOException exception) {
            throw AvatarUploadException.storageFailure(exception);
        }
    }

    boolean deleteManagedAvatar(String publicUrl) {
        return deleteManagedImage(publicUrl);
    }

    public boolean deleteManagedImage(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return false;
        }
        Matcher matcher = MANAGED_URL.matcher(publicUrl.strip());
        if (!matcher.matches()) {
            return false;
        }
        Path target = resolveSafeFilename(matcher.group(1));
        try {
            return Files.deleteIfExists(target);
        } catch (IOException exception) {
            log.warn("Unable to delete replaced managed avatar filename={}", target.getFileName());
            return false;
        }
    }

    private StoredAvatar storeValidatedAvatar(Long userId, MultipartFile file) {
        StoredImage stored = storeValidatedImage("avatar-" + userId + "-", ".avatar-", file);
        return new StoredAvatar(stored.url(), stored.contentType(), stored.width(), stored.height());
    }

    private StoredImage storeValidatedImage(String filenamePrefix, String temporaryPrefix, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw AvatarUploadException.emptyFile();
        }
        if (file.getSize() > maxImageSize) {
            throw AvatarUploadException.tooLarge();
        }

        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0) {
                throw AvatarUploadException.emptyFile();
            }
            if (bytes.length > maxImageSize) {
                throw AvatarUploadException.tooLarge();
            }
            ImageMetadata metadata = inspectImage(bytes, file.getContentType());
            String filename = filenamePrefix + UUID.randomUUID() + metadata.extension();
            Path destination = resolveSafeFilename(filename);
            Path temporary = Files.createTempFile(rootLocation, temporaryPrefix, ".tmp");
            try {
                Files.write(temporary, bytes);
                moveAtomically(temporary, destination);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return new StoredImage(
                    PUBLIC_UPLOAD_PREFIX + filename,
                    metadata.contentType(),
                    metadata.width(),
                    metadata.height(),
                    bytes.length,
                    sha256(bytes),
                    filename);
        } catch (AvatarUploadException exception) {
            throw exception;
        } catch (IOException exception) {
            throw AvatarUploadException.storageFailure(exception);
        }
    }

    private ImageMetadata inspectImage(byte[] bytes, String declaredContentType) {
        ImageMetadata metadata;
        if (isPng(bytes)) {
            metadata = readPngMetadata(bytes);
        } else if (isJpeg(bytes)) {
            metadata = readJpegMetadata(bytes);
        } else if (isWebp(bytes)) {
            metadata = readWebpMetadata(bytes);
        } else {
            throw AvatarUploadException.invalidSignature();
        }

        if (declaredContentType != null && !declaredContentType.isBlank()
                && !metadata.acceptedContentTypes().contains(
                        declaredContentType.strip().toLowerCase(Locale.ROOT))) {
            throw AvatarUploadException.contentTypeMismatch();
        }
        validateDimensions(metadata.width(), metadata.height());
        if (!"image/webp".equals(metadata.contentType())) {
            verifyImageIoDecode(bytes, metadata);
        }
        return metadata;
    }

    private ImageMetadata readPngMetadata(byte[] bytes) {
        if (bytes.length < 24 || !matchesAscii(bytes, 12, "IHDR")) {
            throw AvatarUploadException.invalidSignature();
        }
        int width = readBigEndianInt(bytes, 16);
        int height = readBigEndianInt(bytes, 20);
        return new ImageMetadata(width, height, "image/png", ".png", Set.of("image/png"));
    }

    private ImageMetadata readJpegMetadata(byte[] bytes) {
        int offset = 2;
        while (offset + 3 < bytes.length) {
            while (offset < bytes.length && unsigned(bytes[offset]) == 0xFF) {
                offset++;
            }
            if (offset >= bytes.length) {
                break;
            }
            int marker = unsigned(bytes[offset++]);
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                continue;
            }
            if (marker == 0xD9 || marker == 0xDA || offset + 1 >= bytes.length) {
                break;
            }
            int segmentLength = readUnsignedShort(bytes, offset);
            if (segmentLength < 2 || offset + segmentLength > bytes.length) {
                throw AvatarUploadException.invalidSignature();
            }
            if (isStartOfFrame(marker)) {
                if (segmentLength < 7) {
                    throw AvatarUploadException.invalidSignature();
                }
                int height = readUnsignedShort(bytes, offset + 3);
                int width = readUnsignedShort(bytes, offset + 5);
                return new ImageMetadata(
                        width,
                        height,
                        "image/jpeg",
                        ".jpg",
                        Set.of("image/jpeg", "image/jpg", "image/pjpeg"));
            }
            offset += segmentLength;
        }
        throw AvatarUploadException.invalidSignature();
    }

    private ImageMetadata readWebpMetadata(byte[] bytes) {
        if (bytes.length < 30 || readLittleEndianUnsignedInt(bytes, 4) + 8L != bytes.length) {
            throw AvatarUploadException.invalidSignature();
        }
        String chunk = ascii(bytes, 12, 4);
        long chunkSize = readLittleEndianUnsignedInt(bytes, 16);
        if (chunkSize < 1 || 20L + chunkSize > bytes.length) {
            throw AvatarUploadException.invalidSignature();
        }

        int width;
        int height;
        switch (chunk) {
            case "VP8X" -> {
                if (chunkSize < 10) throw AvatarUploadException.invalidSignature();
                width = 1 + readUnsigned24LittleEndian(bytes, 24);
                height = 1 + readUnsigned24LittleEndian(bytes, 27);
            }
            case "VP8L" -> {
                if (chunkSize < 5 || unsigned(bytes[20]) != 0x2F) {
                    throw AvatarUploadException.invalidSignature();
                }
                int b1 = unsigned(bytes[21]);
                int b2 = unsigned(bytes[22]);
                int b3 = unsigned(bytes[23]);
                int b4 = unsigned(bytes[24]);
                width = 1 + (((b2 & 0x3F) << 8) | b1);
                height = 1 + (((b4 & 0x0F) << 10) | (b3 << 2) | ((b2 & 0xC0) >> 6));
            }
            case "VP8 " -> {
                if (chunkSize < 10
                        || unsigned(bytes[23]) != 0x9D
                        || unsigned(bytes[24]) != 0x01
                        || unsigned(bytes[25]) != 0x2A) {
                    throw AvatarUploadException.invalidSignature();
                }
                width = readUnsignedShortLittleEndian(bytes, 26) & 0x3FFF;
                height = readUnsignedShortLittleEndian(bytes, 28) & 0x3FFF;
            }
            default -> throw AvatarUploadException.invalidSignature();
        }
        return new ImageMetadata(width, height, "image/webp", ".webp", Set.of("image/webp"));
    }

    private void verifyImageIoDecode(byte[] bytes, ImageMetadata metadata) {
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            if (decoded == null
                    || decoded.getWidth() != metadata.width()
                    || decoded.getHeight() != metadata.height()) {
                throw AvatarUploadException.invalidSignature();
            }
        } catch (IOException exception) {
            throw AvatarUploadException.invalidSignature();
        }
    }

    private void validateDimensions(int width, int height) {
        long pixels = (long) width * height;
        if (width <= 0 || height <= 0
                || width > maxWidth || height > maxHeight
                || pixels <= 0 || pixels > maxPixels) {
            throw AvatarUploadException.invalidDimensions();
        }
    }

    private Path resolveSafeFilename(String filename) {
        if (filename == null || !SAFE_FILENAME.matcher(filename).matches()) {
            throw AvatarUploadException.fileNotFound();
        }
        Path resolved = rootLocation.resolve(filename).normalize().toAbsolutePath();
        if (!resolved.getParent().equals(rootLocation)) {
            throw AvatarUploadException.fileNotFound();
        }
        return resolved;
    }

    private void scheduleLifecycleCleanup(String previousAvatarUrl, String newAvatarUrl) {
        if (previousAvatarUrl == null || previousAvatarUrl.equals(newAvatarUrl)) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteManagedAvatar(previousAvatarUrl);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteManagedAvatar(previousAvatarUrl);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteManagedAvatar(newAvatarUrl);
                }
            }
        });
    }

    private void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean isPng(byte[] bytes) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (bytes.length < signature.length) return false;
        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) return false;
        }
        return true;
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 4
                && unsigned(bytes[0]) == 0xFF
                && unsigned(bytes[1]) == 0xD8
                && unsigned(bytes[2]) == 0xFF;
    }

    private boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && matchesAscii(bytes, 0, "RIFF")
                && matchesAscii(bytes, 8, "WEBP");
    }

    private boolean isStartOfFrame(int marker) {
        return switch (marker) {
            case 0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7,
                    0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF -> true;
            default -> false;
        };
    }

    private int readBigEndianInt(byte[] bytes, int offset) {
        if (offset < 0 || offset + 4 > bytes.length) {
            throw AvatarUploadException.invalidSignature();
        }
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    private long readLittleEndianUnsignedInt(byte[] bytes, int offset) {
        if (offset < 0 || offset + 4 > bytes.length) {
            throw AvatarUploadException.invalidSignature();
        }
        return Integer.toUnsignedLong(
                ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
    }

    private int readUnsignedShort(byte[] bytes, int offset) {
        if (offset < 0 || offset + 2 > bytes.length) {
            throw AvatarUploadException.invalidSignature();
        }
        return (unsigned(bytes[offset]) << 8) | unsigned(bytes[offset + 1]);
    }

    private int readUnsignedShortLittleEndian(byte[] bytes, int offset) {
        if (offset < 0 || offset + 2 > bytes.length) {
            throw AvatarUploadException.invalidSignature();
        }
        return unsigned(bytes[offset]) | (unsigned(bytes[offset + 1]) << 8);
    }

    private int readUnsigned24LittleEndian(byte[] bytes, int offset) {
        if (offset < 0 || offset + 3 > bytes.length) {
            throw AvatarUploadException.invalidSignature();
        }
        return unsigned(bytes[offset])
                | (unsigned(bytes[offset + 1]) << 8)
                | (unsigned(bytes[offset + 2]) << 16);
    }

    private boolean matchesAscii(byte[] bytes, int offset, String expected) {
        if (offset < 0 || offset + expected.length() > bytes.length) return false;
        for (int index = 0; index < expected.length(); index++) {
            if (unsigned(bytes[offset + index]) != expected.charAt(index)) return false;
        }
        return true;
    }

    private String ascii(byte[] bytes, int offset, int length) {
        if (offset < 0 || offset + length > bytes.length) {
            throw AvatarUploadException.invalidSignature();
        }
        StringBuilder value = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            value.append((char) unsigned(bytes[offset + index]));
        }
        return value.toString();
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private record ImageMetadata(
            int width,
            int height,
            String contentType,
            String extension,
            Set<String> acceptedContentTypes) {
    }

    public record StoredAvatar(String url, String contentType, int width, int height) {
    }

    public record StoredImage(
            String url,
            String contentType,
            int width,
            int height,
            long sizeBytes,
            String checksumSha256,
            String storageKey) {
    }

    public record StoredImageResource(Resource resource, String contentType, int width, int height) {
    }
}

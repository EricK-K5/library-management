package com.example.library_management.service;

import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    public static final String URL_PREFIX = "/uploads/";
    public static final String AVATAR_FOLDER = "avatars";
    public static final String BOOK_FOLDER = "books";

    public static final long AVATAR_MAX_BYTES = 2L * 1024 * 1024; // 2MB
    public static final long COVER_MAX_BYTES = 5L * 1024 * 1024;  // 5MB

    @Getter
    private final Path root;

    public FileStorageService(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(root.resolve(AVATAR_FOLDER));
        Files.createDirectories(root.resolve(BOOK_FOLDER));
    }

    /**
     * Validate + luu file, tra ve URL tuong doi de luu vao DB.
     */
    public String store(MultipartFile file, String folder, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.FILE_EMPTY);
        }
        if (file.getSize() > maxBytes) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }

        // Dua vao noi dung that (magic bytes), khong tin ten file / content-type FE gui len
        String extension;
        try (InputStream in = file.getInputStream()) {
//            Doc 12 file de xac dinh dinh dang file
            extension = detectExtension(in.readNBytes(12));
        } catch (IOException e) {
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }
        if (extension == null) {
            throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }

        String filename = UUID.randomUUID() + "." + extension;
        Path target = root.resolve(folder).resolve(filename).normalize();
//        bat buoc phai nam trong root
        if (!target.startsWith(root)) {
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Cannot store file", e);
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }
        String url = URL_PREFIX + folder + "/" + filename;

        // File da ghi ra dia ngay, nhung DB co the rollback sau do (loi o buoc khac trong cung transaction):
        // khi do xoa file moi de khong thanh file mo coi. (File CU thi deleteAfterCommit lo.)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        delete(url);
                    }
                }
            });
        }
        return url;
    }


    public void delete(String url) {
        if (!StringUtils.hasText(url) || !url.startsWith(URL_PREFIX)) {
            return;
        }
        Path target = root.resolve(url.substring(URL_PREFIX.length())).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Cannot delete file {}", target, e);
        }
    }
    
    public void deleteAfterCommit(String url) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete(url);
                }
            });
        } else {
            delete(url);
        }
    }

    private String detectExtension(byte[] h) {
        if (h.length >= 3
                && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (h.length >= 8
                && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) {
            return "png";
        }
        if (h.length >= 12
                && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return "webp";
        }
        return null;
    }
}

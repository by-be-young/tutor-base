package com.tutorbase.courseware.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 本地文件实现：{@code <root>/<teacherId>/blobs/<sha256>.md} 与
 * {@code <root>/<teacherId>/manifests/<subject>/v<version>.json}。
 *
 * <p>用于本地开发与测试；生产换对象存储实现即可，接口不变。
 */
public class LocalFileStorage implements CoursewareStorage {

    private final Path root;

    public LocalFileStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public String putBlob(long teacherId, byte[] content) {
        String hash = sha256(content);
        Path target = blobPath(teacherId, hash);
        try {
            Files.createDirectories(target.getParent());
            if (!Files.exists(target)) {
                Path temporary = Files.createTempFile(target.getParent(), "blob-", ".tmp");
                Files.write(temporary, content);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("写入课件正文失败", exception);
        }
        return hash;
    }

    @Override
    public byte[] getBlob(long teacherId, String contentHash) {
        Path path = blobPath(teacherId, contentHash);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new IllegalStateException("读取课件正文失败", exception);
        }
    }

    @Override
    public void putManifest(long teacherId, String subject, int version, byte[] json) {
        Path target = manifestPath(teacherId, subject, version);
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), "manifest-", ".tmp");
            Files.write(temporary, json);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("写入发布清单失败", exception);
        }
    }

    /** 内容哈希：{@code sha256:<hex>}。 */
    public static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("缺少 SHA-256 实现", exception);
        }
    }

    private Path blobPath(long teacherId, String contentHash) {
        String hex = contentHash.startsWith("sha256:") ? contentHash.substring("sha256:".length()) : contentHash;
        if (!hex.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("非法的内容哈希");
        }
        return root.resolve(Long.toString(teacherId)).resolve("blobs").resolve(hex + ".md").normalize();
    }

    private Path manifestPath(long teacherId, String subject, int version) {
        if (!subject.matches("[^/\\\\:*?\"<>|]{1,60}")) {
            throw new IllegalArgumentException("非法的学科名");
        }
        return root.resolve(Long.toString(teacherId))
                .resolve("manifests")
                .resolve(subject)
                .resolve("v" + version + ".json")
                .normalize();
    }
}

package com.tutorbase.courseware.storage;

/**
 * 正文与 manifest 的存储抽象。
 *
 * <p>内容寻址：正文按 sha256 存放，同一篇多次发布或复制到多个老师只占一份物理副本。
 * 本地开发用 {@link LocalFileStorage}，生产可换成 Supabase Storage / OSS 实现，调用方不变。
 */
public interface CoursewareStorage {

    /**
     * 写入正文 blob。
     *
     * @return 内容哈希，形如 {@code sha256:<hex>}
     */
    String putBlob(long teacherId, byte[] content);

    /** 读取正文 blob；不存在时返回 {@code null}。 */
    byte[] getBlob(long teacherId, String contentHash);

    /** 写入不可变发布清单。 */
    void putManifest(long teacherId, String subject, int version, byte[] json);
}

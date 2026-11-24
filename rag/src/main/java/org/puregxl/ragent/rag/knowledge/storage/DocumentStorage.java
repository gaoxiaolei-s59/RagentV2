package org.puregxl.ragent.rag.knowledge.storage;

import java.nio.file.Path;

/** 原文档对象存储接口，数据库只保存 storageKey，不保存文件二进制。 */
public interface DocumentStorage {
    /** 上传已生成校验值的临时文件，key 必须由应用生成且唯一。 */
    void upload(String storageKey, Path file, String contentType);

    /** 删除指定对象，用于数据库保存失败后的补偿。 */
    void delete(String storageKey);
}

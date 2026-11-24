package org.puregxl.ragent.rag.knowledge.storage;

import lombok.RequiredArgsConstructor;
import org.puregxl.ragent.framework.errorcode.BaseErrorCode;
import org.puregxl.ragent.framework.exception.ServiceException;
import org.puregxl.ragent.rag.knowledge.config.KnowledgeStorageProperties;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.file.Path;

/** RustFS 原文件存储；不修改桶的访问策略，不开启公开访问。 */
@RequiredArgsConstructor
public class RustFsDocumentStorage implements DocumentStorage {
    private final S3Client client;
    private final KnowledgeStorageProperties properties;

    @Override
    public void upload(String storageKey, Path file, String contentType) {
        try {
            ensureBucket();
            client.putObject(builder -> builder.bucket(properties.getBucket()).key(storageKey)
                .contentType(contentType), RequestBody.fromFile(file));
        } catch (RuntimeException exception) {
            throw new ServiceException("RustFS文件上传失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            client.deleteObject(builder -> builder.bucket(properties.getBucket()).key(storageKey));
        } catch (RuntimeException exception) {
            throw new ServiceException("RustFS文件清理失败", exception, BaseErrorCode.SERVICE_ERROR);
        }
    }

    /** 首次上传时确认桶存在；仅在 404 时创建，权限错误不误判为桶不存在。 */
    private void ensureBucket() {
        try {
            client.headBucket(builder -> builder.bucket(properties.getBucket()));
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw exception;
            }
            try {
                client.createBucket(builder -> builder.bucket(properties.getBucket()));
            } catch (S3Exception createFailure) {
                if (createFailure.statusCode() != 409) {
                    throw createFailure;
                }
                // 并发请求可能已创建同一桶，再次检查可访问性后继续。
                client.headBucket(builder -> builder.bucket(properties.getBucket()));
            }
        }
    }
}

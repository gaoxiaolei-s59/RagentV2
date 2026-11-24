package org.puregxl.ragent.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.ragent.framework.errorcode.BaseErrorCode;
import org.puregxl.ragent.framework.exception.ClientException;
import org.puregxl.ragent.framework.exception.ServiceException;
import org.puregxl.ragent.rag.knowledge.config.KnowledgeStorageProperties;
import org.puregxl.ragent.rag.knowledge.dto.request.KnowledgeDocumentUploadRequest;
import org.puregxl.ragent.rag.knowledge.dto.response.KnowledgeDocumentResponse;
import org.puregxl.ragent.rag.knowledge.entity.KnowledgeBase;
import org.puregxl.ragent.rag.knowledge.entity.KnowledgeDocument;
import org.puregxl.ragent.rag.knowledge.mapper.KnowledgeBaseMapper;
import org.puregxl.ragent.rag.knowledge.mapper.KnowledgeDocumentMapper;
import org.puregxl.ragent.rag.knowledge.service.KnowledgeDocumentService;
import org.puregxl.ragent.rag.knowledge.storage.DocumentStorage;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 上传阶段不持有数据库事务；对象成功保存后，再用短事务校验父级并写入元数据。
 */
@Slf4j
@Service
@Profile("mysql")
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentStorage storage;
    private final KnowledgeStorageProperties properties;
    private final PlatformTransactionManager transactionManager;

    @Override
    public KnowledgeDocumentResponse uploadKnowledgeDocument(Long knowledgeBaseId,
                                                             KnowledgeDocumentUploadRequest request, MultipartFile file) {
        KnowledgeBase base = requireBase(knowledgeBaseId, false);
        if (file == null || file.isEmpty()) {
            throw new ClientException("上传文件不能为空");
        }
        long maxSize = properties.getMaxFileSize().toBytes();
        if (maxSize <= 0 || file.getSize() > maxSize) {
            throw new ClientException("上传文件超过允许的大小：" + properties.getMaxFileSize());
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename) || filename.length() > 512 || filename.contains("/")
                || filename.contains("\\") || filename.indexOf('\0') >= 0) {
            throw new ClientException("文件名不合法或超过512个字符");
        }
        int dot = filename.lastIndexOf('.');
        String extension = dot > 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (properties.getAllowedExtensions().stream().noneMatch(item -> item.equalsIgnoreCase(extension))) {
            throw new ClientException("不支持的文档类型：" + extension);
        }
        KnowledgeDocumentUploadRequest options = request == null ? new KnowledgeDocumentUploadRequest() : request;
        String name = StringUtils.hasText(options.getName()) ? options.getName().trim() : filename;
        if (name.length() > 255) {
            throw new ClientException("文档展示名称不能超过255个字符，请指定较短名称");
        }
        int chunkSize = options.getChunkSize() == null ? base.getChunkSize() : options.getChunkSize();
        int overlap = options.getChunkOverlap() == null ? base.getChunkOverlap() : options.getChunkOverlap();
        if (chunkSize <= 0 || chunkSize > 100000 || overlap < 0 || overlap >= chunkSize) {
            throw new ClientException("切分长度必须为1至100000，重叠长度必须大于等于0且小于切分长度");
        }
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        if (contentType.length() > 255) {
            throw new ClientException("文件内容类型过长");
        }

        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(IdWorker.getId());
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setName(name);
        document.setOriginalFilename(filename);
        document.setFileExtension(extension);
        document.setContentType(contentType);
        document.setChunkSize(chunkSize);
        document.setChunkOverlap(overlap);
        // 对象名不包含原文件名；同名文件也使用不同文档 ID，避免覆盖。
        document.setStorageKey("knowledge/" + knowledgeBaseId + "/" + document.getId() + "." + extension);

        Path temporaryFile = null;
        boolean uploadAttempted = false;
        try {
            Files.createDirectories(properties.getTemporaryDirectory());
            temporaryFile = Files.createTempFile(properties.getTemporaryDirectory(), "ragent-document-", ".upload");
            document.setSha256(copyAndHash(file, temporaryFile, maxSize));
            document.setFileSize(Files.size(temporaryFile));
            if (document.getFileSize() == 0) {
                throw new ClientException("上传文件不能为空");
            }
            // 即使上传请求失败，远端也可能已写入对象，因此失败时同样尝试补偿删除。
            uploadAttempted = true;
            storage.upload(document.getStorageKey(), temporaryFile, contentType);
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                // 与知识库修改、删除使用相同父记录锁，防止上传结束后写入已删除知识库。
                requireBase(knowledgeBaseId, true);
                if (knowledgeDocumentMapper.insert(document) != 1) {
                    throw new ServiceException("文档信息保存失败");
                }
            });
            return toResponse(document);
        } catch (IOException exception) {
            throw new ServiceException("读取上传文件失败", exception, BaseErrorCode.SERVICE_ERROR);
        } catch (RuntimeException exception) {
            if (uploadAttempted) {
                try {
                    storage.delete(document.getStorageKey());
                } catch (RuntimeException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                    log.error("上传失败后的对象清理失败，需重试清理：key={}", document.getStorageKey(), cleanupFailure);
                }
            }
            throw exception;
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException cleanupFailure) {
                    log.warn("上传临时文件清理失败：{}", temporaryFile, cleanupFailure);
                }
            }
        }
    }

    /**
     * 校验知识库存在且启用；最终保存时需要在事务中加锁再次确认。
     */
    private KnowledgeBase requireBase(Long id, boolean lock) {
        if (id == null || id <= 0) {
            throw new ClientException("知识库ID必须大于0");
        }
        KnowledgeBase base = lock ? knowledgeBaseMapper.selectActiveForUpdate(id) : knowledgeBaseMapper.selectById(id);
        if (base == null || !Integer.valueOf(0).equals(base.getDelFlag())) {
            throw new ClientException("知识库不存在或已删除");
        }
        if (!Boolean.TRUE.equals(base.getEnabled())) {
            throw new ClientException("知识库已禁用，不能上传文档");
        }
        return base;
    }

    /**
     * 边读取边写入和计算 SHA-256，并以实际读取字节数执行大小限制。
     */
    private String copyAndHash(MultipartFile file, Path target, long maxSize) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
        try (var input = file.getInputStream(); var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            long count = 0;
            int length;
            while ((length = input.read(buffer)) != -1) {
                count += length;
                if (count > maxSize) {
                    throw new ClientException("上传文件超过允许的大小：" + properties.getMaxFileSize());
                }
                digest.update(buffer, 0, length);
                output.write(buffer, 0, length);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * 只返回文档信息，不向调用方暴露对象存储凭据。
     */
    private KnowledgeDocumentResponse toResponse(KnowledgeDocument document) {
        return KnowledgeDocumentResponse.builder()
                .id(document.getId().toString())
                .knowledgeBaseId(document.getKnowledgeBaseId().toString())
                .name(document.getName()).originalFilename(document.getOriginalFilename())
                .fileExtension(document.getFileExtension()).contentType(document.getContentType())
                .fileSize(document.getFileSize()).storageKey(document.getStorageKey()).sha256(document.getSha256())
                .processStatus(document.getProcessStatus()).errorMessage(document.getErrorMessage())
                .chunkSize(document.getChunkSize()).chunkOverlap(document.getChunkOverlap())
                .enabled(document.getEnabled()).createTime(document.getCreateTime()).updateTime(document.getUpdateTime())
                .build();
    }
}

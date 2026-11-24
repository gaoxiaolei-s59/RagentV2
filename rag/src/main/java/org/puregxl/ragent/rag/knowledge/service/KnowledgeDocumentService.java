package org.puregxl.ragent.rag.knowledge.service;

import org.puregxl.ragent.rag.knowledge.dto.request.KnowledgeDocumentUploadRequest;
import org.puregxl.ragent.rag.knowledge.dto.response.KnowledgeDocumentResponse;
import org.springframework.web.multipart.MultipartFile;

/** 文档上传业务契约。 */
public interface KnowledgeDocumentService {
    /** 将文件上传到 RustFS 并保存待处理文档记录。 */
    KnowledgeDocumentResponse uploadKnowledgeDocument(Long knowledgeBaseId,
        KnowledgeDocumentUploadRequest request, MultipartFile file);
}

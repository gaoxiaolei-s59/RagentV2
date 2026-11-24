package org.puregxl.ragent.rag.knowledge.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.puregxl.ragent.framework.convention.Result;
import org.puregxl.ragent.framework.web.Results;
import org.puregxl.ragent.rag.knowledge.dto.request.KnowledgeDocumentUploadRequest;
import org.puregxl.ragent.rag.knowledge.dto.response.KnowledgeDocumentResponse;
import org.puregxl.ragent.rag.knowledge.service.KnowledgeDocumentService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 保留现有类名，先实现知识库文档上传入口；解析与切片由后续处理流程执行。
 * <p>文件保存至 RustFS，文档元数据保存至 MySQL。</p>
 */
@RestController
@RequiredArgsConstructor
@Profile("mysql")
@RequestMapping("/knowledge-base/{kb-id}/docs")
public class KnowledgeChunkController {
    private final KnowledgeDocumentService documentService;

    /**
     * 上传原文档并创建 PENDING 状态记录。
     * @param kbId 所属知识库 ID，必须存在且启用
     * @param file 待上传文件，不能为空
     * @param requestParam 可选展示名称和切分参数，通过 multipart 普通字段传入
     * @return 文档信息，ID 按字符串返回
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeDocumentResponse> uploadKnowledgeDocument(@PathVariable("kb-id") Long kbId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @Valid @ModelAttribute KnowledgeDocumentUploadRequest requestParam) {
        return Results.success(documentService.uploadKnowledgeDocument(kbId, requestParam, file));
    }
}

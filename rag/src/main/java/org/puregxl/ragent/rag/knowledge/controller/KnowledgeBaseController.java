package org.puregxl.ragent.rag.knowledge.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.puregxl.ragent.framework.convention.Result;
import org.puregxl.ragent.framework.web.Results;
import org.puregxl.ragent.rag.knowledge.dto.request.KnowledgeBaseEnabledRequest;
import org.puregxl.ragent.rag.knowledge.dto.request.KnowledgeBasePageRequest;
import org.puregxl.ragent.rag.knowledge.dto.request.KnowledgeBaseSaveRequest;
import org.puregxl.ragent.rag.knowledge.service.KnowledgeBaseService;
import org.puregxl.ragent.rag.knowledge.dto.response.KnowledgeBasePageResponse;
import org.puregxl.ragent.rag.knowledge.dto.response.KnowledgeBaseResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/**
 * 知识库管理 HTTP 入口，负责参数校验和统一响应，业务规则交由 Service 处理。
 * <p>仅在 mysql profile 下注册；所有接口以 /knowledge-base 为根路径。</p>
 */
@RestController
@RequestMapping("/knowledge-base")
@RequiredArgsConstructor
@Profile("mysql")
public class KnowledgeBaseController {

    /** 知识库业务服务。 */
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 创建知识库，默认启用。
     *
     * @param request 知识库名称、说明、向量模型和默认切分参数
     * @return 新建知识库的字符串 ID，避免雪花 ID 在前端丢失精度
     */
    @PostMapping
    public Result<String> create(@Valid @RequestBody KnowledgeBaseSaveRequest request) {
        return Results.success(knowledgeBaseService.create(request).toString());
    }

    /**
     * 完整修改知识库配置，不修改启用状态。
     * <p>未传的可选字段会清空；省略切分参数时使用 DTO 默认值。</p>
     *
     * @param id 知识库 ID
     * @param request 完整配置；已有向量索引时不能直接更换模型或维度
     * @return 成功响应
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody KnowledgeBaseSaveRequest request) {
        knowledgeBaseService.update(id, request);
        return Results.success();
    }

    /**
     * 级联逻辑删除知识库及其文档、切片，保留物理记录。
     *
     * @param id 知识库 ID
     * @return 成功响应；文件和向量的物理清理由后续存储功能处理
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        knowledgeBaseService.delete(id);
        return Results.success();
    }

    /**
     * 查询未删除的知识库详情，禁用记录仍可查看。
     *
     * @param id 知识库 ID
     * @return 知识库详情，不包含持久化实体中的删除标记
     */
    @GetMapping("/{id}")
    public Result<KnowledgeBaseResponse> detail(@PathVariable("id") Long id) {
        return Results.success(knowledgeBaseService.detail(id));
    }

    /**
     * 分页查询知识库，按创建时间和 ID 倒序排列。
     *
     * @param request 页码、每页条数、名称关键词和可选启用状态
     * @return 分页记录和总条数，自动排除逻辑删除记录
     */
    @GetMapping
    public Result<KnowledgeBasePageResponse> page(@Valid @ModelAttribute KnowledgeBasePageRequest request) {
        return Results.success(knowledgeBaseService.page(request));
    }

    /**
     * 启用或禁用知识库，保留文档和切片各自的启用设置。
     *
     * @param id 知识库 ID
     * @param request true 表示启用，false 表示禁用
     * @return 成功响应
     */
    @PatchMapping("/{id}/enabled")
    public Result<Void> setEnabled(@PathVariable("id") Long id,
                                   @Valid @RequestBody KnowledgeBaseEnabledRequest request) {
        knowledgeBaseService.setEnabled(id, request.enabled());
        return Results.success();
    }
}

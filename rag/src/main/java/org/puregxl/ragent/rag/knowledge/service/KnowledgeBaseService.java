package org.puregxl.ragent.rag.knowledge.service;

import org.puregxl.ragent.rag.knowledge.dto.KnowledgeBasePageRequest;
import org.puregxl.ragent.rag.knowledge.dto.KnowledgeBaseSaveRequest;
import org.puregxl.ragent.rag.knowledge.vo.KnowledgeBasePageVO;
import org.puregxl.ragent.rag.knowledge.vo.KnowledgeBaseVO;

/** 知识库业务契约，负责配置维护、查询和生命周期管理。 */
public interface KnowledgeBaseService {
    /**
     * 创建知识库。请求参数应已通过 Bean Validation 校验。
     *
     * @param request 知识库配置
     * @return 应用生成的雪花 ID
     */
    Long create(KnowledgeBaseSaveRequest request);

    /**
     * 完整修改业务配置，保留原启用状态。
     *
     * @param id 知识库 ID
     * @param request 完整配置，允许清空可选字段
     * @throws org.puregxl.ragent.framework.exception.ClientException 知识库不存在，或模型变更需要重建索引
     */
    void update(Long id, KnowledgeBaseSaveRequest request);

    /**
     * 在同一事务内逻辑删除知识库及其文档、切片。
     *
     * @param id 知识库 ID
     * @throws org.puregxl.ragent.framework.exception.ClientException ID 不合法或知识库不存在
     */
    void delete(Long id);

    /**
     * 查询知识库详情，包含禁用记录，不包含已删除记录。
     *
     * @param id 知识库 ID
     * @return 对外展示对象
     * @throws org.puregxl.ragent.framework.exception.ClientException ID 不合法或知识库不存在
     */
    KnowledgeBaseVO detail(Long id);

    /**
     * 按名称关键词、启用状态分页查询。请求参数应已通过 Bean Validation 校验。
     *
     * @param request 页码从 1 开始，每页 1 至 100 条
     * @return 按创建时间、ID 倒序排列的分页结果
     */
    KnowledgeBasePageVO page(KnowledgeBasePageRequest request);

    /**
     * 修改知识库自身的启用状态，不覆盖子记录设置。
     *
     * @param id 知识库 ID
     * @param enabled 是否启用
     * @throws org.puregxl.ragent.framework.exception.ClientException ID 不合法或知识库不存在
     */
    void setEnabled(Long id, boolean enabled);
}

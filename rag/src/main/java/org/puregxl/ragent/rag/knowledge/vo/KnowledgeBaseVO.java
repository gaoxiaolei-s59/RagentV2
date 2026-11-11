package org.puregxl.ragent.rag.knowledge.vo;

import java.util.Date;

/**
 * 知识库详情及分页列表中的展示对象。
 *
 * @param id 字符串形式的雪花 ID，避免前端整数精度丢失
 * @param name 知识库名称
 * @param description 可选说明
 * @param embeddingModelId 向量模型配置 ID，可暂时为空
 * @param embeddingDimension 向量维度，可暂时为空
 * @param chunkSize 默认切分字符数
 * @param chunkOverlap 默认重叠字符数
 * @param enabled 知识库自身是否启用
 * @param createTime 创建时间
 * @param updateTime 最后修改时间
 */
public record KnowledgeBaseVO(String id, String name, String description,
                              String embeddingModelId, Integer embeddingDimension,
                              Integer chunkSize, Integer chunkOverlap, Boolean enabled,
                              Date createTime, Date updateTime) {
}

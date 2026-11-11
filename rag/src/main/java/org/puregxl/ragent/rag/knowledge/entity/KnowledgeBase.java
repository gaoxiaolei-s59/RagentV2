package org.puregxl.ragent.rag.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 知识库及其默认切分、向量模型配置。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_base")
public class KnowledgeBase extends BaseKnowledgeEntity {

    /** 知识库展示名称。 */
    private String name;

    /** 可选的用途或内容说明。 */
    private String description;

    /** 对应 ai.embedding.candidates 中的模型 ID。 */
    private String embeddingModelId;

    /** 模型尚未确定时允许为空，创建向量集合前必须确认。 */
    private Integer embeddingDimension;

    /** 首版按字符数切分；文档保存实际使用的参数。 */
    private Integer chunkSize = 800;

    /** 相邻片段默认重叠字符数，必须小于 chunkSize。 */
    private Integer chunkOverlap = 100;
}

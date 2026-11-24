package org.puregxl.ragent.rag.knowledge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/** 知识库详情及分页列表中的返回对象。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseResponse {
    /** 字符串形式的雪花 ID，避免前端整数精度丢失。 */
    private String id;

    /** 知识库名称。 */
    private String name;

    /** 可选说明。 */
    private String description;

    /** 向量模型配置 ID，可暂时为空。 */
    private String embeddingModelId;

    /** 向量维度，可暂时为空。 */
    private Integer embeddingDimension;

    /** 默认切分字符数。 */
    private Integer chunkSize;

    /** 默认重叠字符数。 */
    private Integer chunkOverlap;

    /** 知识库自身是否启用。 */
    private Boolean enabled;

    /** 创建时间。 */
    private Date createTime;

    /** 最后修改时间。 */
    private Date updateTime;
}

package org.puregxl.ragent.rag.knowledge.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

/** 创建及完整修改知识库的参数；不接受启用状态、删除标记及审计字段。 */
@Data
public class KnowledgeBaseSaveRequest {
    /** 知识库展示名称，保存前去除首尾空白。 */
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 255, message = "知识库名称不能超过255个字符")
    private String name;

    /** 可选说明；完整修改时传 null 或省略可清空。 */
    @Size(max = 16000, message = "知识库说明不能超过16000个字符")
    private String description;

    /** ai.embedding.candidates 中的模型配置 ID，允许暂不指定。 */
    @Size(max = 128, message = "模型ID不能超过128个字符")
    private String embeddingModelId;

    /** 可选向量维度；向量入库前必须与实际模型及集合一致。 */
    @Min(value = 1, message = "向量维度必须大于0")
    private Integer embeddingDimension;

    /** 默认切分字符数，省略时为 800。 */
    @NotNull(message = "切分长度不能为空")
    @Min(value = 1, message = "切分长度必须大于0")
    @Max(value = 100000, message = "切分长度不能超过100000")
    private Integer chunkSize = 800;

    /** 相邻片段重叠字符数，省略时为 100，必须小于切分长度。 */
    @NotNull(message = "重叠长度不能为空")
    @Min(value = 0, message = "重叠长度不能为负数")
    private Integer chunkOverlap = 100;

    /**
     * 校验两个切分参数的相互关系。
     * <p>空值交由字段上的 NotNull 校验，避免同一个空值产生重复错误。</p>
     *
     * @return 参数为空或重叠长度小于切分长度时为 true
     */
    @AssertTrue(message = "重叠长度必须小于切分长度")
    public boolean isChunkConfigurationValid() {
        return chunkSize == null || chunkOverlap == null || chunkOverlap < chunkSize;
    }
}

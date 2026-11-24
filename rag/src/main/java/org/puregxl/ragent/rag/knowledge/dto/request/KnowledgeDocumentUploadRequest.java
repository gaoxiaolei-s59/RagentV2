package org.puregxl.ragent.rag.knowledge.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** multipart 上传附加参数；未指定切分参数时继承知识库默认配置。 */
@Data
public class KnowledgeDocumentUploadRequest {
    /** 可选展示名称，省略时使用原文件名。 */
    @Size(max = 255, message = "文档名称不能超过255个字符")
    private String name;

    /** 可选切分字符数。 */
    @Min(value = 1, message = "切分长度必须大于0")
    @Max(value = 100000, message = "切分长度不能超过100000")
    private Integer chunkSize;

    /** 可选重叠字符数，最终必须小于实际切分长度。 */
    @Min(value = 0, message = "重叠长度不能为负数")
    private Integer chunkOverlap;
}

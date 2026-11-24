package org.puregxl.ragent.rag.knowledge.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 知识库分页查询参数，由 GET 请求的查询字符串绑定。 */
@Data
public class KnowledgeBasePageRequest {
    /** 页码，从 1 开始，默认第 1 页。 */
    @NotNull @Min(value = 1, message = "页码必须大于0")
    private Integer pageNum = 1;

    /** 每页条数，默认 10，最大 100。 */
    @NotNull @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 100, message = "每页最多100条")
    private Integer pageSize = 10;

    /** 可选名称关键词，非空时执行模糊查询。 */
    @Size(max = 255, message = "查询名称不能超过255个字符")
    private String name;

    /** 可选启用状态；null 表示同时查询启用和禁用记录。 */
    private Boolean enabled;
}

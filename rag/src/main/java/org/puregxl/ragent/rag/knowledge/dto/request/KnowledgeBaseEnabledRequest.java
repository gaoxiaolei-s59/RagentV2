package org.puregxl.ragent.rag.knowledge.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 知识库启用状态修改参数。
 *
 * @param enabled true 启用、false 禁用；必须明确传入，不接受 null
 */
public record KnowledgeBaseEnabledRequest(@NotNull(message = "启用状态不能为空") Boolean enabled) {
}

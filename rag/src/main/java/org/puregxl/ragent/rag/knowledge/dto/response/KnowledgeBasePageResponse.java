package org.puregxl.ragent.rag.knowledge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 知识库分页查询返回对象。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBasePageResponse {
    /** 当前页码，从 1 开始。 */
    private long pageNum;

    /** 每页条数。 */
    private long pageSize;

    /** 符合查询条件且未逻辑删除的总记录数。 */
    private long total;

    /** 当前页数据，按创建时间、ID 倒序排列。 */
    private List<KnowledgeBaseResponse> records;
}

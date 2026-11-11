package org.puregxl.ragent.rag.knowledge.vo;

import java.util.List;

/**
 * 知识库分页查询结果。
 *
 * @param pageNum 当前页码，从 1 开始
 * @param pageSize 每页条数
 * @param total 符合查询条件且未逻辑删除的总记录数
 * @param records 当前页数据，按创建时间、ID 倒序排列
 */
public record KnowledgeBasePageVO(long pageNum, long pageSize, long total, List<KnowledgeBaseVO> records) {
}

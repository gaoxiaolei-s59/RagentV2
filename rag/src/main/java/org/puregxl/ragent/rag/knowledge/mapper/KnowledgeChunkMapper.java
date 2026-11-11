package org.puregxl.ragent.rag.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.puregxl.ragent.rag.knowledge.entity.KnowledgeChunk;

/** 切片持久化接口，用于向量状态检查及知识库删除时的切片逻辑删除。 */
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {
}

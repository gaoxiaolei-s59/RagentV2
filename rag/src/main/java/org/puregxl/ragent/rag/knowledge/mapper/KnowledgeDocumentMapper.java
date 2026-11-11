package org.puregxl.ragent.rag.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.puregxl.ragent.rag.knowledge.entity.KnowledgeDocument;

/** 文档持久化接口，当前用于知识库删除时批量更新文档逻辑删除标记。 */
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
}

package org.puregxl.ragent.rag.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.puregxl.ragent.rag.knowledge.enums.ChunkVectorStatus;

/** 文档切片；向量库以本记录 ID 关联，MySQL 只保存正文和索引状态。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "knowledge_chunk", autoResultMap = true)
public class KnowledgeChunk extends BaseKnowledgeEntity {

    /** 所属知识库 ID，必须与关联文档的知识库一致。 */
    private Long knowledgeBaseId;

    /** 所属文档 ID，对应 knowledge_document.id。 */
    private Long documentId;

    /** 文档内从 1 开始的片段序号。 */
    private Integer chunkNo;

    /** 切片正文，数据库使用 LONGTEXT 保存。 */
    private String content;

    /** 正文的估算 Token 数，尚未计算时为 0。 */
    private Integer tokenCount = 0;

    /** 当前正文 SHA-256 的 64 位十六进制字符串。 */
    private String contentHash;

    /** 当前正文的向量索引状态，新增或正文变更后应为 PENDING。 */
    private ChunkVectorStatus vectorStatus = ChunkVectorStatus.PENDING;

    /** 向量化或索引失败原因，成功处理后由业务清空。 */
    private String errorMessage;
}

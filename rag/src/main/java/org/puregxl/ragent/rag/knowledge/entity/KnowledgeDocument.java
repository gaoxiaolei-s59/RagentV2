package org.puregxl.ragent.rag.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.puregxl.ragent.rag.knowledge.enums.DocumentProcessStatus;

/** 上传文档的文件信息和解析切分状态，原文件不存入数据库。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "knowledge_document", autoResultMap = true)
public class KnowledgeDocument extends BaseKnowledgeEntity {

    /** 所属知识库 ID，对应 knowledge_base.id。 */
    private Long knowledgeBaseId;

    /** 可修改的展示名称。 */
    private String name;

    /** 上传时的原始文件名，独立于可修改的展示名称。 */
    private String originalFilename;

    /** 不带点的小写文件扩展名，例如 txt、md。 */
    private String fileExtension;

    /** 上传文件的 MIME 类型，实际解析时仍需校验内容。 */
    private String contentType;

    /** 文件大小，单位字节。 */
    private Long fileSize;

    /** 相对于存储根目录的 key，或对象存储 key。 */
    private String storageKey;

    /** 原文件 SHA-256 的 64 位十六进制字符串。 */
    private String sha256;

    /** 文档解析切分状态，新增时为待处理。 */
    private DocumentProcessStatus processStatus = DocumentProcessStatus.PENDING;

    /** 解析或切分失败原因，成功处理后由业务清空。 */
    private String errorMessage;

    /** 本文档实际使用的切分参数，由创建流程从知识库配置复制。 */
    private Integer chunkSize;

    /** 本文档实际采用的重叠字符数。 */
    private Integer chunkOverlap;
}

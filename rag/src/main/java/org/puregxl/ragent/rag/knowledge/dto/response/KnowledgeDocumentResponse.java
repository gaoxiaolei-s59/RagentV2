package org.puregxl.ragent.rag.knowledge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.puregxl.ragent.rag.knowledge.enums.DocumentProcessStatus;

import java.util.Date;

/** 文档上传返回信息，不包含文件二进制或对象存储凭据。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentResponse {
    /** 文档雪花 ID，以字符串返回。 */
    private String id;

    /** 所属知识库 ID，以字符串返回。 */
    private String knowledgeBaseId;

    /** 展示名称。 */
    private String name;

    /** 上传原文件名。 */
    private String originalFilename;

    /** 小写扩展名。 */
    private String fileExtension;

    /** 上传内容类型；不代表已校验正文格式。 */
    private String contentType;

    /** 原文件字节数。 */
    private Long fileSize;

    /** RustFS 桶内的对象 key。 */
    private String storageKey;

    /** 原文件 SHA-256 十六进制校验值。 */
    private String sha256;

    /** 文档处理状态，新上传为 PENDING。 */
    private DocumentProcessStatus processStatus;

    /** 处理失败原因。 */
    private String errorMessage;

    /** 本次实际切分字符数。 */
    private Integer chunkSize;

    /** 本次实际重叠字符数。 */
    private Integer chunkOverlap;

    /** 文档自身是否启用。 */
    private Boolean enabled;

    /** 创建时间。 */
    private Date createTime;

    /** 修改时间。 */
    private Date updateTime;

}

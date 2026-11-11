-- MySQL 8.0.16+；手动执行，不随应用启动自动运行。
-- 雪花 ID 由应用生成；启用状态与逻辑删除分别维护。
CREATE DATABASE IF NOT EXISTS ragent
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE ragent;

CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT NOT NULL COMMENT '应用分配的雪花ID',
    name VARCHAR(255) NOT NULL COMMENT '知识库名称',
    description TEXT NULL COMMENT '知识库说明',
    embedding_model_id VARCHAR(128) NULL COMMENT '向量模型配置ID',
    embedding_dimension INT NULL COMMENT '向量维度，创建向量集合前确定',
    chunk_size INT NOT NULL DEFAULT 800 COMMENT '默认切分字符数',
    chunk_overlap INT NOT NULL DEFAULT 100 COMMENT '默认重叠字符数',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '自身是否启用',
    del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常，1已删除',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    PRIMARY KEY (id),
    KEY idx_kb_page (del_flag, enabled, create_time, id),
    CONSTRAINT chk_kb_chunk_size CHECK (chunk_size > 0),
    CONSTRAINT chk_kb_chunk_overlap CHECK (chunk_overlap >= 0 AND chunk_overlap < chunk_size),
    CONSTRAINT chk_kb_dimension CHECK (embedding_dimension IS NULL OR embedding_dimension > 0),
    CONSTRAINT chk_kb_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_kb_deleted CHECK (del_flag IN (0, 1))
) ENGINE=InnoDB COMMENT='知识库';

CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT NOT NULL COMMENT '应用分配的雪花ID',
    knowledge_base_id BIGINT NOT NULL COMMENT '所属知识库ID',
    name VARCHAR(255) NOT NULL COMMENT '文档展示名称',
    original_filename VARCHAR(512) NOT NULL COMMENT '上传时的原文件名',
    file_extension VARCHAR(32) NOT NULL COMMENT '不带点的小写扩展名',
    content_type VARCHAR(255) NOT NULL COMMENT '文件MIME类型',
    file_size BIGINT NOT NULL COMMENT '原文件字节数',
    storage_key VARCHAR(1024) NOT NULL COMMENT '原文件存储标识',
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原文件SHA-256',
    process_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/PROCESSING/READY/FAILED',
    error_message VARCHAR(2048) NULL COMMENT '解析切分失败原因',
    chunk_size INT NOT NULL COMMENT '本次实际切分字符数',
    chunk_overlap INT NOT NULL COMMENT '本次实际重叠字符数',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '自身是否启用',
    del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常，1已删除',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    PRIMARY KEY (id),
    -- 供切片复合外键使用，保证切片与所属文档位于同一知识库。
    UNIQUE KEY uk_document_id_base (id, knowledge_base_id),
    KEY idx_doc_page (knowledge_base_id, del_flag, enabled, create_time, id),
    KEY idx_doc_sha256 (knowledge_base_id, sha256, del_flag),
    KEY idx_doc_process (process_status, del_flag, update_time),
    CONSTRAINT fk_document_base FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_base (id),
    CONSTRAINT chk_doc_file_size CHECK (file_size >= 0),
    CONSTRAINT chk_doc_chunk_size CHECK (chunk_size > 0),
    CONSTRAINT chk_doc_chunk_overlap CHECK (chunk_overlap >= 0 AND chunk_overlap < chunk_size),
    CONSTRAINT chk_doc_process CHECK (process_status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT chk_doc_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_doc_deleted CHECK (del_flag IN (0, 1))
) ENGINE=InnoDB COMMENT='知识库上传文档';

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGINT NOT NULL COMMENT '应用分配的雪花ID，同时用作向量库关联ID',
    knowledge_base_id BIGINT NOT NULL COMMENT '所属知识库ID',
    document_id BIGINT NOT NULL COMMENT '所属文档ID',
    chunk_no INT NOT NULL COMMENT '文档内片段序号，从1开始',
    content LONGTEXT NOT NULL COMMENT '切片正文',
    token_count INT NOT NULL DEFAULT 0 COMMENT '估算Token数',
    content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '正文SHA-256',
    vector_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/INDEXING/READY/FAILED',
    error_message VARCHAR(2048) NULL COMMENT '向量处理失败原因',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '自身是否启用',
    del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常，1已删除',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    PRIMARY KEY (id),
    KEY idx_chunk_document_base (document_id, knowledge_base_id),
    KEY idx_chunk_page (document_id, del_flag, chunk_no, id),
    KEY idx_chunk_base (knowledge_base_id, del_flag, enabled),
    KEY idx_chunk_vector (vector_status, del_flag, update_time),
    CONSTRAINT fk_chunk_document FOREIGN KEY (document_id, knowledge_base_id)
        REFERENCES knowledge_document (id, knowledge_base_id),
    CONSTRAINT chk_chunk_no CHECK (chunk_no > 0),
    CONSTRAINT chk_chunk_tokens CHECK (token_count >= 0),
    CONSTRAINT chk_chunk_vector CHECK (vector_status IN ('PENDING', 'INDEXING', 'READY', 'FAILED')),
    CONSTRAINT chk_chunk_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_chunk_deleted CHECK (del_flag IN (0, 1))
) ENGINE=InnoDB COMMENT='知识库文档切片';

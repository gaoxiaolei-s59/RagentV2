-- 手动回滚：会永久删除这三张表及数据；执行前停止相关业务并备份。
-- 按外键依赖逆序删除，保留数据库和其他业务表。
USE ragent;
DROP TABLE IF EXISTS knowledge_chunk;
DROP TABLE IF EXISTS knowledge_document;
DROP TABLE IF EXISTS knowledge_base;

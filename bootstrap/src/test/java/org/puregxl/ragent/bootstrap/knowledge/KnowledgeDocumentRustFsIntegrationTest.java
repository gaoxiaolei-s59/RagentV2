package org.puregxl.ragent.bootstrap.knowledge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.puregxl.ragent.framework.config.DataBaseConfiguration;
import org.puregxl.ragent.framework.web.GlobalExceptionHandler;
import org.puregxl.ragent.rag.knowledge.config.KnowledgeDataSourceConfiguration;
import org.puregxl.ragent.rag.knowledge.config.KnowledgeStorageConfiguration;
import org.puregxl.ragent.rag.knowledge.config.KnowledgeStorageProperties;
import org.puregxl.ragent.rag.knowledge.controller.KnowledgeChunkController;
import org.puregxl.ragent.rag.knowledge.dto.request.KnowledgeDocumentUploadRequest;
import org.puregxl.ragent.rag.knowledge.entity.KnowledgeBase;
import org.puregxl.ragent.rag.knowledge.enums.DocumentProcessStatus;
import org.puregxl.ragent.rag.knowledge.mapper.KnowledgeBaseMapper;
import org.puregxl.ragent.rag.knowledge.mapper.KnowledgeDocumentMapper;
import org.puregxl.ragent.rag.knowledge.service.KnowledgeDocumentService;
import org.puregxl.ragent.rag.knowledge.service.impl.KnowledgeDocumentServiceImpl;
import org.puregxl.ragent.rag.knowledge.storage.DocumentStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import software.amazon.awssdk.services.s3.S3Client;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 真实 MySQL + RustFS 上传验证；不使用测试外层事务，验证 Service 自身提交及对象读回。
 */
@SpringJUnitConfig(classes = {KnowledgeDataSourceConfiguration.class, DataBaseConfiguration.class,
        KnowledgeStorageConfiguration.class, KnowledgeDocumentServiceImpl.class},
        initializers = ConfigDataApplicationContextInitializer.class)
@ActiveProfiles("mysql")
@EnabledIfSystemProperty(named = "knowledge.storage.test", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KnowledgeDocumentRustFsIntegrationTest {
    @Autowired
    KnowledgeBaseMapper baseMapper;
    @Autowired
    KnowledgeDocumentMapper documentMapper;
    @Autowired
    KnowledgeDocumentService service;
    @Autowired
    DocumentStorage storage;
    @Autowired
    S3Client documentS3Client;
    @Autowired
    KnowledgeStorageProperties properties;
    @Autowired
    DataSource dataSource;
    private Long knowledgeBaseId;
    private JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        KnowledgeBase base = new KnowledgeBase();
        base.setName("RustFS上传联调临时库");
        baseMapper.insert(base);
        knowledgeBaseId = base.getId();
        mvc = MockMvcBuilders.standaloneSetup(new KnowledgeChunkController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void cleanup() {
        if (knowledgeBaseId == null) return;
        try {
            for (String key : jdbc.queryForList("SELECT storage_key FROM knowledge_document WHERE knowledge_base_id=?",
                    String.class, knowledgeBaseId)) {
                storage.delete(key);
            }
        } finally {
            // 仅清理由本测试创建的记录，保留桶及所有既有业务数据。
            jdbc.update("DELETE FROM knowledge_chunk WHERE knowledge_base_id=?", knowledgeBaseId);
            jdbc.update("DELETE FROM knowledge_document WHERE knowledge_base_id=?", knowledgeBaseId);
            jdbc.update("DELETE FROM knowledge_base WHERE id=?", knowledgeBaseId);
        }
    }

    @Test
    void uploadsThroughControllerAndReadsBackSameBytesFromRustFs() throws Exception {
        byte[] content = "你好，RustFS 文件上传🙂".getBytes(StandardCharsets.UTF_8);
        var response = mvc.perform(multipart("/knowledge-base/{id}/docs/upload", knowledgeBaseId)
                        .file(new MockMultipartFile("file", "example.txt", "text/plain", content))
                        .param("name", "联调文档").param("chunkSize", "500").param("chunkOverlap", "50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.knowledgeBaseId").value(knowledgeBaseId.toString()))
                .andExpect(jsonPath("$.data.chunkSize").value(500))
                .andExpect(jsonPath("$.data.processStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.createTime").exists())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var data = com.google.gson.JsonParser.parseString(response).getAsJsonObject().getAsJsonObject("data");
        var document = documentMapper.selectById(Long.valueOf(data.get("id").getAsString()));
        assertThat(document.getName()).isEqualTo("联调文档");
        assertThat(document.getProcessStatus()).isEqualTo(DocumentProcessStatus.PENDING);
        assertThat(document.getFileSize()).isEqualTo(content.length);
        assertThat(document.getSha256()).hasSize(64);
        assertThat(readObject(document.getStorageKey())).isEqualTo(content);
    }

    @Test
    void sameFilenameCreatesIndependentDocumentsAndObjects() {
        var request = new KnowledgeDocumentUploadRequest();
        var first = service.uploadKnowledgeDocument(knowledgeBaseId, request,
                new MockMultipartFile("file", "same.md", "text/markdown", "first".getBytes(StandardCharsets.UTF_8)));
        var second = service.uploadKnowledgeDocument(knowledgeBaseId, request,
                new MockMultipartFile("file", "same.md", "text/markdown", "second".getBytes(StandardCharsets.UTF_8)));
        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(first.getStorageKey()).isNotEqualTo(second.getStorageKey());
        assertThat(readObject(first.getStorageKey())).isEqualTo("first".getBytes(StandardCharsets.UTF_8));
        assertThat(readObject(second.getStorageKey())).isEqualTo("second".getBytes(StandardCharsets.UTF_8));
        assertThat(first.getChunkSize()).isEqualTo(800);
        assertThat(first.getChunkOverlap()).isEqualTo(100);
    }

    @Test
    void missingFileAndDisabledBaseDoNotCreateDocuments() throws Exception {
        mvc.perform(multipart("/knowledge-base/{id}/docs/upload", knowledgeBaseId))
                .andExpect(jsonPath("$.code").value("A000001"));
        jdbc.update("UPDATE knowledge_base SET enabled=0 WHERE id=?", knowledgeBaseId);
        mvc.perform(multipart("/knowledge-base/{id}/docs/upload", knowledgeBaseId)
                        .file(new MockMultipartFile("file", "example.txt", "text/plain", new byte[]{1})))
                .andExpect(jsonPath("$.code").value("A000001"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_document WHERE knowledge_base_id=?",
                Integer.class, knowledgeBaseId)).isZero();
    }

    private byte[] readObject(String key) {
        return documentS3Client.getObjectAsBytes(builder -> builder.bucket(properties.getBucket()).key(key)).asByteArray();
    }
}

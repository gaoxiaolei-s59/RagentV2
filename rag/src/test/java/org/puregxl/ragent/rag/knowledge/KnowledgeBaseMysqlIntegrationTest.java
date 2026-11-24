package org.puregxl.ragent.rag.knowledge;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.puregxl.ragent.framework.config.DataBaseConfiguration;
import org.puregxl.ragent.framework.exception.ClientException;
import org.puregxl.ragent.framework.web.GlobalExceptionHandler;
import org.puregxl.ragent.rag.knowledge.config.KnowledgeDataSourceConfiguration;
import org.puregxl.ragent.rag.knowledge.controller.KnowledgeBaseController;
import org.puregxl.ragent.rag.knowledge.dto.request.KnowledgeBasePageRequest;
import org.puregxl.ragent.rag.knowledge.dto.request.KnowledgeBaseSaveRequest;
import org.puregxl.ragent.rag.knowledge.service.KnowledgeBaseService;
import org.puregxl.ragent.rag.knowledge.service.impl.KnowledgeBaseServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用现有 MySQL 表，测试数据在事务结束后回滚，不调用模型服务。 */
@SpringJUnitConfig(classes = {KnowledgeDataSourceConfiguration.class, DataBaseConfiguration.class,
    KnowledgeBaseServiceImpl.class}, initializers = ConfigDataApplicationContextInitializer.class)
@ActiveProfiles("mysql")
@Transactional
@EnabledIfSystemProperty(named = "knowledge.mysql.test", matches = "true")
class KnowledgeBaseMysqlIntegrationTest {
    @Autowired KnowledgeBaseService service;
    @Autowired DataSource dataSource;
    private JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        mvc = MockMvcBuilders.standaloneSetup(new KnowledgeBaseController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void createsAndQueriesKnowledgeBaseThroughController() throws Exception {
        String result = mvc.perform(post("/knowledge-base").contentType("application/json")
                .content("{\"name\":\"接口创建测试\",\"description\":\"测试说明\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data").isString()).andReturn().getResponse().getContentAsString();
        String id = com.google.gson.JsonParser.parseString(result).getAsJsonObject().get("data").getAsString();
        mvc.perform(get("/knowledge-base/{id}", id))
            .andExpect(jsonPath("$.data.id").value(id))
            .andExpect(jsonPath("$.data.name").value("接口创建测试"))
            .andExpect(jsonPath("$.data.chunkSize").value(800))
            .andExpect(jsonPath("$.data.chunkOverlap").value(100))
            .andExpect(jsonPath("$.data.createTime").exists());
    }

    @Test
    void updatesMetadataWithoutResettingDisabledStateAndCanClearOptionalFields() {
        Long id = service.create(request("原名称"));
        service.setEnabled(id, false);
        KnowledgeBaseSaveRequest update = request("新名称");
        update.setDescription(null);
        update.setEmbeddingModelId(null);
        update.setEmbeddingDimension(null);
        update.setChunkSize(600);
        service.update(id, update);
        var result = service.detail(id);
        assertThat(result.getName()).isEqualTo("新名称");
        assertThat(result.getEnabled()).isFalse();
        assertThat(result.getDescription()).isNull();
        assertThat(result.getEmbeddingModelId()).isNull();
        assertThat(result.getEmbeddingDimension()).isNull();
        assertThat(result.getChunkSize()).isEqualTo(600);
    }

    @Test
    void updatesTogglesAndDeletesThroughController() throws Exception {
        Long id = service.create(request("HTTP操作测试"));
        mvc.perform(patch("/knowledge-base/{id}/enabled", id).contentType("application/json")
                .content("{\"enabled\":false}"))
            .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(put("/knowledge-base/{id}", id).contentType("application/json")
                .content("{\"name\":\"HTTP修改完成\",\"chunkSize\":600,\"chunkOverlap\":60}"))
            .andExpect(jsonPath("$.code").value("0"));
        mvc.perform(get("/knowledge-base/{id}", id))
            .andExpect(jsonPath("$.data.name").value("HTTP修改完成"))
            .andExpect(jsonPath("$.data.enabled").value(false));
        mvc.perform(patch("/knowledge-base/{id}/enabled", id).contentType("application/json")
                .content("{\"enabled\":true}"))
            .andExpect(jsonPath("$.code").value("0"));
        assertThat(service.detail(id).getEnabled()).isTrue();
        mvc.perform(delete("/knowledge-base/{id}", id)).andExpect(jsonPath("$.code").value("0"));
        mvc.perform(get("/knowledge-base/{id}", id))
            .andExpect(jsonPath("$.code").value("A000001"))
            .andExpect(jsonPath("$.message").value("知识库不存在或已删除"));
    }

    @Test
    void pagesByKeywordAndEnabledState() throws Exception {
        String prefix = "page-" + IdWorker.getId();
        service.create(request(prefix + "-enabled"));
        Long disabled = service.create(request(prefix + "-disabled"));
        service.setEnabled(disabled, false);
        mvc.perform(get("/knowledge-base").param("name", prefix).param("enabled", "false")
                .param("pageNum", "1").param("pageSize", "1"))
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records.length()").value(1))
            .andExpect(jsonPath("$.data.records[0].id").value(disabled.toString()));
        KnowledgeBasePageRequest page = new KnowledgeBasePageRequest();
        page.setName(prefix); page.setPageNum(2); page.setPageSize(1);
        assertThat(service.page(page).getRecords()).hasSize(1);
        assertThat(service.page(page).getTotal()).isEqualTo(2);
    }

    @Test
    void logicallyDeletesBaseAndItsDocumentsAndChunks() {
        Long id = service.create(request("级联删除测试"));
        long document = insertDocument(id);
        long chunk = insertChunk(id, document);
        service.delete(id);
        for (String table : new String[]{"knowledge_base", "knowledge_document", "knowledge_chunk"}) {
            long record = table.equals("knowledge_base") ? id : table.equals("knowledge_document") ? document : chunk;
            assertThat(jdbc.queryForObject("SELECT del_flag FROM " + table + " WHERE id=?", Integer.class, record))
                .isEqualTo(1);
        }
        assertThatThrownBy(() -> service.detail(id)).isInstanceOf(ClientException.class)
            .hasMessageContaining("知识库不存在");
        KnowledgeBasePageRequest page = new KnowledgeBasePageRequest(); page.setName("级联删除测试");
        assertThat(service.page(page).getRecords()).noneMatch(item -> item.getId().equals(id.toString()));
    }

    @Test
    void refusesEmbeddingModelChangeAfterVectorHasBeenIndexed() {
        Long id = service.create(request("模型配置保护测试"));
        insertChunk(id, insertDocument(id));
        KnowledgeBaseSaveRequest changed = request("模型配置保护测试"); changed.setEmbeddingModelId("other");
        assertThatThrownBy(() -> service.update(id, changed)).isInstanceOf(ClientException.class)
            .hasMessageContaining("重建");
        assertThat(service.detail(id).getEmbeddingModelId()).isEqualTo("local-embedding");
    }

    @Test
    void validatesRequestsAndReportsMissingBase() throws Exception {
        mvc.perform(post("/knowledge-base").contentType("application/json").content("{\"name\":\" \"}"))
            .andExpect(jsonPath("$.code").value("A000001"));
        mvc.perform(post("/knowledge-base").contentType("application/json")
                .content("{\"name\":\"非法切分\",\"chunkSize\":100,\"chunkOverlap\":100}"))
            .andExpect(jsonPath("$.code").value("A000001"));
        mvc.perform(get("/knowledge-base").param("pageSize", "101"))
            .andExpect(jsonPath("$.code").value("A000001"));
        mvc.perform(patch("/knowledge-base/1/enabled").contentType("application/json").content("{}"))
            .andExpect(jsonPath("$.code").value("A000001"));
        mvc.perform(get("/knowledge-base/0"))
            .andExpect(jsonPath("$.code").value("A000001"));
    }

    private KnowledgeBaseSaveRequest request(String name) {
        KnowledgeBaseSaveRequest request = new KnowledgeBaseSaveRequest();
        request.setName(name); request.setDescription("说明");
        request.setEmbeddingModelId("local-embedding"); request.setEmbeddingDimension(768);
        return request;
    }

    private long insertDocument(long base) {
        long id = IdWorker.getId();
        jdbc.update("""
            INSERT INTO knowledge_document
            (id, knowledge_base_id, name, original_filename, file_extension, content_type,
             file_size, storage_key, sha256, chunk_size, chunk_overlap)
            VALUES (?, ?, '测试文档', 'test.txt', 'txt', 'text/plain', 4, 'test/file', ?, 800, 100)
            """, id, base, "a".repeat(64));
        return id;
    }

    private long insertChunk(long base, long document) {
        long id = IdWorker.getId();
        jdbc.update("""
            INSERT INTO knowledge_chunk
            (id, knowledge_base_id, document_id, chunk_no, content, content_hash, vector_status)
            VALUES (?, ?, ?, 1, '测试切片', ?, 'READY')
            """, id, base, document, "b".repeat(64));
        return id;
    }
}

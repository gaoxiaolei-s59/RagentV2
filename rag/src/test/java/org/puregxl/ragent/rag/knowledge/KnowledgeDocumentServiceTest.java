package org.puregxl.ragent.rag.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.puregxl.ragent.framework.exception.ClientException;
import org.puregxl.ragent.rag.knowledge.config.KnowledgeStorageProperties;
import org.puregxl.ragent.rag.knowledge.dto.request.KnowledgeDocumentUploadRequest;
import org.puregxl.ragent.rag.knowledge.entity.KnowledgeBase;
import org.puregxl.ragent.rag.knowledge.entity.KnowledgeDocument;
import org.puregxl.ragent.rag.knowledge.mapper.KnowledgeBaseMapper;
import org.puregxl.ragent.rag.knowledge.mapper.KnowledgeDocumentMapper;
import org.puregxl.ragent.rag.knowledge.service.impl.KnowledgeDocumentServiceImpl;
import org.puregxl.ragent.rag.knowledge.storage.DocumentStorage;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 上传业务测试：真实读取文件和计算校验值，模拟数据库及外部存储边界。 */
class KnowledgeDocumentServiceTest {
    @TempDir Path temporaryDirectory;
    private KnowledgeBaseMapper baseMapper;
    private KnowledgeDocumentMapper documentMapper;
    private DocumentStorage storage;
    private PlatformTransactionManager transactionManager;
    private KnowledgeDocumentServiceImpl service;
    private KnowledgeBase base;
    private KnowledgeStorageProperties properties;

    @BeforeEach
    void setUp() {
        baseMapper = mock(KnowledgeBaseMapper.class);
        documentMapper = mock(KnowledgeDocumentMapper.class);
        storage = mock(DocumentStorage.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        base = new KnowledgeBase(); base.setId(1L); base.setName("测试库");
        when(baseMapper.selectById(1L)).thenReturn(base);
        when(baseMapper.selectActiveForUpdate(1L)).thenReturn(base);
        when(documentMapper.insert(any(KnowledgeDocument.class))).thenReturn(1);
        properties = new KnowledgeStorageProperties();
        properties.setTemporaryDirectory(temporaryDirectory);
        service = new KnowledgeDocumentServiceImpl(baseMapper, documentMapper, storage, properties, transactionManager);
    }

    @Test
    void uploadsBytesAndPersistsMetadataWithEffectiveChunkSettings() throws Exception {
        AtomicReference<byte[]> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            stored.set(Files.readAllBytes(invocation.getArgument(1, Path.class)));
            return null;
        }).when(storage).upload(anyString(), any(Path.class), anyString());
        KnowledgeDocumentUploadRequest request = new KnowledgeDocumentUploadRequest();
        request.setName("自定义展示名称"); request.setChunkSize(500); request.setChunkOverlap(50);
        var result = service.uploadKnowledgeDocument(1L, request, file("example.txt", "hello"));
        assertThat(stored.get()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        assertThat(result.getId()).isNotBlank();
        assertThat(result.getKnowledgeBaseId()).isEqualTo("1");
        assertThat(result.getName()).isEqualTo("自定义展示名称");
        assertThat(result.getOriginalFilename()).isEqualTo("example.txt");
        assertThat(result.getSha256()).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        assertThat(result.getFileSize()).isEqualTo(5);
        assertThat(result.getChunkSize()).isEqualTo(500);
        assertThat(result.getChunkOverlap()).isEqualTo(50);
        assertThat(result.getProcessStatus().name()).isEqualTo("PENDING");
        verify(documentMapper).insert(any(KnowledgeDocument.class));
        verify(transactionManager).commit(any());
        assertTemporaryFilesRemoved();
    }

    @Test
    void rejectsMissingEmptyUnsupportedOrUnsafeFileBeforeUploading() {
        for (var file : new MockMultipartFile[]{null, file("empty.txt", ""), file("program.exe", "x"),
                file("../escape.txt", "x"), file("..\\escape.txt", "x")}) {
            assertThatThrownBy(() -> service.uploadKnowledgeDocument(1L, new KnowledgeDocumentUploadRequest(), file))
                .isInstanceOf(ClientException.class);
        }
        verifyNoInteractions(storage, documentMapper);
    }

    @Test
    void rejectsMissingOrDisabledKnowledgeBase() {
        when(baseMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.uploadKnowledgeDocument(1L, new KnowledgeDocumentUploadRequest(), file("x.txt", "x")))
            .isInstanceOf(ClientException.class);
        when(baseMapper.selectById(1L)).thenReturn(base); base.setEnabled(false);
        assertThatThrownBy(() -> service.uploadKnowledgeDocument(1L, new KnowledgeDocumentUploadRequest(), file("x.txt", "x")))
            .isInstanceOf(ClientException.class);
        verifyNoInteractions(storage, documentMapper);
    }

    @Test
    void validatesEffectiveOverlapAgainstKnowledgeBaseDefault() {
        base.setChunkSize(50);
        assertThatThrownBy(() -> service.uploadKnowledgeDocument(1L, new KnowledgeDocumentUploadRequest(), file("x.txt", "x")))
            .isInstanceOf(ClientException.class).hasMessageContaining("重叠");
        verifyNoInteractions(storage, documentMapper);
    }

    @Test
    void deletesUploadedObjectWhenDatabaseInsertFails() throws Exception {
        when(documentMapper.insert(any(KnowledgeDocument.class))).thenThrow(new IllegalStateException("insert failed"));
        assertThatThrownBy(() -> service.uploadKnowledgeDocument(1L, new KnowledgeDocumentUploadRequest(), file("x.txt", "x")))
            .hasMessageContaining("insert failed");
        verify(storage).delete(anyString());
        verify(transactionManager).rollback(any());
        assertTemporaryFilesRemoved();
    }

    @Test
    void rechecksParentAfterStorageUploadAndCompensatesIfDeleted() throws Exception {
        when(baseMapper.selectActiveForUpdate(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.uploadKnowledgeDocument(1L, new KnowledgeDocumentUploadRequest(), file("x.txt", "x")))
            .isInstanceOf(ClientException.class);
        verify(storage).delete(anyString());
        verify(documentMapper, never()).insert(any(KnowledgeDocument.class));
        assertTemporaryFilesRemoved();
    }

    @Test
    void cleansUpPossibleObjectAfterStorageFailureAndDoesNotInsertDocument() throws Exception {
        doThrow(new IllegalStateException("storage failed")).when(storage).upload(anyString(), any(Path.class), anyString());
        assertThatThrownBy(() -> service.uploadKnowledgeDocument(1L, new KnowledgeDocumentUploadRequest(), file("x.txt", "x")))
            .hasMessageContaining("storage failed");
        verify(storage).delete(anyString());
        verifyNoInteractions(documentMapper);
        assertTemporaryFilesRemoved();
    }

    @Test
    void limitsActualStreamBytesEvenIfReportedSizeIsSmaller() throws Exception {
        properties.setMaxFileSize(DataSize.ofBytes(2));
        MockMultipartFile misleadingFile = new MockMultipartFile("file", "x.txt", "text/plain", new byte[]{1, 2, 3}) {
            @Override public long getSize() { return 1; }
        };
        assertThatThrownBy(() -> service.uploadKnowledgeDocument(1L, new KnowledgeDocumentUploadRequest(), misleadingFile))
            .isInstanceOf(ClientException.class).hasMessageContaining("大小");
        verifyNoInteractions(storage, documentMapper);
        assertTemporaryFilesRemoved();
    }

    @Test
    void preservesOriginalFailureWhenCompensationAlsoFails() throws Exception {
        when(documentMapper.insert(any(KnowledgeDocument.class))).thenThrow(new IllegalStateException("insert failed"));
        doThrow(new IllegalStateException("cleanup failed")).when(storage).delete(anyString());
        assertThatThrownBy(() -> service.uploadKnowledgeDocument(1L, new KnowledgeDocumentUploadRequest(), file("x.txt", "x")))
            .hasMessageContaining("insert failed").satisfies(error -> {
                assertThat(error.getSuppressed()).hasSize(1);
                assertThat(error.getSuppressed()[0]).hasMessageContaining("cleanup failed");
            });
        assertTemporaryFilesRemoved();
    }

    @Test
    void compensatesWhenTransactionCommitFailsAfterInsert() throws Exception {
        doThrow(new IllegalStateException("commit failed")).when(transactionManager).commit(any());
        assertThatThrownBy(() -> service.uploadKnowledgeDocument(1L, new KnowledgeDocumentUploadRequest(), file("x.txt", "x")))
            .hasMessageContaining("commit failed");
        verify(documentMapper).insert(any(KnowledgeDocument.class));
        verify(storage).delete(anyString());
        assertTemporaryFilesRemoved();
    }

    @Test
    void compensatesWhenKnowledgeBaseBecomesDisabledDuringUpload() throws Exception {
        KnowledgeBase disabled = new KnowledgeBase(); disabled.setId(1L); disabled.setEnabled(false);
        when(baseMapper.selectActiveForUpdate(1L)).thenReturn(disabled);
        assertThatThrownBy(() -> service.uploadKnowledgeDocument(1L, new KnowledgeDocumentUploadRequest(), file("x.txt", "x")))
            .isInstanceOf(ClientException.class).hasMessageContaining("禁用");
        verify(storage).delete(anyString());
        verify(documentMapper, never()).insert(any(KnowledgeDocument.class));
        assertTemporaryFilesRemoved();
    }

    private MockMultipartFile file(String name, String text) {
        return new MockMultipartFile("file", name, "text/plain", text.getBytes(StandardCharsets.UTF_8));
    }

    private void assertTemporaryFilesRemoved() throws Exception {
        try (var files = Files.list(temporaryDirectory)) {
            assertThat(files).isEmpty();
        }
    }
}

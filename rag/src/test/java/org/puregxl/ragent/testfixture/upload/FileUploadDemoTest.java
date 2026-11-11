package org.puregxl.ragent.testfixture.upload;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.puregxl.ragent.framework.convention.Result;
import org.puregxl.ragent.framework.web.Results;
import org.puregxl.ragent.rag.knowledge.entity.KnowledgeDocument;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文件上传最小示例：Controller → Service → ServiceImpl → 本地文件。
 * 所有示例实现只存在于 test；不启动数据库、模型服务或整个应用。
 * 文档实体仅返回给测试展示元数据，尚未调用 Mapper 保存。
 * 项目主源码可编译后，可在根目录执行：
 * mvn -pl rag -am -Dtest=FileUploadDemoTest -Dsurefire.failIfNoSpecifiedTests=false test
 */
class FileUploadDemoTest {

    private static final String UPLOAD_URL = "/demo/knowledge/documents/upload";
    private static final byte[] CONTENT = "这是上传测试文件。\n".getBytes(StandardCharsets.UTF_8);

    /** 每个测试使用独立目录，JUnit 在测试结束后自动清理。 */
    @TempDir
    Path uploadRoot;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        UploadService service = new LocalUploadService(uploadRoot);
        mvc = MockMvcBuilders.standaloneSetup(new UploadController(service)).build();
    }

    @Test
    void uploadsFileAndReturnsDocumentMetadata() throws Exception {
        mvc.perform(multipart(UPLOAD_URL)
                .file(file("示例.txt", CONTENT))
                .param("knowledgeBaseId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("0"))
            .andExpect(jsonPath("$.data.knowledgeBaseId").value(1))
            .andExpect(jsonPath("$.data.originalFilename").value("示例.txt"))
            .andExpect(jsonPath("$.data.fileExtension").value("txt"))
            .andExpect(jsonPath("$.data.fileSize").value(CONTENT.length))
            .andExpect(jsonPath("$.data.contentType").value("text/plain"))
            .andExpect(jsonPath("$.data.processStatus").value("PENDING"))
            .andExpect(jsonPath("$.data.sha256")
                .value("bf319743dc236734a2f85b7cde25ebc528842353ca40f5b633980f8802be3c10"))
            .andExpect(jsonPath("$.data.storageKey").isString());

        List<Path> stored = storedFiles();
        assertThat(stored).hasSize(1);
        assertThat(Files.readAllBytes(stored.get(0))).isEqualTo(CONTENT);
        assertThat(uploadRoot.relativize(stored.get(0)).getName(0).toString()).isEqualTo("1");
    }

    @Test
    void rejectsEmptyFileWithoutWritingAnything() throws Exception {
        mvc.perform(multipart(UPLOAD_URL).file(file("empty.txt", new byte[0]))
                .param("knowledgeBaseId", "1"))
            .andExpect(status().isBadRequest());
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void rejectsUnsupportedExtensionWithoutWritingAnything() throws Exception {
        mvc.perform(multipart(UPLOAD_URL).file(file("program.exe", CONTENT))
                .param("knowledgeBaseId", "1"))
            .andExpect(status().isBadRequest());
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void rejectsFilenameContainingAPath() throws Exception {
        for (String filename : List.of("../escape.txt", "..\\escape.txt")) {
            mvc.perform(multipart(UPLOAD_URL).file(file(filename, CONTENT))
                    .param("knowledgeBaseId", "1"))
                .andExpect(status().isBadRequest());
        }
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void sameFilenameDoesNotOverwritePreviousUpload() throws Exception {
        for (String text : List.of("第一次上传", "第二次上传")) {
            mvc.perform(multipart(UPLOAD_URL)
                    .file(file("same.md", text.getBytes(StandardCharsets.UTF_8)))
                    .param("knowledgeBaseId", "1"))
                .andExpect(status().isOk());
        }
        List<Path> stored = storedFiles();
        assertThat(stored).hasSize(2);
        assertThat(List.of(Files.readString(stored.get(0)), Files.readString(stored.get(1))))
            .containsExactlyInAnyOrder("第一次上传", "第二次上传");
    }

    private MockMultipartFile file(String filename, byte[] content) {
        // 第一个参数必须与 Controller 中 @RequestParam("file") 一致。
        return new MockMultipartFile("file", filename, "text/plain", content);
    }

    private List<Path> storedFiles() throws IOException {
        try (var paths = Files.walk(uploadRoot)) {
            return paths.filter(Files::isRegularFile).toList();
        }
    }

    /** Controller 层：接收 multipart 参数并返回统一响应。 */
    @RestController
    static class UploadController {
        private final UploadService service;

        UploadController(UploadService service) {
            this.service = service;
        }

        @PostMapping(value = UPLOAD_URL, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public Result<KnowledgeDocument> upload(@RequestParam("knowledgeBaseId") Long knowledgeBaseId,
                                                @RequestParam("file") MultipartFile file) throws IOException {
            return Results.success(service.upload(knowledgeBaseId, file));
        }
    }

    /** Service 层：定义上传能力。 */
    interface UploadService {
        KnowledgeDocument upload(Long knowledgeBaseId, MultipartFile file) throws IOException;
    }

    /** ServiceImpl 层：校验、保存文件、组装文档信息。 */
    static class LocalUploadService implements UploadService {
        private final Path root;

        LocalUploadService(Path root) {
            this.root = root;
        }

        @Override
        public KnowledgeDocument upload(Long knowledgeBaseId, MultipartFile file) throws IOException {
            // 1. 校验请求。正式业务还需要查询知识库是否存在、是否已删除或禁用。
            if (knowledgeBaseId == null || knowledgeBaseId <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "知识库 ID 必须大于 0");
            }
            if (file.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件不能为空");
            }
            String filename = file.getOriginalFilename();
            if (filename == null || filename.isBlank()
                    || filename.contains("/") || filename.contains("\\") || filename.indexOf('\0') >= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件名不合法");
            }
            int dot = filename.lastIndexOf('.');
            String extension = dot > 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
            if (!List.of("txt", "md").contains(extension)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "示例仅支持 txt、md 文件");
            }

            // 2. 以知识库 ID 分目录，生成唯一文件名，避免原文件名导致覆盖或路径穿越。
            Path directory = root.resolve(knowledgeBaseId.toString());
            Files.createDirectories(directory);
            MessageDigest digest = sha256();
            Path target = Files.createTempFile(directory, "upload-", "." + extension);
            try (var input = new DigestInputStream(file.getInputStream(), digest)) {
                // 流式写入，替换的仅是上一步由本次请求创建的空文件。
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
                throw exception;
            }

            // 3. 组装元数据。storageKey 是相对路径，便于后续迁移存储位置。
            KnowledgeDocument document = new KnowledgeDocument();
            document.setKnowledgeBaseId(knowledgeBaseId);
            document.setName(filename);
            document.setOriginalFilename(filename);
            document.setFileExtension(extension);
            document.setContentType(file.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType());
            document.setFileSize(Files.size(target));
            document.setStorageKey(root.relativize(target).toString().replace('\\', '/'));
            document.setSha256(HexFormat.of().formatHex(digest.digest()));
            // 正式业务在此复制知识库切分参数，再通过 Mapper 保存；本 demo 不写数据库。
            // 返回实体仅用于展示，正式 HTTP 接口应转换为 VO，并将 Long ID 按字符串返回。
            return document;
        }

        private MessageDigest sha256() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
            }
        }
    }
}

package org.puregxl.ragent.rag.knowledge.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/** RustFS 连接和上传限制；凭据通过环境变量覆盖，不生成包含密钥的 toString。 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "knowledge.storage")
public class KnowledgeStorageProperties {
    /** RustFS S3 API 地址，不是控制台地址。 */
    @NotNull
    private URI endpoint = URI.create("http://127.0.0.1:9000");
    /** 本地开发默认账号，实际环境使用 RUSTFS_ACCESS_KEY。 */
    @NotBlank
    private String accessKey = "rustfsadmin";
    /** 本地开发默认密钥，实际环境使用 RUSTFS_SECRET_KEY。 */
    @NotBlank
    private String secretKey = "rustfsadmin";
    /** 统一存放知识库原文件的私有桶。 */
    @NotBlank
    private String bucket = "ragent-documents";
    @NotBlank
    private String region = "us-east-1";
    /** 单文件上限，与 Web 层 multipart 限制配合使用。 */
    @NotNull
    private DataSize maxFileSize = DataSize.ofMegabytes(50);
    /** 只限制上传格式，不代表这些格式已经实现解析。 */
    @NotNull
    private List<String> allowedExtensions = List.of("txt", "md", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv");
    /** 流式计算校验值及 SDK 上传使用的临时目录，上传结束清理文件。 */
    @NotNull
    private Path temporaryDirectory = Path.of(System.getProperty("java.io.tmpdir"));
}

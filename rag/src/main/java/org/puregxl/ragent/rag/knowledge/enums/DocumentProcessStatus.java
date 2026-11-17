package org.puregxl.ragent.rag.knowledge.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 文档解析切分状态，以字符串持久化。
 */
@Getter
@RequiredArgsConstructor
public enum DocumentProcessStatus {
    /**
     * 等待解析和切分。
     */
    PENDING("PENDING"),
    /**
     * 正在解析或切分。
     */
    PROCESSING("PROCESSING"),
    /**
     * 解析切分完成，不代表切片向量已经入库。
     */
    READY("READY"),
    /**
     * 解析切分失败，原因保存在文档 errorMessage 中。
     */
    FAILED("FAILED");

    /**
     * 数据库中保存的稳定状态值，不依赖枚举序号。
     */
    @EnumValue
    private final String value;
}

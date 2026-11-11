package org.puregxl.ragent.rag.knowledge.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 切片向量索引状态，与启用、删除状态分别维护。 */
@Getter
@RequiredArgsConstructor
public enum ChunkVectorStatus {
    /** 等待生成或重新生成向量。 */
    PENDING("PENDING"),
    /** 正在向量化或写入向量索引。 */
    INDEXING("INDEXING"),
    /** 当前正文对应的向量已成功建立索引。 */
    READY("READY"),
    /** 向量处理失败，原因保存在切片 errorMessage 中。 */
    FAILED("FAILED");

    /** 数据库中保存的稳定状态值，不依赖枚举序号。 */
    @EnumValue
    private final String value;
}

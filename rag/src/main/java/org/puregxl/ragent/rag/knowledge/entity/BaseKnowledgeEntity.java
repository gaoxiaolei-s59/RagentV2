package org.puregxl.ragent.rag.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.util.Date;

/** 知识库、文档和切片的公共持久化字段。 */
@Data
public abstract class BaseKnowledgeEntity {

    /** 由 MyBatis-Plus 分配雪花 ID，不使用数据库自增。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 当前记录自身是否启用；父级是否启用由业务查询另行校验。 */
    private Boolean enabled = true;

    /** 逻辑删除标记：0 正常、1 已删除，通用查询自动过滤已删除记录。 */
    @TableLogic(value = "0", delval = "1")
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag = 0;

    /** 创建时间，由 framework 的字段填充器在插入时赋值。 */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /** 修改时间；实体写入时自动填充，Wrapper 更新由业务显式赋值。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}

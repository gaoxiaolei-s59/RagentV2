package org.puregxl.ragent.rag.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.puregxl.ragent.rag.knowledge.entity.KnowledgeBase;

/** 知识库持久化接口，通用 CRUD 及逻辑删除 SQL 由 MyBatis-Plus 提供。 */
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {
    /**
     * 锁定未删除的知识库，供修改、删除和状态切换复用。
     * <p>必须在事务内调用，行锁持续到该事务提交或回滚。</p>
     *
     * @param id 知识库 ID
     * @return 未删除的知识库；不存在或已删除时返回 null
     */
    @Select("SELECT * FROM knowledge_base WHERE id = #{id} AND del_flag = 0 FOR UPDATE")
    KnowledgeBase selectActiveForUpdate(@Param("id") Long id);
}

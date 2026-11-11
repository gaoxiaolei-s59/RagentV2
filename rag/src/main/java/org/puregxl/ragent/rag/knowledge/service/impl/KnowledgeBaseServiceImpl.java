package org.puregxl.ragent.rag.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.puregxl.ragent.framework.exception.ClientException;
import org.puregxl.ragent.rag.knowledge.dto.KnowledgeBasePageRequest;
import org.puregxl.ragent.rag.knowledge.dto.KnowledgeBaseSaveRequest;
import org.puregxl.ragent.rag.knowledge.entity.KnowledgeBase;
import org.puregxl.ragent.rag.knowledge.entity.KnowledgeChunk;
import org.puregxl.ragent.rag.knowledge.entity.KnowledgeDocument;
import org.puregxl.ragent.rag.knowledge.enums.ChunkVectorStatus;
import org.puregxl.ragent.rag.knowledge.mapper.KnowledgeBaseMapper;
import org.puregxl.ragent.rag.knowledge.mapper.KnowledgeChunkMapper;
import org.puregxl.ragent.rag.knowledge.mapper.KnowledgeDocumentMapper;
import org.puregxl.ragent.rag.knowledge.service.KnowledgeBaseService;
import org.puregxl.ragent.rag.knowledge.vo.KnowledgeBasePageVO;
import org.puregxl.ragent.rag.knowledge.vo.KnowledgeBaseVO;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Objects;

/**
 * 基于 MyBatis-Plus 的知识库业务实现。
 * <p>修改、删除和状态切换通过事务及父记录行锁协调同一知识库的写操作；
 * 查询由 MyBatis-Plus 自动排除逻辑删除记录。</p>
 */
@Service
@Profile("mysql")
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    /** 知识库配置读写及父记录行锁。 */
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    /** 删除知识库时批量更新其文档的逻辑删除标记。 */
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    /** 查询向量索引状态，并在删除知识库时处理所属切片。 */
    private final KnowledgeChunkMapper knowledgeChunkMapper;

    /**
     * {@inheritDoc}
     * <p>新实体的 ID 和时间由 MyBatis-Plus 生成、填充。</p>
     */
    @Override
    @Transactional
    public Long create(KnowledgeBaseSaveRequest request) {
        KnowledgeBase base = new KnowledgeBase();
        base.setName(request.getName().trim());
        base.setDescription(request.getDescription());
        base.setEmbeddingModelId(normalizeModelId(request.getEmbeddingModelId()));
        base.setEmbeddingDimension(request.getEmbeddingDimension());
        base.setChunkSize(request.getChunkSize());
        base.setChunkOverlap(request.getChunkOverlap());
        knowledgeBaseMapper.insert(base);
        return base.getId();
    }

    /**
     * {@inheritDoc}
     * <p>先锁定知识库，再检查向量模型变更条件，最后显式更新允许修改的列。</p>
     */
    @Override
    @Transactional
    public void update(Long id, KnowledgeBaseSaveRequest request) {
        KnowledgeBase current = requireForUpdate(id);
        String modelId = normalizeModelId(request.getEmbeddingModelId());
        // 成功建立的向量依赖原模型和维度，禁用切片同样需要保留此保护。
        boolean modelChanged = !Objects.equals(current.getEmbeddingModelId(), modelId)
            || !Objects.equals(current.getEmbeddingDimension(), request.getEmbeddingDimension());
        if (modelChanged && knowledgeChunkMapper.selectCount(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getKnowledgeBaseId, id)
                .eq(KnowledgeChunk::getVectorStatus, ChunkVectorStatus.READY)) > 0) {
            throw new ClientException("知识库已有向量索引，修改向量模型或维度前需要重建索引");
        }
        // 使用显式更新列，既支持清空可选字段，也避免实体默认值重置禁用状态。
        knowledgeBaseMapper.update(null, Wrappers.<KnowledgeBase>lambdaUpdate()
            .eq(KnowledgeBase::getId, id)
            .set(KnowledgeBase::getName, request.getName().trim())
            .set(KnowledgeBase::getDescription, request.getDescription())
            .set(KnowledgeBase::getEmbeddingModelId, modelId)
            .set(KnowledgeBase::getEmbeddingDimension, request.getEmbeddingDimension())
            .set(KnowledgeBase::getChunkSize, request.getChunkSize())
            .set(KnowledgeBase::getChunkOverlap, request.getChunkOverlap())
            .set(KnowledgeBase::getUpdateTime, new Date()));
    }

    /**
     * {@inheritDoc}
     * <p>按切片、文档、知识库的顺序更新删除标记，任一步抛出运行时异常时整体回滚。</p>
     */
    @Override
    @Transactional
    public void delete(Long id) {
        KnowledgeBase current = requireForUpdate(id);
        Date now = new Date();
        // 只逻辑删除数据库记录。文件和向量的物理清理留待对应存储服务接入。
        knowledgeChunkMapper.update(null, Wrappers.<KnowledgeChunk>lambdaUpdate()
            .eq(KnowledgeChunk::getKnowledgeBaseId, id)
            .set(KnowledgeChunk::getDelFlag, 1)
            .set(KnowledgeChunk::getUpdateTime, now));
        knowledgeDocumentMapper.update(null, Wrappers.<KnowledgeDocument>lambdaUpdate()
            .eq(KnowledgeDocument::getKnowledgeBaseId, id)
            .set(KnowledgeDocument::getDelFlag, 1)
            .set(KnowledgeDocument::getUpdateTime, now));
        current.setUpdateTime(now);
        knowledgeBaseMapper.deleteById(current);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public KnowledgeBaseVO detail(Long id) {
        validateId(id);
        KnowledgeBase base = knowledgeBaseMapper.selectById(id);
        if (base == null) {
            throw new ClientException("知识库不存在或已删除");
        }
        return toVO(base);
    }

    /**
     * {@inheritDoc}
     * <p>创建时间相同时以雪花 ID 排序，保证相同查询条件下分页顺序稳定。</p>
     */
    @Override
    public KnowledgeBasePageVO page(KnowledgeBasePageRequest request) {
        var query = Wrappers.<KnowledgeBase>lambdaQuery()
            .like(StringUtils.hasText(request.getName()), KnowledgeBase::getName,
                request.getName() == null ? null : request.getName().trim())
            .eq(request.getEnabled() != null, KnowledgeBase::getEnabled, request.getEnabled())
            .orderByDesc(KnowledgeBase::getCreateTime, KnowledgeBase::getId);
        Page<KnowledgeBase> page = knowledgeBaseMapper.selectPage(
            new Page<>(request.getPageNum(), request.getPageSize()), query);
        return new KnowledgeBasePageVO(page.getCurrent(), page.getSize(), page.getTotal(),
            page.getRecords().stream().map(this::toVO).toList());
    }

    /**
     * {@inheritDoc}
     * <p>后续检索入口需要同时检查知识库及其子记录的启用状态。</p>
     */
    @Override
    @Transactional
    public void setEnabled(Long id, boolean enabled) {
        requireForUpdate(id);
        // 不修改子记录自身的启用状态，避免恢复知识库时覆盖单独禁用的文档或切片。
        knowledgeBaseMapper.update(null, Wrappers.<KnowledgeBase>lambdaUpdate()
            .eq(KnowledgeBase::getId, id)
            .set(KnowledgeBase::getEnabled, enabled)
            .set(KnowledgeBase::getUpdateTime, new Date()));
    }

    /**
     * 校验 ID 并锁定未删除的知识库，必须在事务内调用。
     *
     * @param id 知识库 ID
     * @return 被当前事务锁定的知识库
     * @throws ClientException ID 不合法或知识库不存在
     */
    private KnowledgeBase requireForUpdate(Long id) {
        validateId(id);
        KnowledgeBase base = knowledgeBaseMapper.selectActiveForUpdate(id);
        if (base == null) {
            throw new ClientException("知识库不存在或已删除");
        }
        return base;
    }

    /**
     * 校验外部传入的主键，防止无效 ID 进入数据库查询。
     *
     * @param id 必须大于 0 的知识库 ID
     */
    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new ClientException("知识库ID必须大于0");
        }
    }

    /**
     * 去除模型 ID 首尾空白，将空字符串统一保存为 null。
     *
     * @param modelId 可选模型配置 ID
     * @return 规范化后的模型 ID
     */
    private String normalizeModelId(String modelId) {
        return StringUtils.hasText(modelId) ? modelId.trim() : null;
    }

    /**
     * 转换持久化实体，隐藏删除标记并将 ID 转换成字符串。
     *
     * @param base 数据库中的知识库记录
     * @return 详情及列表共用的展示对象
     */
    private KnowledgeBaseVO toVO(KnowledgeBase base) {
        return new KnowledgeBaseVO(base.getId().toString(), base.getName(), base.getDescription(),
            base.getEmbeddingModelId(), base.getEmbeddingDimension(), base.getChunkSize(),
            base.getChunkOverlap(), base.getEnabled(), base.getCreateTime(), base.getUpdateTime());
    }
}

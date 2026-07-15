package com.xyoo.helper.repository;

import com.xyoo.helper.entity.DocInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文档信息数据访问层
 */
@Repository
public interface DocInfoRepository extends JpaRepository<DocInfo, Long> {

    /**
     * 查询所有展示的文档列表，按创建时间倒序
     *
     * @param isVisible 是否展示
     */
    List<DocInfo> findByIsVisibleOrderByCreateTimeDesc(Boolean isVisible);

    /**
     * 根据所属菜单查询展示的文档列表，按创建时间倒序
     *
     * @param menuId    所属菜单ID
     * @param isVisible 是否展示
     */
    List<DocInfo> findByMenuIdAndIsVisibleOrderByCreateTimeDesc(Long menuId, Boolean isVisible);

    /**
     * 根据业务文档ID查询
     *
     * @param docId 文档业务ID
     */
    Optional<DocInfo> findByDocId(String docId);

    /**
     * 根据标签模糊查询展示的文档，按创建时间倒序
     *
     * @param tag       标签关键词
     * @param isVisible 是否展示
     */
    List<DocInfo> findByDocTagsContainingAndIsVisibleOrderByCreateTimeDesc(String tag, Boolean isVisible);

    /**
     * 模糊搜索：根据文档标题、标签、内容匹配（仅展示的文档）
     *
     * @param keyword   搜索关键词
     * @param isVisible 是否展示
     */
    @Query("SELECT d FROM DocInfo d WHERE d.isVisible = :isVisible AND (" +
           "d.docTitle LIKE %:keyword% OR " +
           "d.docTags LIKE %:keyword% OR " +
           "d.docContent LIKE %:keyword%) " +
           "ORDER BY d.createTime DESC")
    List<DocInfo> searchByKeyword(@Param("keyword") String keyword,
                                  @Param("isVisible") Boolean isVisible);
}

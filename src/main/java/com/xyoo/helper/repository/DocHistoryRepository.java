package com.xyoo.helper.repository;

import com.xyoo.helper.entity.DocHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文档编辑历史数据访问层（拉链表）
 */
@Repository
public interface DocHistoryRepository extends JpaRepository<DocHistory, Long> {

    /**
     * 根据文档ID查询所有编辑历史，按版本号倒序
     *
     * @param docInfoId 文档信息表主键ID
     */
    List<DocHistory> findByDocInfoIdOrderByVersionDesc(Long docInfoId);

    /**
     * 查询指定文档的最大版本号
     *
     * @param docInfoId 文档信息表主键ID
     */
    DocHistory findTopByDocInfoIdOrderByVersionDesc(Long docInfoId);
}

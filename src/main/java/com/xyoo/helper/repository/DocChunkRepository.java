package com.xyoo.helper.repository;

import com.xyoo.helper.entity.DocChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 文档切片仓储。
 */
@Repository
public interface DocChunkRepository extends JpaRepository<DocChunk, Long> {

    /** 查询文档的全部切片（按序号升序） */
    List<DocChunk> findByDocIdOrderByChunkIndexAsc(String docId);

    /** 删除文档的全部切片 */
    void deleteByDocId(String docId);

    /** 统计文档的切片数量 */
    int countByDocId(String docId);
}

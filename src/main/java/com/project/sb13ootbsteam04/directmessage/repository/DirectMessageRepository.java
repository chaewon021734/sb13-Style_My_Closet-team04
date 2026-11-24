package com.project.sb13ootbsteam04.directmessage.repository;

import com.project.sb13ootbsteam04.directmessage.entity.DirectMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {

  // 첫 페이지 조회
  @Query("""
            SELECT dm
            FROM DirectMessage dm
            WHERE (
                (dm.sender.id = :userId AND dm.receiver.id = :targetUserId)
                OR
                (dm.sender.id = :targetUserId AND dm.receiver.id = :userId)
            )
            ORDER BY dm.createdAt DESC, dm.id DESC
            """)
  List<DirectMessage> findFirstPage(
      @Param("userId") UUID userId,
      @Param("targetUserId") UUID targetUserId,
      Pageable pageable
  );


  // 두 사용자 사이의 전체 메시지 개수
  @Query("""
            SELECT COUNT(dm)
            FROM DirectMessage dm
            WHERE (
                (dm.sender.id = :userId AND dm.receiver.id = :targetUserId)
                OR
                (dm.sender.id = :targetUserId AND dm.receiver.id = :userId)
            )
            """)
  long countMessagesBetweenUsers(
      @Param("userId") UUID userId,
      @Param("targetUserId") UUID targetUserId
  );
}
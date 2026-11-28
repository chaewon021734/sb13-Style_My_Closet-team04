package com.project.sb13ootbsteam04.notification.repository;

import com.project.sb13ootbsteam04.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  // 첫 페이지 조회
  @Query("""
            SELECT n
            FROM Notification n
            WHERE n.receiver.id = :receiverId
            ORDER BY n.createdAt DESC, n.id DESC
            """)
  List<Notification> findFirstPage(
      @Param("receiverId") UUID receiverId,
      Pageable pageable
  );

  // 다음 페이지 조회
  @Query("""
            SELECT n
            FROM Notification n
            WHERE n.receiver.id = :receiverId
            AND (
                n.createdAt < :cursor
                OR (
                    n.createdAt = :cursor
                    AND n.id < :idAfter
                )
            )
            ORDER BY n.createdAt DESC, n.id DESC
            """)
  List<Notification> findNextPage(
      @Param("receiverId") UUID receiverId,
      @Param("cursor") Instant cursor,
      @Param("idAfter") UUID idAfter,
      Pageable pageable
  );

  // 수신자의 전체 알림 개수
  long countByReceiver_Id(UUID receiverId);

  // 같은 내용의 알림이 이미 있는지 확인 (날씨 변화 중복 방지)
  boolean existsByReceiver_IdAndContent(UUID receiverId, String content);
}

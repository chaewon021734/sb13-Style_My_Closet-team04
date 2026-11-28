package com.project.sb13ootbsteam04.feed.repository;

import com.project.sb13ootbsteam04.feed.entity.FeedClothes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeedClothesRepository extends JpaRepository<FeedClothes, UUID> {

    // 특정 피드에 등록된 의상 목록 조회
    List<FeedClothes> findByFeed_Id(UUID feedId);

    // 특정 피드에 등록된 의상 연결 정보 삭제
    void deleteByFeed_Id(UUID feedId);

    // 특정 피드에 특정 의상이 등록되어 있는지 확인
    boolean existsByFeed_IdAndClothesId(UUID feedId, UUID clothesId);
}
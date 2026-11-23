package com.project.sb13ootbsteam04.clothes.repository;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.repository.dto.ClothesSearchCondition;
import com.project.sb13ootbsteam04.clothes.repository.dto.ClothesSearchResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class ClothesRepositoryCustomImpl implements ClothesRepositoryCustom {

  private final EntityManager entityManager;

  @Override
  @Transactional(readOnly = true)
  public ClothesSearchResult search(ClothesSearchCondition condition) {
    validate(condition);

    String filterClause = buildFilterClause(condition);
    List<Clothes> fetchedClothes = fetchPage(condition, filterClause);
    boolean hasNext = fetchedClothes.size() > condition.limit();
    List<Clothes> content = hasNext ? fetchedClothes.subList(0, condition.limit()) : fetchedClothes;

    if (!content.isEmpty()) {
      initializeAttributes(content);
    }
    // 첫 페이지에 일치하는 의상이 모두 들어오면 별도 COUNT 없이 전체 건수를 알 수 있다.
    long totalCount = condition.cursor() == null && !hasNext ? content.size() : count(condition, filterClause);
    return new ClothesSearchResult(content, hasNext, totalCount);
  }

  private List<Clothes> fetchPage(ClothesSearchCondition condition, String filterClause) {
    // 응답에는 소유자 ID만 필요하므로 사용자 엔티티 전체를 조회하지 않는다.
    StringBuilder jpql = new StringBuilder("select c from Clothes c").append(filterClause);

    if (condition.cursor() != null) {
      jpql.append(" and (c.createdAt < :cursor")
          .append(" or (c.createdAt = :cursor and c.id < :idAfter))");
    }

    jpql.append(" order by c.createdAt desc, c.id desc");

    TypedQuery<Clothes> query = entityManager.createQuery(jpql.toString(), Clothes.class);
    bindFilters(query, condition);

    if (condition.cursor() != null) {
      query.setParameter("cursor", condition.cursor());
      query.setParameter("idAfter", condition.idAfter());
    }

    // 추가 1건은 다음 페이지 존재 여부를 확인하는 용도로만 사용한다.
    int queryLimit = condition.limit() == Integer.MAX_VALUE ? Integer.MAX_VALUE : condition.limit() + 1;
    return query.setMaxResults(queryLimit).getResultList();
  }

  private long count(ClothesSearchCondition condition, String filterClause) {
    // 현재 소유자·타입에 맞는 전체 건수이므로 커서 조건은 제외한다.
    TypedQuery<Long> query = entityManager.createQuery(
        "select count(c) from Clothes c" + filterClause,
        Long.class
    );
    bindFilters(query, condition);
    return query.getSingleResult();
  }

  private void initializeAttributes(List<Clothes> content) {
    List<UUID> clothesIds = content.stream().map(Clothes::getId).toList();
    // 페이지네이션을 마친 의상에 대해서만 컬렉션을 fetch join해 메모리 페이징을 방지한다.
    entityManager.createQuery(
            """
                select distinct c
                from Clothes c
                left join fetch c.attributes a
                left join fetch a.definition
                where c.id in :ids
                """,
            Clothes.class
        )
        .setParameter("ids", clothesIds)
        .getResultList();
  }

  private String buildFilterClause(ClothesSearchCondition condition) {
    String clause = " where c.owner.id = :ownerId";
    if (condition.typeEqual() != null) {
      clause += " and c.type = :typeEqual";
    }
    return clause;
  }

  private void bindFilters(TypedQuery<?> query, ClothesSearchCondition condition) {
    query.setParameter("ownerId", condition.ownerId());
    if (condition.typeEqual() != null) {
      query.setParameter("typeEqual", condition.typeEqual());
    }
  }

  private void validate(ClothesSearchCondition condition) {
    if (condition == null) {
      throw new IllegalArgumentException("의상 조회 조건은 필수입니다.");
    }
    if (condition.ownerId() == null) {
      throw new IllegalArgumentException("소유자 ID는 필수입니다.");
    }

    if (condition.limit() < 1) {
      throw new IllegalArgumentException("limit은 1 이상이어야 합니다.");
    }

    // 일부만 전달된 커서를 첫 페이지로 취급하면 중복 목록이 발생할 수 있다.
    if ((condition.cursor() == null) != (condition.idAfter() == null)) {
      throw new IllegalArgumentException("cursor와 idAfter는 함께 제공되거나 모두 null이어야 합니다.");
    }
  }
}


package com.project.sb13ootbsteam04.directmessage.repository;

import com.project.sb13ootbsteam04.directmessage.entity.DirectMessage;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("postgres-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
@EnabledIfEnvironmentVariable(named = "OTBOO_POSTGRES_TEST", matches = "true")
class DirectMessageRepositoryIntegrationTest {

  private static final String TEST_SCHEMA =
      "otboo_dm_test_" + UUID.randomUUID().toString().replace("-", "");

  @DynamicPropertySource
  static void configureTestSchema(DynamicPropertyRegistry registry) {
    registry.add("spring.jpa.properties.hibernate.default_schema", () -> TEST_SCHEMA);
    registry.add("spring.jpa.properties.hibernate.hbm2ddl.create_namespaces", () -> true);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
  }

  @Autowired
  private DirectMessageRepository directMessageRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EntityManager entityManager;

  @Test
  void findFirstPage_returnsMessagesBetweenTwoUsersInBothDirections() {

    // given
    User userA = userRepository.saveAndFlush(
        new User("사용자A", "dm-a-" + UUID.randomUUID() + "@test.com", "password")
    );

    User userB = userRepository.saveAndFlush(
        new User("사용자B", "dm-b-" + UUID.randomUUID() + "@test.com", "password")
    );

    User userC = userRepository.saveAndFlush(
        new User("사용자C", "dm-c-" + UUID.randomUUID() + "@test.com", "password")
    );

    DirectMessage messageAtoB = directMessageRepository.saveAndFlush(
        new DirectMessage(userA, userB, "A가 B에게 보낸 메시지")
    );

    DirectMessage messageBtoA = directMessageRepository.saveAndFlush(
        new DirectMessage(userB, userA, "B가 A에게 보낸 메시지")
    );

    directMessageRepository.saveAndFlush(
        new DirectMessage(userC, userA, "C가 A에게 보낸 메시지")
    );

    entityManager.clear();

    // when
    var result = directMessageRepository.findFirstPage(
        userA.getId(),
        userB.getId(),
        org.springframework.data.domain.PageRequest.ofSize(10)
    );

    // then
    assertThat(result)
        .extracting(DirectMessage::getId)
        .containsExactlyInAnyOrder(
            messageAtoB.getId(),
            messageBtoA.getId()
        );
  }

  //
  @Test
  void findFirstPage_returnsMessagesInLatestFirstOrder() {
    User userA = userRepository.saveAndFlush(
        new User("사용자A", "order-a-" + UUID.randomUUID() + "@test.com", "password")
    );
    User userB = userRepository.saveAndFlush(
        new User("사용자B", "order-b-" + UUID.randomUUID() + "@test.com", "password")
    );

    DirectMessage message1 = directMessageRepository.saveAndFlush(
        new DirectMessage(userA, userB, "첫 번째 메시지")
    );
    DirectMessage message2 = directMessageRepository.saveAndFlush(
        new DirectMessage(userB, userA, "두 번째 메시지")
    );
    DirectMessage message3 = directMessageRepository.saveAndFlush(
        new DirectMessage(userA, userB, "세 번째 메시지")
    );

    entityManager.clear();

    var result = directMessageRepository.findFirstPage(
        userA.getId(),
        userB.getId(),
        org.springframework.data.domain.PageRequest.ofSize(10)
    );

    var expected = java.util.stream.Stream.of(message1, message2, message3)
        .sorted(
            java.util.Comparator
                .comparing(DirectMessage::getCreatedAt)
                .thenComparing(DirectMessage::getId)
                .reversed()
        )
        .map(DirectMessage::getId)
        .toList();

    assertThat(result)
        .extracting(DirectMessage::getId)
        .containsExactlyElementsOf(expected);
  }

  @Test
  void findNextPage_returnsMessagesOlderThanCursor() {
    User userA = userRepository.saveAndFlush(
        new User("사용자A", "cursor-a-" + UUID.randomUUID() + "@test.com", "password")
    );
    User userB = userRepository.saveAndFlush(
        new User("사용자B", "cursor-b-" + UUID.randomUUID() + "@test.com", "password")
    );

    DirectMessage message1 = directMessageRepository.saveAndFlush(
        new DirectMessage(userA, userB, "첫 번째 메시지")
    );
    DirectMessage message2 = directMessageRepository.saveAndFlush(
        new DirectMessage(userB, userA, "두 번째 메시지")
    );
    DirectMessage message3 = directMessageRepository.saveAndFlush(
        new DirectMessage(userA, userB, "세 번째 메시지")
    );
    DirectMessage message4 = directMessageRepository.saveAndFlush(
        new DirectMessage(userB, userA, "네 번째 메시지")
    );

    entityManager.clear();

    var result = directMessageRepository.findNextPage(
        userA.getId(),
        userB.getId(),
        message2.getCreatedAt(),
        message2.getId(),
        org.springframework.data.domain.PageRequest.ofSize(10)
    );

    assertThat(result)
        .extracting(DirectMessage::getId)
        .containsExactly(message1.getId());
  }

  //
  @Test
  void countMessagesBetweenUsers_returnsOnlyMessagesBetweenTwoUsers() {
    User userA = userRepository.saveAndFlush(
        new User("사용자A", "count-a-" + UUID.randomUUID() + "@test.com", "password")
    );
    User userB = userRepository.saveAndFlush(
        new User("사용자B", "count-b-" + UUID.randomUUID() + "@test.com", "password")
    );
    User userC = userRepository.saveAndFlush(
        new User("사용자C", "count-c-" + UUID.randomUUID() + "@test.com", "password")
    );

    directMessageRepository.saveAndFlush(
        new DirectMessage(userA, userB, "A에서 B로")
    );
    directMessageRepository.saveAndFlush(
        new DirectMessage(userB, userA, "B에서 A로")
    );
    directMessageRepository.saveAndFlush(
        new DirectMessage(userA, userB, "A에서 B로 한 번 더")
    );
    directMessageRepository.saveAndFlush(
        new DirectMessage(userC, userA, "C에서 A로")
    );

    long result = directMessageRepository.countMessagesBetweenUsers(
        userA.getId(),
        userB.getId()
    );

    assertThat(result).isEqualTo(3);
  }
}

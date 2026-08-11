package mtvs.onvision.vision.command.repository;

import mtvs.onvision.vision.command.domain.Command;
import mtvs.onvision.vision.command.domain.CommandType;
import mtvs.onvision.vision.common.config.JpaConfig;
import mtvs.onvision.vision.support.PostgresContainerSupport;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 파생 쿼리 이름(`findAllByReceiverIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc`)이
 * 의도한 기간·정렬·필터로 동작하는지 확인한다.
 * 이름 규칙이 틀리면 부팅 시점에야 터지고, 기간 조건이 빠지면 화면이 조용히 전체를 받는다.
 */
@DataJpaTest
@Import(JpaConfig.class)   // HistoryEntity의 createdAt이 nullable = false다. 감사 설정이 필요하다
@DisplayName("CommandRepository의")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommandRepositoryTest extends PostgresContainerSupport {

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User persistUser(String email, String phoneNumber, UserRole role) {
        return entityManager.persist(new User(email, "password", "이름", phoneNumber, role));
    }

    private Command persistCommand(User receiver, String content) {
        Command command = new Command(content, CommandType.GUARDIAN_INSTRUCTION, receiver);
        return entityManager.persist(command);
    }

    /** 생성자가 `occurredAt`을 `Instant.now()`로 박으므로, 과거 지시는 필드를 직접 바꿔 심는다. */
    private Command persistCommandAt(User receiver, String content, Instant occurredAt) {
        Command command = new Command(content, CommandType.GUARDIAN_INSTRUCTION, receiver);
        ReflectionTestUtils.setField(command, "occurredAt", occurredAt);
        return entityManager.persist(command);
    }

    @Nested
    @DisplayName("Describe: findAllByReceiverIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc 메서드는")
    class Describe_with_findAllSince {

        @Nested
        @DisplayName("Context: 기준 시각 앞뒤로 지시가 섞여 있으면")
        class Context_with_commands_around_boundary {

            @Test
            @DisplayName("It : 기준 시각 이후 지시만 가져온다")
            void it_filters_by_occurred_at() {
                //given
                User ward = persistUser("ward1@test.com", "01011110001", UserRole.WARD);
                Instant from = Instant.now().truncatedTo(ChronoUnit.HOURS);

                persistCommandAt(ward, "기준 한참 전", from.minus(2, ChronoUnit.HOURS));
                persistCommandAt(ward, "기준 1초 전", from.minusSeconds(1));
                persistCommandAt(ward, "기준 정각", from);
                persistCommandAt(ward, "기준 이후", from.plusSeconds(1));
                entityManager.flush();

                //when
                List<Command> found = commandRepository
                        .findAllByReceiverIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(ward.getId(), from);

                //then : GreaterThanEqual이므로 기준 정각은 포함이다
                assertThat(found).extracting(Command::getContent)
                        .containsExactly("기준 이후", "기준 정각");
            }

            @Test
            @DisplayName("It : occurredAt 최신순으로 정렬한다")
            void it_sorts_by_occurred_at_desc() {
                //given
                User ward = persistUser("ward2@test.com", "01011110002", UserRole.WARD);
                Instant from = Instant.now().truncatedTo(ChronoUnit.HOURS);

                // 입력 순서와 시각 순서를 어긋나게 심어야 정렬이 실제로 검증된다
                persistCommandAt(ward, "두번째", from.plusSeconds(20));
                persistCommandAt(ward, "가장 최근", from.plusSeconds(30));
                persistCommandAt(ward, "가장 오래됨", from.plusSeconds(10));
                entityManager.flush();

                //when
                List<Command> found = commandRepository
                        .findAllByReceiverIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(ward.getId(), from);

                //then
                assertThat(found).extracting(Command::getContent)
                        .containsExactly("가장 최근", "두번째", "가장 오래됨");
                assertThat(found).extracting(Command::getOccurredAt)
                        .isSortedAccordingTo(Comparator.reverseOrder());
            }

            @Test
            @DisplayName("It : 건수 상한이 없어 기준 이후 전부를 가져온다")
            void it_has_no_row_limit() {
                //given : 옛 findTop5 시절이면 5건에서 잘렸다
                User ward = persistUser("ward3@test.com", "01011110003", UserRole.WARD);
                Instant from = Instant.now().truncatedTo(ChronoUnit.HOURS);
                for (int i = 1; i <= 7; i++) {
                    persistCommandAt(ward, "지시 " + i, from.plusSeconds(i));
                }
                entityManager.flush();

                //when
                List<Command> found = commandRepository
                        .findAllByReceiverIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(ward.getId(), from);

                //then
                assertThat(found).hasSize(7);
            }
        }

        @Nested
        @DisplayName("Context: 다른 피보호자의 지시가 섞여 있으면")
        class Context_with_other_receiver {

            @Test
            @DisplayName("It : 요청한 수신자의 지시만 가져온다")
            void it_filters_by_receiver() {
                //given
                User ward = persistUser("ward4@test.com", "01011110004", UserRole.WARD);
                User otherWard = persistUser("ward5@test.com", "01011110005", UserRole.WARD);
                Instant from = Instant.now().truncatedTo(ChronoUnit.HOURS);

                persistCommand(ward, "내 지시");
                persistCommand(otherWard, "남의 지시 1");
                persistCommand(otherWard, "남의 지시 2");
                entityManager.flush();

                //when
                List<Command> found = commandRepository
                        .findAllByReceiverIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(ward.getId(), from);

                //then
                assertThat(found).hasSize(1);
                assertThat(found.getFirst().getContent()).isEqualTo("내 지시");
            }
        }

        @Nested
        @DisplayName("Context: 받은 지시가 없으면")
        class Context_without_command {

            @Test
            @DisplayName("It : 빈 목록을 반환한다")
            void it_returns_empty_list() {
                //given
                User ward = persistUser("ward6@test.com", "01011110006", UserRole.WARD);
                entityManager.flush();

                //when&then
                assertThat(commandRepository.findAllByReceiverIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
                        ward.getId(), Instant.now().truncatedTo(ChronoUnit.HOURS))).isEmpty();
            }
        }
    }
}

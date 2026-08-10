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

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 파생 쿼리 이름(`findTop5By...OrderByCreatedAtDesc`)이 의도한 개수·정렬·필터로 동작하는지 확인한다.
 * 이름 규칙이 틀리면 부팅 시점에야 터지고, Top5가 빠지면 화면이 조용히 전체를 받는다.
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

    @Nested
    @DisplayName("Describe: findTop5ByReceiverIdOrderByCreatedAtDesc 메서드는")
    class Describe_with_findTop5 {

        @Nested
        @DisplayName("Context: 지시가 5건을 넘으면")
        class Context_with_more_than_five {

            @Test
            @DisplayName("It : 5건까지만 가져온다")
            void it_limits_to_five() {
                //given
                User ward = persistUser("ward1@test.com", "01011110001", UserRole.WARD);
                for (int i = 1; i <= 7; i++) {
                    persistCommand(ward, "지시 " + i);
                }
                entityManager.flush();

                //when
                List<Command> found = commandRepository.findTop5ByReceiverIdOrderByCreatedAtDesc(ward.getId());

                //then
                assertThat(found).hasSize(5);
            }

            @Test
            @DisplayName("It : 최신순으로 정렬한다")
            void it_sorts_by_created_at_desc() {
                //given
                User ward = persistUser("ward2@test.com", "01011110002", UserRole.WARD);
                for (int i = 1; i <= 7; i++) {
                    persistCommand(ward, "지시 " + i);
                }
                entityManager.flush();

                //when
                List<Command> found = commandRepository.findTop5ByReceiverIdOrderByCreatedAtDesc(ward.getId());

                //then
                List<LocalDateTime> createdAts = found.stream().map(Command::getCreatedAt).toList();
                assertThat(createdAts).isSortedAccordingTo(Comparator.reverseOrder());
            }
        }

        @Nested
        @DisplayName("Context: 다른 피보호자의 지시가 섞여 있으면")
        class Context_with_other_receiver {

            @Test
            @DisplayName("It : 요청한 수신자의 지시만 가져온다")
            void it_filters_by_receiver() {
                //given
                User ward = persistUser("ward3@test.com", "01011110003", UserRole.WARD);
                User otherWard = persistUser("ward4@test.com", "01011110004", UserRole.WARD);
                persistCommand(ward, "내 지시");
                persistCommand(otherWard, "남의 지시 1");
                persistCommand(otherWard, "남의 지시 2");
                entityManager.flush();

                //when
                List<Command> found = commandRepository.findTop5ByReceiverIdOrderByCreatedAtDesc(ward.getId());

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
                User ward = persistUser("ward5@test.com", "01011110005", UserRole.WARD);
                entityManager.flush();

                //when&then
                assertThat(commandRepository.findTop5ByReceiverIdOrderByCreatedAtDesc(ward.getId())).isEmpty();
            }
        }
    }
}

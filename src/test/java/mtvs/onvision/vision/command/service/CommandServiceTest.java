package mtvs.onvision.vision.command.service;

import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.command.domain.Command;
import mtvs.onvision.vision.command.domain.CommandType;
import mtvs.onvision.vision.command.dto.CommandResponse;
import mtvs.onvision.vision.command.dto.InstructionRequest;
import mtvs.onvision.vision.command.event.GuardianInstructed;
import mtvs.onvision.vision.command.repository.CommandRepository;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static mtvs.onvision.vision.alert.service.AlertService.SEOUL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommandService의")
class CommandServiceTest {

    @InjectMocks
    private CommandService commandService;

    @Mock
    private CommandRepository commandRepository;

    @Mock
    private UserService userService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    Long guardianId = 1L;
    Long wardId = 2L;

    CurrentUser guardian = new CurrentUser(guardianId, "guardian@test.com", UserRole.GUARDIAN);

    User wardEntity = user(wardId, "ward@test.com", UserRole.WARD);

    /** id는 영속화 시점에 채워지므로 단위 테스트에서는 직접 넣는다 */
    private User user(Long id, String email, UserRole role) {
        User user = new User(email, "password", "이름", "01000000000", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Command command(Long id, String content) {
        Command command = new Command(content, CommandType.GUARDIAN_INSTRUCTION, wardEntity);
        ReflectionTestUtils.setField(command, "id", id);
        return command;
    }

    /** 리포지토리에 실제로 넘어간 Command를 꺼낸다 */
    private Command captureSaved() {
        ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
        verify(commandRepository).save(captor.capture());
        return captor.getValue();
    }

    /** 발행된 이벤트를 꺼낸다 */
    private GuardianInstructed captureEvent() {
        ArgumentCaptor<GuardianInstructed> captor = ArgumentCaptor.forClass(GuardianInstructed.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Describe: guardianInstruct 메서드는")
    class Describe_with_guardianInstruct {

        @Nested
        @DisplayName("Context: 보호자에게 연결된 피보호자가 있으면")
        class Context_with_related_ward {

            @Test
            @DisplayName("It : 피보호자를 수신자로 하는 지시를 저장한다")
            void it_saves_command_for_ward() {
                //given
                given(userService.getWardFromGuardianId(guardianId)).willReturn(wardEntity);

                //when
                commandService.guardianInstruct(new InstructionRequest("잠시 멈추세요."), guardian);

                //then
                Command saved = captureSaved();
                assertThat(saved.getContent()).isEqualTo("잠시 멈추세요.");
                assertThat(saved.getReceiver()).isSameAs(wardEntity);
            }

            @Test
            @DisplayName("(보호자 지시)It : type을 GUARDIAN_INSTRUCTION으로 고정한다")
            void it_fixes_type_to_guardian_instruction() {
                //given
                given(userService.getWardFromGuardianId(guardianId)).willReturn(wardEntity);

                //when
                commandService.guardianInstruct(new InstructionRequest("횡단보도 입니다."), guardian);

                //then
                assertThat(captureSaved().getType()).isEqualTo(CommandType.GUARDIAN_INSTRUCTION);
            }

            @Test
            @DisplayName("It : 발생 시각을 엔티티가 채운다")
            void it_fills_occurred_at() {
                //given
                given(userService.getWardFromGuardianId(guardianId)).willReturn(wardEntity);
                Instant before = Instant.now();

                //when
                commandService.guardianInstruct(new InstructionRequest("조심하세요."), guardian);

                //then
                assertThat(captureSaved().getOccurredAt()).isBetween(before, Instant.now());
            }

            @Test
            @DisplayName("It : GuardianInstructed 이벤트를 발행한다")
            void it_publishes_event() {
                //given
                given(userService.getWardFromGuardianId(guardianId)).willReturn(wardEntity);

                //when
                commandService.guardianInstruct(new InstructionRequest("왼쪽으로 이동하세요."), guardian);

                //then
                GuardianInstructed event = captureEvent();
                assertThat(event.content()).isEqualTo("왼쪽으로 이동하세요.");
                assertThat(event.receiverId()).isEqualTo(wardId);
            }

            @Test
            @DisplayName("It : 이벤트의 발생 시각이 저장된 값과 같다")
            void it_publishes_same_occurred_at_as_saved() {
                //given : 리스너가 Instant.now()를 다시 만들면 앱이 판단할 기준이 어긋난다
                given(userService.getWardFromGuardianId(guardianId)).willReturn(wardEntity);

                //when
                commandService.guardianInstruct(new InstructionRequest("오른쪽으로 이동하세요."), guardian);

                //then
                assertThat(captureEvent().occurredAt()).isEqualTo(captureSaved().getOccurredAt());
            }

            @Test
            @DisplayName("(id가 필요하다)It : 저장한 뒤에 이벤트를 발행한다")
            void it_saves_before_publishing() {
                //given : 이벤트가 commandId를 싣기 때문에 순서가 뒤집히면 null이 나간다
                given(userService.getWardFromGuardianId(guardianId)).willReturn(wardEntity);

                //when
                commandService.guardianInstruct(new InstructionRequest("잠시 멈추세요."), guardian);

                //then
                InOrder inOrder = inOrder(commandRepository, eventPublisher);
                inOrder.verify(commandRepository).save(any(Command.class));
                inOrder.verify(eventPublisher).publishEvent(any(GuardianInstructed.class));
            }
        }

        @Nested
        @DisplayName("Context: 연결된 피보호자가 없으면")
        class Context_without_relation {

            @Test
            @DisplayName("It : NOT_FOUND_RELATION 오류 발생")
            void it_throws_not_found_relation() {
                //given
                willThrow(new BusinessException(ErrorCode.NOT_FOUND_RELATION))
                        .given(userService).getWardFromGuardianId(guardianId);

                //when&then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> commandService.guardianInstruct(new InstructionRequest("잠시 멈추세요."), guardian));
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_RELATION);
            }

            @Test
            @DisplayName("It : 저장도 발행도 하지 않는다")
            void it_does_not_save_nor_publish() {
                //given
                willThrow(new BusinessException(ErrorCode.NOT_FOUND_RELATION))
                        .given(userService).getWardFromGuardianId(guardianId);

                //when
                assertThrows(BusinessException.class,
                        () -> commandService.guardianInstruct(new InstructionRequest("잠시 멈추세요."), guardian));

                //then
                verify(commandRepository, never()).save(any());
                verifyNoInteractions(eventPublisher);
            }
        }
    }

    @Nested
    @DisplayName("Describe: getInstructs 메서드는")
    class Describe_with_getInstructs {

        @Nested
        @DisplayName("Context: 보낸 지시가 있으면")
        class Context_with_commands {

            @Test
            @DisplayName("It : 본인이 아니라 피보호자가 받은 지시를 조회한다")
            void it_reads_ward_commands() {
                //given
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(commandRepository.findAllByReceiverIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(eq(wardId), any(Instant.class)))
                        .willReturn(List.of());

                //when
                commandService.getInstructs(guardian);

                //then
                verify(commandRepository).findAllByReceiverIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(eq(wardId), any(Instant.class));
            }

            @Test
            @DisplayName("It : 조회 기준 시각을 오늘 KST 00:00으로 잡는다")
            void it_uses_today_window_in_kst() {
                //given
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(commandRepository.findAllByReceiverIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(eq(wardId), any(Instant.class)))
                        .willReturn(List.of());

                //when
                commandService.getInstructs(guardian);

                //then : 서버 기본 시간대가 아니라 KST 기준이어야 한다. UTC로 잡으면 하루 경계가 9시간 어긋난다
                ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
                verify(commandRepository)
                        .findAllByReceiverIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(eq(wardId), captor.capture());

                Instant expected = LocalDate.now(SEOUL).atStartOfDay(SEOUL).toInstant();
                assertThat(captor.getValue()).isEqualTo(expected);
            }

            @Test
            @DisplayName("It : 조회 순서를 유지한 채 CommandResponse로 변환한다")
            void it_maps_to_response_keeping_order() {
                //given
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(commandRepository.findAllByReceiverIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(eq(wardId), any(Instant.class)))
                        .willReturn(List.of(command(3L, "지금 어디가니?"), command(2L, "횡단보도 입니다.")));

                //when
                List<CommandResponse> response = commandService.getInstructs(guardian);

                //then
                assertThat(response).hasSize(2);
                assertThat(response.getFirst().id()).isEqualTo(3L);
                assertThat(response.getFirst().content()).isEqualTo("지금 어디가니?");
                assertThat(response.getFirst().occurredAt()).isNotNull();
                assertThat(response.get(1).id()).isEqualTo(2L);
            }
        }

        @Nested
        @DisplayName("Context: 보낸 지시가 없으면")
        class Context_without_commands {

            @Test
            @DisplayName("It : 빈 목록을 반환한다")
            void it_returns_empty_list() {
                //given
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(commandRepository.findAllByReceiverIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(eq(wardId), any(Instant.class)))
                        .willReturn(List.of());

                //when&then
                assertThat(commandService.getInstructs(guardian)).isEmpty();
            }
        }
    }
}

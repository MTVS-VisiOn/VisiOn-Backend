package mtvs.onvision.vision.command.service;

import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.command.domain.Instruction;
import mtvs.onvision.vision.command.dto.InstructionRequest;
import mtvs.onvision.vision.command.dto.InstructionResponse;
import mtvs.onvision.vision.command.repository.InstructionRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("InstructionService의")
class InstructionServiceTest {

    @InjectMocks
    private InstructionService instructionService;

    @Mock
    private InstructionRepository instructionRepository;

    @Mock
    private UserService userService;

    Long guardianId = 1L;
    Long otherGuardianId = 9L;
    Long instructionId = 10L;

    CurrentUser guardian = new CurrentUser(guardianId, "guardian@test.com", UserRole.GUARDIAN);

    User guardianEntity = user(guardianId, "guardian@test.com");
    User otherGuardianEntity = user(otherGuardianId, "other@test.com");

    /** id는 영속화 시점에 채워지므로 단위 테스트에서는 직접 넣는다 */
    private User user(Long id, String email) {
        User user = new User(email, "password", "보호자", "0100000000" + id, UserRole.GUARDIAN);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Instruction instruction(String content, User owner) {
        Instruction instruction = new Instruction(content, owner);
        ReflectionTestUtils.setField(instruction, "id", instructionId);
        return instruction;
    }

    /** 리포지토리에 실제로 넘어간 Instruction을 꺼낸다 */
    private Instruction captureSaved() {
        ArgumentCaptor<Instruction> captor = ArgumentCaptor.forClass(Instruction.class);
        verify(instructionRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Describe: saveInstruction 메서드는")
    class Describe_with_saveInstruction {

        @Nested
        @DisplayName("Context: 보호자가 문구를 등록하면")
        class Context_with_valid_request {

            @Test
            @DisplayName("It : 요청한 보호자를 주인으로 저장한다")
            void it_saves_with_owner() {
                //given
                given(userService.currentUserToUser(guardianId)).willReturn(guardianEntity);

                //when
                instructionService.saveInstruction(new InstructionRequest("잠시 멈추세요."), guardian);

                //then
                Instruction saved = captureSaved();
                assertThat(saved.getInstruction()).isEqualTo("잠시 멈추세요.");
                assertThat(saved.getGuardian()).isSameAs(guardianEntity);
            }
        }
    }

    @Nested
    @DisplayName("Describe: getInstructions 메서드는")
    class Describe_with_getInstructions {

        @Nested
        @DisplayName("Context: 등록한 문구가 있으면")
        class Context_with_instructions {

            @Test
            @DisplayName("It : 본인 것만 조회해 InstructionResponse로 변환한다")
            void it_maps_own_instructions() {
                //given
                given(instructionRepository.findAllByGuardianId(guardianId))
                        .willReturn(List.of(
                                instruction("잠시 멈추세요.", guardianEntity),
                                instruction("횡단보도 입니다.", guardianEntity)));

                //when
                List<InstructionResponse> response = instructionService.getInstructions(guardian);

                //then
                assertThat(response).hasSize(2);
                assertThat(response.getFirst().content()).isEqualTo("잠시 멈추세요.");
                assertThat(response.get(1).content()).isEqualTo("횡단보도 입니다.");
            }
        }

        @Nested
        @DisplayName("Context: 등록한 문구가 없으면")
        class Context_without_instructions {

            @Test
            @DisplayName("It : 빈 목록을 반환한다")
            void it_returns_empty_list() {
                //given
                given(instructionRepository.findAllByGuardianId(guardianId)).willReturn(List.of());

                //when&then
                assertThat(instructionService.getInstructions(guardian)).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Describe: updateInstruction 메서드는")
    class Describe_with_updateInstruction {

        @Nested
        @DisplayName("Context: 본인이 등록한 문구면")
        class Context_with_own_instruction {

            @Test
            @DisplayName("(더티 체킹)It : save 없이 내용만 바꾼다")
            void it_updates_content_without_save() {
                //given
                Instruction instruction = instruction("잠시 멈추세요.", guardianEntity);
                given(instructionRepository.findById(instructionId)).willReturn(Optional.of(instruction));

                //when
                instructionService.updateInstruction(instructionId, new InstructionRequest("천천히 가세요."), guardian);

                //then
                assertThat(instruction.getInstruction()).isEqualTo("천천히 가세요.");
                verify(instructionRepository, never()).save(any());
            }
        }

        @Nested
        @DisplayName("Context: 없는 id면")
        class Context_with_unknown_id {

            @Test
            @DisplayName("It : NOT_FOUND_INSTRUCTION 오류 발생")
            void it_throws_not_found_instruction() {
                //given
                given(instructionRepository.findById(instructionId)).willReturn(Optional.empty());

                //when&then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> instructionService.updateInstruction(
                                instructionId, new InstructionRequest("천천히 가세요."), guardian));
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_INSTRUCTION);
            }
        }

        @Nested
        @DisplayName("Context: 다른 보호자가 등록한 문구면")
        class Context_with_others_instruction {

            @Test
            @DisplayName("It : NOT_OWNER 오류 발생")
            void it_throws_not_owner() {
                //given
                given(instructionRepository.findById(instructionId))
                        .willReturn(Optional.of(instruction("잠시 멈추세요.", otherGuardianEntity)));

                //when&then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> instructionService.updateInstruction(
                                instructionId, new InstructionRequest("천천히 가세요."), guardian));
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_OWNER);
            }

            @Test
            @DisplayName("It : 내용을 바꾸지 않는다")
            void it_keeps_content() {
                //given
                Instruction instruction = instruction("잠시 멈추세요.", otherGuardianEntity);
                given(instructionRepository.findById(instructionId)).willReturn(Optional.of(instruction));

                //when
                assertThrows(BusinessException.class,
                        () -> instructionService.updateInstruction(
                                instructionId, new InstructionRequest("천천히 가세요."), guardian));

                //then
                assertThat(instruction.getInstruction()).isEqualTo("잠시 멈추세요.");
            }
        }
    }

    @Nested
    @DisplayName("Describe: deleteInstruction 메서드는")
    class Describe_with_deleteInstruction {

        @Nested
        @DisplayName("Context: 본인이 등록한 문구면")
        class Context_with_own_instruction {

            @Test
            @DisplayName("(소프트 삭제가 아니다)It : 행을 실제로 지운다")
            void it_hard_deletes() {
                //given
                Instruction instruction = instruction("잠시 멈추세요.", guardianEntity);
                given(instructionRepository.findById(instructionId)).willReturn(Optional.of(instruction));

                //when
                instructionService.deleteInstruction(instructionId, guardian);

                //then
                verify(instructionRepository).delete(instruction);
            }
        }

        @Nested
        @DisplayName("Context: 없는 id면")
        class Context_with_unknown_id {

            @Test
            @DisplayName("It : NOT_FOUND_INSTRUCTION 오류 발생")
            void it_throws_not_found_instruction() {
                //given
                given(instructionRepository.findById(instructionId)).willReturn(Optional.empty());

                //when&then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> instructionService.deleteInstruction(instructionId, guardian));
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_INSTRUCTION);
            }
        }

        @Nested
        @DisplayName("Context: 다른 보호자가 등록한 문구면")
        class Context_with_others_instruction {

            @Test
            @DisplayName("It : NOT_OWNER 오류가 나고 삭제하지 않는다")
            void it_throws_not_owner_and_does_not_delete() {
                //given
                given(instructionRepository.findById(instructionId))
                        .willReturn(Optional.of(instruction("잠시 멈추세요.", otherGuardianEntity)));

                //when&then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> instructionService.deleteInstruction(instructionId, guardian));
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_OWNER);
                verify(instructionRepository, never()).delete(any());
            }
        }
    }
}

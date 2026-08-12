package mtvs.onvision.vision.common.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import mtvs.onvision.vision.alert.domain.AlertType;
import mtvs.onvision.vision.alert.domain.NotifyStatus;
import mtvs.onvision.vision.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FcmService의")
class FcmServiceTest {

    @InjectMocks
    private FcmService fcmService;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private UserService userService;

    Long alertId = 10L;
    int maxAttempts = 3;
    Instant occurredAt = Instant.parse("2026-08-06T06:12:00Z");   // KST 오후 3:12

    /** @Value 필드는 단위 테스트에서 주입되지 않는다. yml과 같은 값을 넣는다 */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fcmService, "maxAttempts", maxAttempts);
        ReflectionTestUtils.setField(fcmService, "pushCommandTtl", Duration.ofHours(24));
        ReflectionTestUtils.setField(fcmService, "signalPushTtl", Duration.ofSeconds(60));
    }

    /** 생성자가 막혀 있어 직접 만들 수 없다. Mockito가 생성자를 우회해 만든다 */
    private FirebaseMessagingException exceptionWith(MessagingErrorCode code) {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        given(exception.getMessagingErrorCode()).willReturn(code);
        return exception;
    }

    private Map<String, NotifyStatus> send(List<String> fids) {
        return fcmService.sendNotification(alertId, AlertType.OBSTACLE, occurredAt, fids);
    }

    @Nested
    @DisplayName("Describe: sendNotification 메서드는")
    class Describe_with_sendNotification {

        @Nested
        @DisplayName("Context: 기기가 여러 대 등록돼 있으면")
        class Context_with_multiple_fids {

            @Test
            @DisplayName("It : 기기 수만큼 전송하고 기기별 결과를 돌려준다")
            void it_sends_to_each_device() throws Exception {
                //when
                Map<String, NotifyStatus> results = send(List.of("fid-phone", "fid-tablet"));

                //then
                verify(firebaseMessaging, times(2)).send(any(Message.class));
                assertThat(results)
                        .containsEntry("fid-phone", NotifyStatus.SENT)
                        .containsEntry("fid-tablet", NotifyStatus.SENT);
            }
        }

        @Nested
        @DisplayName("Context: 등록된 기기가 없으면")
        class Context_without_fids {

            @Test
            @DisplayName("It : 전송을 시도하지 않고 빈 결과를 돌려준다")
            void it_does_not_send() {
                //when
                Map<String, NotifyStatus> results = send(List.of());

                //then
                verifyNoInteractions(firebaseMessaging);
                assertThat(results).isEmpty();
            }
        }

        @Nested
        @DisplayName("Context: 한 기기가 실패해도")
        class Context_with_partial_failure {

            @Test
            @DisplayName("It : 나머지 기기 전송을 계속한다")
            void it_continues_sending() throws Exception {
                //given - UNREGISTERED는 재시도하지 않으므로 기기당 1회씩만 호출된다
                FirebaseMessagingException unregistered = exceptionWith(MessagingErrorCode.UNREGISTERED);
                given(firebaseMessaging.send(any(Message.class)))
                        .willThrow(unregistered)
                        .willReturn("projects/onvision/messages/1");

                //when
                Map<String, NotifyStatus> results = send(List.of("dead-fid", "alive-fid"));

                //then
                verify(firebaseMessaging, times(2)).send(any(Message.class));
                assertThat(results)
                        .containsEntry("dead-fid", NotifyStatus.UNREGISTERED)
                        .containsEntry("alive-fid", NotifyStatus.SENT);
            }
        }
    }

    @Nested
    @DisplayName("Describe: titleOf 메서드는")
    class Describe_with_titleOf {

        @Nested
        @DisplayName("Context: 발생 시각과 타입이 주어지면")
        class Context_with_occurred_at {

            @Test
            @DisplayName("It : KST 시각과 타입 라벨을 붙여 만든다")
            void it_formats_in_kst() {
                //given - UTC 06:12는 KST 15:12다
                //when
                String title = fcmService.titleOf(AlertType.OBSTACLE, occurredAt);

                //then - UTC로 새면 "오전 6:12"가 된다
                assertThat(title).isEqualTo("오후 3:12 · 장애물 감지");
            }

            @Test
            @DisplayName("It : 타입마다 다른 라벨을 쓴다")
            void it_uses_type_label() {
                //when-then
                assertThat(fcmService.titleOf(AlertType.DISCONNECTED, occurredAt))
                        .isEqualTo("오후 3:12 · 연결 끊김");
                assertThat(fcmService.titleOf(AlertType.LOW_BATTERY, occurredAt))
                        .isEqualTo("오후 3:12 · 배터리 부족");
            }
        }
    }

    @Nested
    @DisplayName("Describe: sendToDevice 메서드는")
    class Describe_with_sendToDevice {

        private NotifyStatus sendTo(String fid) {
            return fcmService.sendToDevice(alertId, AlertType.OBSTACLE, occurredAt, fid);
        }

        @Nested
        @DisplayName("Context: 전송에 성공하면")
        class Context_with_success {

            @Test
            @DisplayName("It : 재시도 없이 SENT를 돌려준다")
            void it_returns_sent() throws Exception {
                //given
                given(firebaseMessaging.send(any(Message.class))).willReturn("projects/onvision/messages/1");

                //when
                NotifyStatus status = sendTo("fid-phone");

                //then
                assertThat(status).isEqualTo(NotifyStatus.SENT);
                verify(firebaseMessaging, times(1)).send(any(Message.class));
            }
        }

        @Nested
        @DisplayName("Context: 전송이 UNREGISTERED로 실패하면")
        class Context_with_unregistered {

            @Test
            @DisplayName("It : 재시도하지 않고 해당 fid를 지운 뒤 UNREGISTERED를 돌려준다")
            void it_deletes_dead_fid() throws Exception {
                //given - 예외를 먼저 만든다. willThrow 안에서 만들면 스터빙이 중첩돼 실패한다
                FirebaseMessagingException unregistered = exceptionWith(MessagingErrorCode.UNREGISTERED);
                given(firebaseMessaging.send(any(Message.class))).willThrow(unregistered);

                //when
                NotifyStatus status = sendTo("dead-fid");

                //then
                assertThat(status).isEqualTo(NotifyStatus.UNREGISTERED);
                verify(userService).deleteFid("dead-fid");
                verify(firebaseMessaging, times(1)).send(any(Message.class));
            }
        }

        @Nested
        @DisplayName("Context: 되돌릴 수 없는 사유로 실패하면")
        class Context_with_permanent_error {

            @Test
            @DisplayName("It : 재시도하지 않고 EXPIRED를 돌려준다")
            void it_gives_up_immediately() throws Exception {
                //given
                FirebaseMessagingException invalid = exceptionWith(MessagingErrorCode.INVALID_ARGUMENT);
                given(firebaseMessaging.send(any(Message.class))).willThrow(invalid);

                //when
                NotifyStatus status = sendTo("bad-fid");

                //then
                assertThat(status).isEqualTo(NotifyStatus.EXPIRED);
                verify(firebaseMessaging, times(1)).send(any(Message.class));
                verify(userService, never()).deleteFid(anyString());
            }
        }

        @Nested
        @DisplayName("Context: 일시 장애가 계속되면")
        class Context_with_retriable_error {

            @Test
            @DisplayName("It : maxAttempts만큼 재시도한 뒤 FAILED를 돌려준다")
            void it_retries_then_fails() throws Exception {
                //given
                FirebaseMessagingException unavailable = exceptionWith(MessagingErrorCode.UNAVAILABLE);
                given(firebaseMessaging.send(any(Message.class))).willThrow(unavailable);

                //when
                NotifyStatus status = sendTo("alive-fid");

                //then - 스케줄러가 다시 볼 수 있도록 FAILED다. fid는 살아 있으므로 지우지 않는다
                assertThat(status).isEqualTo(NotifyStatus.FAILED);
                verify(firebaseMessaging, times(maxAttempts)).send(any(Message.class));
                verify(userService, never()).deleteFid(anyString());
            }
        }

        @Nested
        @DisplayName("Context: 일시 장애 뒤 재시도가 성공하면")
        class Context_with_recovering_error {

            @Test
            @DisplayName("It : SENT를 돌려준다")
            void it_recovers() throws Exception {
                //given
                FirebaseMessagingException unavailable = exceptionWith(MessagingErrorCode.UNAVAILABLE);
                given(firebaseMessaging.send(any(Message.class)))
                        .willThrow(unavailable)
                        .willReturn("projects/onvision/messages/1");

                //when
                NotifyStatus status = sendTo("alive-fid");

                //then
                assertThat(status).isEqualTo(NotifyStatus.SENT);
                verify(firebaseMessaging, times(2)).send(any(Message.class));
            }
        }
    }
}

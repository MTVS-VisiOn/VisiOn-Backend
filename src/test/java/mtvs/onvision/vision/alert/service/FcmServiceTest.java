package mtvs.onvision.vision.alert.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import mtvs.onvision.vision.alert.domain.AlertType;
import mtvs.onvision.vision.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
    String title = "알림";
    String body = "피보호자에게 장애물이 다가왔어요.";

    /** 생성자가 막혀 있어 직접 만들 수 없다. Mockito가 생성자를 우회해 만든다 */
    private FirebaseMessagingException exceptionWith(MessagingErrorCode code) {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        given(exception.getMessagingErrorCode()).willReturn(code);
        return exception;
    }

    private void send(List<String> fids) {
        fcmService.sendNotification(alertId, AlertType.OBSTACLE, title, body, fids);
    }

    @Nested
    @DisplayName("Describe: sendNotification 메서드는")
    class Describe_with_sendNotification {

        @Nested
        @DisplayName("Context: 기기가 여러 대 등록돼 있으면")
        class Context_with_multiple_fids {

            @Test
            @DisplayName("It : 기기 수만큼 전송한다")
            void it_sends_to_each_device() throws Exception {
                //when
                send(List.of("fid-phone", "fid-tablet"));

                //then
                verify(firebaseMessaging, times(2)).send(any(Message.class));
            }
        }

        @Nested
        @DisplayName("Context: 등록된 기기가 없으면")
        class Context_without_fids {

            @Test
            @DisplayName("It : 전송을 시도하지 않는다")
            void it_does_not_send() {
                //when
                send(List.of());

                //then
                verifyNoInteractions(firebaseMessaging);
            }
        }

        @Nested
        @DisplayName("Context: 전송이 UNREGISTERED로 실패하면")
        class Context_with_unregistered {

            @Test
            @DisplayName("It : 해당 fid를 삭제한다")
            void it_deletes_dead_fid() throws Exception {
                //given - 예외를 먼저 만든다. willThrow 안에서 만들면 스터빙이 중첩돼 실패한다
                FirebaseMessagingException unregistered = exceptionWith(MessagingErrorCode.UNREGISTERED);
                given(firebaseMessaging.send(any(Message.class))).willThrow(unregistered);

                //when
                send(List.of("dead-fid"));

                //then
                verify(userService).deleteFid("dead-fid");
            }
        }

        @Nested
        @DisplayName("Context: 전송이 그 외 사유로 실패하면")
        class Context_with_other_error {

            @Test
            @DisplayName("It : fid를 삭제하지 않는다")
            void it_keeps_fid() throws Exception {
                //given
                FirebaseMessagingException unavailable = exceptionWith(MessagingErrorCode.UNAVAILABLE);
                given(firebaseMessaging.send(any(Message.class))).willThrow(unavailable);

                //when
                send(List.of("alive-fid"));

                //then
                verify(userService, never()).deleteFid(anyString());
            }
        }

        @Nested
        @DisplayName("Context: 한 기기가 실패해도")
        class Context_with_partial_failure {

            @Test
            @DisplayName("It : 나머지 기기 전송을 계속한다")
            void it_continues_sending() throws Exception {
                //given
                FirebaseMessagingException unregistered = exceptionWith(MessagingErrorCode.UNREGISTERED);
                given(firebaseMessaging.send(any(Message.class)))
                        .willThrow(unregistered)
                        .willReturn("projects/onvision/messages/1");

                //when
                send(List.of("dead-fid", "alive-fid"));

                //then
                verify(firebaseMessaging, times(2)).send(any(Message.class));
                verify(userService).deleteFid("dead-fid");
                verify(userService, never()).deleteFid("alive-fid");
            }
        }
    }
}

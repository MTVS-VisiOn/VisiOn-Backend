package mtvs.onvision.vision.alert.service;

import mtvs.onvision.vision.alert.domain.Alert;
import mtvs.onvision.vision.alert.domain.AlertType;
import mtvs.onvision.vision.alert.dto.ObstacleRequest;
import mtvs.onvision.vision.alert.event.ObstacleDetected;
import mtvs.onvision.vision.alert.repository.AlertRepository;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.image.service.ImageService;
import mtvs.onvision.vision.location.service.LocationService;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertService의")
class AlertServiceTest {

    @InjectMocks
    private AlertService alertService;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UserService userService;

    @Mock
    private LocationService locationService;

    @Mock
    private ImageService imageService;

    Long wardId = 2L;
    Long alertId = 10L;
    String address = "서울특별시 강남구 테헤란로 152";
    String s3Key = "alerts/OBSTACLE/2026/08/05/uuid/obstacle.jpg";
    Instant occurredAt = Instant.parse("2026-08-05T09:12:33.512Z");

    CurrentUser ward = new CurrentUser(wardId, "ward@test.com", UserRole.WARD);
    User wardEntity = new User("ward@test.com", "password", "피보호자", "01012345678", UserRole.WARD);

    MultipartFile image = new MockMultipartFile("image", "obstacle.jpg", "image/jpeg", "dummy".getBytes());

    ObstacleRequest request = new ObstacleRequest(
            occurredAt, 37.4979, 127.0276, "전방 2m에 자전거가 세워져 있습니다", "위험 음성 재생");

    /**
     * detectObstacle이 registerSynchronization을 호출하므로 동기화가 활성화돼 있어야 한다.
     * 활성화하지 않으면 IllegalStateException이 난다.
     */
    @BeforeEach
    void initSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    /** JPA IDENTITY 전략이 save 시점에 id를 채우는 것을 흉내낸다 */
    private void givenSaveAssignsId() {
        given(alertRepository.save(any(Alert.class))).willAnswer(invocation -> {
            Alert saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", alertId);
            return saved;
        });
    }

    private void givenExternalCallsSucceed() {
        given(userService.currentUserToUser(wardId)).willReturn(wardEntity);
        given(locationService.getRoadAddress(request.latitude(), request.longitude())).willReturn(address);
        given(imageService.saveImage(image, AlertType.OBSTACLE)).willReturn(s3Key);
    }

    @Nested
    @DisplayName("Describe: detectObstacle 메서드는")
    class Describe_with_detectObstacle {

        @Nested
        @DisplayName("Context: 정상적인 요청이 주어지면")
        class Context_with_available_data {

            @Test
            @DisplayName("It : 주소와 s3Key를 채워 Alert를 저장한다")
            void it_saves_alert() {
                //given
                givenExternalCallsSucceed();
                givenSaveAssignsId();

                //when
                alertService.detectObstacle(request, image, ward);

                //then
                ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
                verify(alertRepository).save(captor.capture());

                Alert saved = captor.getValue();
                assertThat(saved.getType()).isEqualTo(AlertType.OBSTACLE);
                assertThat(saved.getContent()).isEqualTo(request.message());
                assertThat(saved.getLatitude()).isEqualTo(request.latitude());
                assertThat(saved.getLongitude()).isEqualTo(request.longitude());
                assertThat(saved.getAddress()).isEqualTo(address);
                assertThat(saved.getS3Key()).isEqualTo(s3Key);
                assertThat(saved.getOccurredAt()).isEqualTo(occurredAt);
                assertThat(saved.getSender()).isEqualTo(wardEntity);
            }

            @Test
            @DisplayName("It : 저장된 alertId와 wardId를 담아 ObstacleDetected를 발행한다")
            void it_publishes_event() {
                //given
                givenExternalCallsSucceed();
                givenSaveAssignsId();

                //when
                alertService.detectObstacle(request, image, ward);

                //then
                ArgumentCaptor<ObstacleDetected> captor = ArgumentCaptor.forClass(ObstacleDetected.class);
                verify(eventPublisher).publishEvent(captor.capture());

                assertThat(captor.getValue().alertId()).isEqualTo(alertId);
                assertThat(captor.getValue().wardId()).isEqualTo(wardId);
            }
        }

        @Nested
        @DisplayName("Context: 트랜잭션이 롤백되면")
        class Context_with_rollback {

            @Test
            @DisplayName("It : 업로드한 S3 이미지를 보상 삭제한다")
            void it_deletes_uploaded_image() {
                //given
                givenExternalCallsSucceed();
                givenSaveAssignsId();
                alertService.detectObstacle(request, image, ward);

                //when - 트랜잭션 매니저가 롤백으로 완료 콜백을 호출한 상황
                triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

                //then
                verify(imageService).deleteImage(s3Key);
            }
        }

        @Nested
        @DisplayName("Context: 트랜잭션이 정상 커밋되면")
        class Context_with_commit {

            @Test
            @DisplayName("It : S3 이미지를 지우지 않는다")
            void it_keeps_uploaded_image() {
                //given
                givenExternalCallsSucceed();
                givenSaveAssignsId();
                alertService.detectObstacle(request, image, ward);

                //when
                triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);

                //then
                verify(imageService, never()).deleteImage(any());
            }
        }
    }

    private void triggerAfterCompletion(int status) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(status));
    }
}

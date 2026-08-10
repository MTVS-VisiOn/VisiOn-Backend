package mtvs.onvision.vision.alert.service;

import mtvs.onvision.vision.alert.domain.Alert;
import mtvs.onvision.vision.alert.domain.AlertType;
import mtvs.onvision.vision.alert.dto.AlertResponse;
import mtvs.onvision.vision.alert.dto.ObstacleRequest;
import mtvs.onvision.vision.alert.event.ObstacleDetected;
import mtvs.onvision.vision.alert.repository.AlertNotificationRepository;
import mtvs.onvision.vision.alert.repository.AlertRepository;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertService의")
class AlertServiceTest {

    @InjectMocks
    private AlertService alertService;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private AlertNotificationRepository alertNotificationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UserService userService;

    @Mock
    private LocationService locationService;

    @Mock
    private ImageService imageService;

    Long wardId = 2L;
    Long guardianId = 1L;
    Long alertId = 10L;
    String address = "서울특별시 강남구 테헤란로 152";
    String s3Key = "alerts/OBSTACLE/2026/08/05/uuid/obstacle.jpg";
    Instant occurredAt = Instant.parse("2026-08-05T09:12:33.512Z");

    CurrentUser ward = new CurrentUser(wardId, "ward@test.com", UserRole.WARD);
    CurrentUser guardian = new CurrentUser(guardianId, "guardian@test.com", UserRole.GUARDIAN);
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
        given(alertNotificationRepository.tryStartCooldown(wardId, AlertType.OBSTACLE)).willReturn(true);
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
            @DisplayName("It : 저장된 alertId·wardId·occurredAt을 담아 ObstacleDetected를 발행한다")
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
                // 푸시 문구의 시각이 여기서 온다. 빠지면 리스너가 알 방법이 없다
                assertThat(captor.getValue().occurredAt()).isEqualTo(occurredAt);
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

            @Test
            @DisplayName("It : 쿨다운도 되돌린다. 저장이 안 됐는데 다음 감지가 막히면 안 된다")
            void it_clears_cooldown() {
                //given
                givenExternalCallsSucceed();
                givenSaveAssignsId();
                alertService.detectObstacle(request, image, ward);

                //when
                triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

                //then
                verify(alertNotificationRepository).clearCooldown(wardId, AlertType.OBSTACLE);
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

            @Test
            @DisplayName("It : 쿨다운을 유지한다")
            void it_keeps_cooldown() {
                //given
                givenExternalCallsSucceed();
                givenSaveAssignsId();
                alertService.detectObstacle(request, image, ward);

                //when
                triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);

                //then
                verify(alertNotificationRepository, never()).clearCooldown(any(), any());
            }
        }

        @Nested
        @DisplayName("Context: 쿨다운 중이면")
        class Context_with_active_cooldown {

            @Test
            @DisplayName("It : 이미지 업로드도 저장도 이벤트 발행도 하지 않는다")
            void it_skips_everything() {
                //given
                given(alertNotificationRepository.tryStartCooldown(wardId, AlertType.OBSTACLE)).willReturn(false);

                //when
                alertService.detectObstacle(request, image, ward);

                //then - S3 업로드 전에 막혀야 업로드 비용이 안 든다
                verifyNoInteractions(imageService, locationService, eventPublisher);
                verify(alertRepository, never()).save(any());
            }
        }
    }

    @Nested
    @DisplayName("Describe: detectBatteryLow 메서드는")
    class Describe_with_detectBatteryLow {

        int battery = 18;

        private void givenCooldownAvailable() {
            given(alertNotificationRepository.tryStartCooldown(wardId, AlertType.LOW_BATTERY)).willReturn(true);
            given(userService.currentUserToUser(wardId)).willReturn(wardEntity);
        }

        private Alert captureSaved() {
            ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
            verify(alertRepository).save(captor.capture());
            return captor.getValue();
        }

        @Nested
        @DisplayName("Context: 쿨다운 중이 아니면")
        class Context_without_cooldown {

            @Test
            @DisplayName("It : 배터리 잔량을 문구로 만들어 Alert를 저장하고 id를 돌려준다")
            void it_saves_alert() {
                //given
                givenCooldownAvailable();
                givenSaveAssignsId();

                //when
                Optional<Long> saved = alertService.detectBatteryLow(battery, occurredAt, wardId);

                //then
                assertThat(saved).contains(alertId);

                Alert alert = captureSaved();
                assertThat(alert.getType()).isEqualTo(AlertType.LOW_BATTERY);
                assertThat(alert.getContent()).isEqualTo("배터리 잔량이 18%남았습니다.");
                assertThat(alert.getOccurredAt()).isEqualTo(occurredAt);
                assertThat(alert.getSender()).isEqualTo(wardEntity);
            }

            @Test
            @DisplayName("It : 이미지·좌표·주소·조치는 비워 둔다")
            void it_leaves_obstacle_only_fields_null() {
                //given - 이 컬럼들이 NOT NULL이면 여기서 저장이 터진다. nullable 완화가 필요했던 이유다
                givenCooldownAvailable();
                givenSaveAssignsId();

                //when
                alertService.detectBatteryLow(battery, occurredAt, wardId);

                //then
                Alert alert = captureSaved();
                assertThat(alert.getS3Key()).isNull();
                assertThat(alert.getLatitude()).isNull();
                assertThat(alert.getLongitude()).isNull();
                assertThat(alert.getAddress()).isNull();
                assertThat(alert.getAction()).isNull();
            }
        }

        @Nested
        @DisplayName("Context: 쿨다운 중이면")
        class Context_with_active_cooldown {

            @Test
            @DisplayName("It : 빈 값을 돌려주고 저장하지 않는다")
            void it_skips_everything() {
                //given
                given(alertNotificationRepository.tryStartCooldown(wardId, AlertType.LOW_BATTERY)).willReturn(false);

                //when
                Optional<Long> saved = alertService.detectBatteryLow(battery, occurredAt, wardId);

                //then
                assertThat(saved).isEmpty();
                verifyNoInteractions(userService);
                verify(alertRepository, never()).save(any());
            }
        }

        @Nested
        @DisplayName("Context: 트랜잭션이 롤백되면")
        class Context_with_rollback {

            @Test
            @DisplayName("It : 쿨다운을 되돌린다. 저장이 안 됐는데 다음 감지가 막히면 안 된다")
            void it_clears_cooldown() {
                //given
                givenCooldownAvailable();
                givenSaveAssignsId();
                alertService.detectBatteryLow(battery, occurredAt, wardId);

                //when
                triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

                //then
                verify(alertNotificationRepository).clearCooldown(wardId, AlertType.LOW_BATTERY);
            }
        }

        @Nested
        @DisplayName("Context: 트랜잭션이 정상 커밋되면")
        class Context_with_commit {

            @Test
            @DisplayName("It : 쿨다운을 유지한다")
            void it_keeps_cooldown() {
                //given
                givenCooldownAvailable();
                givenSaveAssignsId();
                alertService.detectBatteryLow(battery, occurredAt, wardId);

                //when
                triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);

                //then
                verify(alertNotificationRepository, never()).clearCooldown(any(), any());
            }
        }
    }

    @Nested
    @DisplayName("Describe: detectDisconnect 메서드는")
    class Describe_with_detectDisconnect {

        private void givenCooldownAvailable() {
            given(alertNotificationRepository.tryStartCooldown(wardId, AlertType.DISCONNECTED)).willReturn(true);
            given(userService.currentUserToUser(wardId)).willReturn(wardEntity);
        }

        @Nested
        @DisplayName("Context: 쿨다운 중이 아니면")
        class Context_without_cooldown {

            @Test
            @DisplayName("It : 마지막 정상 연결 시각을 occurredAt으로 Alert를 저장하고 id를 돌려준다")
            void it_saves_alert() {
                //given - occurredAt은 감지한 시각이 아니라 끊긴 시각이다. 스케줄러가 값으로 넘겨준다
                givenCooldownAvailable();
                givenSaveAssignsId();

                //when
                Optional<Long> saved = alertService.detectDisconnect(occurredAt, wardId);

                //then
                assertThat(saved).contains(alertId);

                ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
                verify(alertRepository).save(captor.capture());

                Alert alert = captor.getValue();
                assertThat(alert.getType()).isEqualTo(AlertType.DISCONNECTED);
                assertThat(alert.getContent()).isEqualTo("기기의 연결이 끊어졌습니다.");
                assertThat(alert.getOccurredAt()).isEqualTo(occurredAt);
                assertThat(alert.getSender()).isEqualTo(wardEntity);
                // 이미지도 위치도 없는 알림이다
                assertThat(alert.getS3Key()).isNull();
                assertThat(alert.getLatitude()).isNull();
                assertThat(alert.getLongitude()).isNull();
                assertThat(alert.getAddress()).isNull();
                assertThat(alert.getAction()).isNull();
            }
        }

        @Nested
        @DisplayName("Context: 쿨다운 중이면")
        class Context_with_active_cooldown {

            @Test
            @DisplayName("It : 빈 값을 돌려주고 저장하지 않는다")
            void it_skips_everything() {
                //given - 스케줄러가 중복 실행됐을 때의 방어다. 정상 경로에서는 unwatch가 먼저 막는다
                given(alertNotificationRepository.tryStartCooldown(wardId, AlertType.DISCONNECTED)).willReturn(false);

                //when
                Optional<Long> saved = alertService.detectDisconnect(occurredAt, wardId);

                //then
                assertThat(saved).isEmpty();
                verifyNoInteractions(userService);
                verify(alertRepository, never()).save(any());
            }
        }

        @Nested
        @DisplayName("Context: 트랜잭션이 롤백되면")
        class Context_with_rollback {

            @Test
            @DisplayName("It : 쿨다운을 되돌린다")
            void it_clears_cooldown() {
                //given
                givenCooldownAvailable();
                givenSaveAssignsId();
                alertService.detectDisconnect(occurredAt, wardId);

                //when
                triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

                //then
                verify(alertNotificationRepository).clearCooldown(wardId, AlertType.DISCONNECTED);
            }
        }

        @Nested
        @DisplayName("Context: 트랜잭션이 정상 커밋되면")
        class Context_with_commit {

            @Test
            @DisplayName("It : 쿨다운을 유지한다")
            void it_keeps_cooldown() {
                //given
                givenCooldownAvailable();
                givenSaveAssignsId();
                alertService.detectDisconnect(occurredAt, wardId);

                //when
                triggerAfterCompletion(TransactionSynchronization.STATUS_COMMITTED);

                //then
                verify(alertNotificationRepository, never()).clearCooldown(any(), any());
            }
        }
    }

    @Nested
    @DisplayName("Describe: getAlertDetail 메서드는")
    class Describe_with_getAlertDetail {

        String presignedUrl = "https://bucket.s3.ap-northeast-2.amazonaws.com/" + s3Key + "?X-Amz-Signature=abc";

        private Alert alertOf(User sender) {
            Alert alert = new Alert(AlertType.OBSTACLE, "전방 2m에 자전거가 세워져 있습니다",
                    37.4979, 127.0276, address, s3Key, occurredAt, "위험 음성 재생", sender);
            ReflectionTestUtils.setField(alert, "id", alertId);
            return alert;
        }

        private User userWithId(Long id) {
            User user = new User("ward@test.com", "password", "피보호자", "01012345678", UserRole.WARD);
            ReflectionTestUtils.setField(user, "id", id);
            return user;
        }

        @Nested
        @DisplayName("Context: 보호자가 자기 피보호자의 알림을 조회하면")
        class Context_with_own_ward_alert {

            @Test
            @DisplayName("It : presigned URL을 채운 상세 정보를 반환한다")
            void it_returns_detail() {
                //given
                Alert alert = alertOf(userWithId(wardId));
                given(alertRepository.findById(alertId)).willReturn(Optional.of(alert));
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(imageService.getPresignedUrl(s3Key)).willReturn(presignedUrl);

                //when
                AlertResponse response = alertService.getAlertDetail(alertId, guardian);

                //then
                assertThat(response.type()).isEqualTo(AlertType.OBSTACLE);
                // 저장은 Instant, 응답은 KST LocalDateTime. UTC 09:12:33.512 = KST 18:12:33.512
                assertThat(response.occurredAt())
                        .isEqualTo(occurredAt.atZone(ZoneId.of("Asia/Seoul")).toLocalDateTime());
                assertThat(response.occurredPlace()).isEqualTo(address);
                assertThat(response.content()).isEqualTo("전방 2m에 자전거가 세워져 있습니다");
                assertThat(response.action()).isEqualTo("위험 음성 재생");
                // s3Key가 아니라 조회 시점에 만든 presigned URL을 내려준다
                assertThat(response.presignedUrl()).isEqualTo(presignedUrl);
            }
        }

        @Nested
        @DisplayName("Context: 없는 알림 id가 주어지면")
        class Context_with_unknown_alert {

            @Test
            @DisplayName("It : NOT_FOUND_ALERT 예외를 던진다")
            void it_throws_not_found() {
                //given
                given(alertRepository.findById(alertId)).willReturn(Optional.empty());

                //when-then
                assertThatThrownBy(() -> alertService.getAlertDetail(alertId, guardian))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND_ALERT);

                verify(imageService, never()).getPresignedUrl(any());
            }
        }

        @Nested
        @DisplayName("Context: 다른 피보호자의 알림을 조회하면")
        class Context_with_others_alert {

            @Test
            @DisplayName("It : NOT_GUARDIAN 예외를 던지고 presigned URL을 만들지 않는다")
            void it_throws_not_guardian() {
                //given - 알림의 주인은 999번 피보호자인데, 이 보호자에게 연결된 피보호자는 2번이다
                Alert alert = alertOf(userWithId(999L));
                given(alertRepository.findById(alertId)).willReturn(Optional.of(alert));
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);

                //when-then
                assertThatThrownBy(() -> alertService.getAlertDetail(alertId, guardian))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_GUARDIAN);

                verify(imageService, never()).getPresignedUrl(any());
            }
        }
    }

    @Nested
    @DisplayName("Describe: getAlertsInWeek 메서드는")
    class Describe_with_getAlertsInWeek {

        ZoneId SEOUL = ZoneId.of("Asia/Seoul");

        private User wardWithId() {
            User user = new User("ward@test.com", "password", "피보호자", "01012345678", UserRole.WARD);
            ReflectionTestUtils.setField(user, "id", wardId);
            return user;
        }

        /** KST 기준 시각으로 알림을 만든다. 내부 저장은 Instant다 */
        private Alert alertAtKst(String kstDateTime, String content) {
            Instant instant = LocalDateTime.parse(kstDateTime).atZone(SEOUL).toInstant();
            return new Alert(AlertType.OBSTACLE, content, 37.4979, 127.0276,
                    address, s3Key, instant, "위험 음성 재생", wardWithId());
        }

        @Nested
        @DisplayName("Context: 최근 7일 내 알림이 여러 날짜에 걸쳐 있으면")
        class Context_with_alerts_across_dates {

            @Test
            @DisplayName("It : KST 날짜별로 묶어 최신 날짜부터 반환한다")
            void it_groups_by_kst_date() {
                //given - 리포지토리는 최신순으로 내려준다
                List<Alert> alerts = List.of(
                        alertAtKst("2026-08-05T18:55:00", "보행로에 배달 오토바이가 정차해 있습니다"),
                        alertAtKst("2026-08-05T07:05:00", "전방 2m에 자전거가 세워져 있습니다"),
                        alertAtKst("2026-08-03T08:45:00", "횡단보도 앞에 화분이 놓여 있습니다")
                );
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(alertRepository.findAllBySenderIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(eq(wardId), any(Instant.class)))
                        .willReturn(alerts);
                given(imageService.getPresignedUrl(s3Key)).willReturn("https://example.com/presigned");

                //when
                Map<LocalDate, List<AlertResponse>> result = alertService.getAlertsInWeek(guardian);

                //then - 최신 날짜가 먼저
                assertThat(result.keySet())
                        .containsExactly(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 3));
                assertThat(result.get(LocalDate.of(2026, 8, 5))).hasSize(2);
                assertThat(result.get(LocalDate.of(2026, 8, 3))).hasSize(1);
                // 그룹 안에서도 최신순이 유지된다
                assertThat(result.get(LocalDate.of(2026, 8, 5)))
                        .extracting(AlertResponse::content)
                        .containsExactly("보행로에 배달 오토바이가 정차해 있습니다", "전방 2m에 자전거가 세워져 있습니다");
            }

            @Test
            @DisplayName("It : UTC 기준으로는 전날인 새벽 알림도 KST 날짜로 묶는다")
            void it_groups_by_kst_not_utc() {
                //given - KST 08-05 07:05 = UTC 08-04 22:05
                Alert earlyMorning = alertAtKst("2026-08-05T07:05:00", "전방 2m에 자전거가 세워져 있습니다");
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(alertRepository.findAllBySenderIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(eq(wardId), any(Instant.class)))
                        .willReturn(List.of(earlyMorning));
                given(imageService.getPresignedUrl(s3Key)).willReturn("https://example.com/presigned");

                //when
                Map<LocalDate, List<AlertResponse>> result = alertService.getAlertsInWeek(guardian);

                //then - UTC로 묶었다면 08-04가 됐을 것이다
                assertThat(result).containsOnlyKeys(LocalDate.of(2026, 8, 5));
                assertThat(result.get(LocalDate.of(2026, 8, 5)).get(0).occurredAt())
                        .isEqualTo(LocalDateTime.of(2026, 8, 5, 7, 5));
            }

            @Test
            @DisplayName("It : 조회 기준 시각을 오늘 포함 7일치의 시작(6일 전 KST 자정)으로 넘긴다")
            void it_queries_from_kst_midnight() {
                //given
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(alertRepository.findAllBySenderIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(eq(wardId), any(Instant.class)))
                        .willReturn(List.of());

                //when
                alertService.getAlertsInWeek(guardian);

                //then
                ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
                verify(alertRepository)
                        .findAllBySenderIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(eq(wardId), captor.capture());

                LocalDateTime kst = LocalDateTime.ofInstant(captor.getValue(), SEOUL);
                assertThat(kst.toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
                assertThat(kst.toLocalDate()).isEqualTo(LocalDate.now(SEOUL).minusDays(6));
            }
        }

        @Nested
        @DisplayName("Context: 최근 7일 내 알림이 없으면")
        class Context_without_alerts {

            @Test
            @DisplayName("It : 빈 맵을 반환한다")
            void it_returns_empty_map() {
                //given
                given(userService.getWardIdFromGuardianId(guardianId)).willReturn(wardId);
                given(alertRepository.findAllBySenderIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(eq(wardId), any(Instant.class)))
                        .willReturn(List.of());

                //when
                Map<LocalDate, List<AlertResponse>> result = alertService.getAlertsInWeek(guardian);

                //then
                assertThat(result).isEmpty();
                verify(imageService, never()).getPresignedUrl(any());
            }
        }
    }

    private void triggerAfterCompletion(int status) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(status));
    }
}

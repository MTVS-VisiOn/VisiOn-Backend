package mtvs.onvision.vision.location.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import mtvs.onvision.vision.common.domain.HistoryEntity;

import java.time.Instant;

/**
 * 피보호자의 위치 이력.
 *
 * **저장은 JPA가 하지 않는다.** Redis 버퍼를 비우는 경로가 `LocationHistoryJdbcRepository`의
 * 배치 INSERT이고, 재시도 멱등성을 위해 `ON CONFLICT DO NOTHING`을 쓰는데 JPA로는 표현할 수 없다.
 * 이 엔티티는 조회용이며 `ddl-auto: validate`가 컬럼을 맞춰보는 대상이기도 하다.
 *
 * append-only라 `BaseEntity`가 아니라 `HistoryEntity`(created_at만)를 상속한다.
 */
@Entity
@Getter
@Table(name = "location_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationHistory extends HistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    /** 위치를 보낸 피보호자. `LocationReport.userId`가 그대로 들어온다 */
    @Column(nullable = false)
    private Long wardId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    /** 반경 오차(m). 기기가 못 주면 null이다 */
    private Float accuracy;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MovementStatus status;

    /** 기기가 측정한 시각. 감사 시각인 createdAt과 다르다 — 버퍼에 머문 시간만큼 벌어진다 */
    @Column(nullable = false)
    private Instant recordedAt;
}

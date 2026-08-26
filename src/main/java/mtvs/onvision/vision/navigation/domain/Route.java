package mtvs.onvision.vision.navigation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import mtvs.onvision.vision.common.domain.BaseEntity;
import mtvs.onvision.vision.navigation.dto.NavigationSummary;
import mtvs.onvision.vision.user.domain.User;

@Entity
@Getter
@Table(name = "routes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Route extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false,length = 50)
    private String startingName;

    @Column(nullable = false, length = 100)
    private String startingAddress;

    @Column(nullable = false)
    private Double startingLat;

    @Column(nullable = false)
    private Double startingLon;

    @Column(nullable = false, length = 50)
    private String destinationName;

    @Column(nullable = false, length = 100)
    private String destinationAddress;

    @Column(nullable = false)
    private Double destinationLat;

    @Column(nullable = false)
    private Double destinationLon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RouteStatus status;

    @Column(nullable = false)
    private Integer totalDistance;

    @Column(nullable = false)
    private Integer totalTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransportMode mode;

    @Column(nullable = false, columnDefinition = "text")
    private String report;   //경로 전체 json

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private User ward;

    public Route (TransportMode mode, NavigationSummary summary, String json, User ward) {
        this.startingName = summary.startingName();
        this.startingAddress = summary.startingAddress();
        this.startingLat = summary.startingCoordinate().getFirst();
        this.startingLon = summary.startingCoordinate().getLast();
        this.destinationName = summary.destinationName();
        this.destinationAddress = summary.destinationAddress();
        this.destinationLat = summary.destinationCoordinate().getFirst();
        this.destinationLon = summary.destinationCoordinate().getLast();
        this.status = RouteStatus.IN_PROGRESS;
        this.totalDistance = summary.totalDistance();
        this.totalTime = summary.totalTime();
        this.mode = mode;
        this.report = json;
        this.ward = ward;
    }

    public void canceled() {
        this.status = RouteStatus.CANCELED;
    }

    public void completed() {
        this.status = RouteStatus.COMPLETED;
    }
}

package mtvs.onvision.vision.navigation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import mtvs.onvision.vision.common.domain.BaseEntity;
import mtvs.onvision.vision.navigation.dto.NavigationRouteReport;
import mtvs.onvision.vision.navigation.dto.NavigationSummary;
import mtvs.onvision.vision.navigation.dto.TransitRoute;
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

    @Column(nullable = false, columnDefinition = "text")
    private String report;   //경로 전체 json

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private User ward;

    public Route (NavigationRouteReport report, String json, User ward) {
        NavigationSummary summary = report.summary();
        this.startingName = summary.startingName();
        this.startingAddress = summary.startingRoadAddress();
        this.startingLat = summary.startingCoordinate().getLast();
        this.startingLon = summary.startingCoordinate().getFirst();
        this.destinationName = summary.destinationName();
        this.destinationAddress = summary.destinationRoadAddress();
        this.destinationLat = summary.destinationCoordinate().getLast();
        this.destinationLon = summary.destinationCoordinate().getFirst();
        this.status = RouteStatus.IN_PROGRESS;
        this.totalDistance = summary.totalDistance();
        this.totalTime = summary.totalTime();
        this.report = json;
        this.ward = ward;
    }

    public Route(TransitRoute report, String json, User ward) {
        NavigationSummary summary = report.summary();
        this.startingName = summary.startingName();
        this.startingAddress = summary.startingRoadAddress();
        this.startingLat = summary.startingCoordinate().getLast();
        this.startingLon = summary.startingCoordinate().getFirst();
        this.destinationName = summary.destinationName();
        this.destinationAddress = summary.destinationRoadAddress();
        this.destinationLat = summary.destinationCoordinate().getLast();
        this.destinationLon = summary.destinationCoordinate().getFirst();
        this.status = RouteStatus.IN_PROGRESS;
        this.totalDistance = summary.totalDistance();
        this.totalTime = summary.totalTime();
        this.report = json;
        this.ward = ward;
    }

    public void canceled() {
        this.status = RouteStatus.CANCELLED;
    }

    public void completed() {
        this.status = RouteStatus.COMPLETED;
    }
}

package mtvs.onvision.vision.favorite.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import mtvs.onvision.vision.common.domain.BaseEntity;
import mtvs.onvision.vision.favorite.dto.FavoriteRequest;
import mtvs.onvision.vision.user.domain.User;

@Entity
@Getter
@Table(name = "favorites")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;   //지도상의 장소

    @Column(length = 50)
    private String nickname;   //장소의 별칭

    @Column(nullable = false, length = 50)
    private String pkey;  //티맵 아이디

    @Column(nullable = false, length = 100)
    private String landAddress;

    @Column(nullable = false, length = 100)
    private String roadAddress;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @ManyToOne
    @JoinColumn(nullable = false)
    private User user;

    public Favorite (FavoriteRequest request, User user) {
        this.name = request.name();
        this.nickname = request.nickname() == null? null : request.nickname();
        this.pkey = request.pkey();
        this.landAddress = request.landAddress();
        this.roadAddress = request.roadAddress();
        this.latitude = request.noorLat();
        this.longitude = request.noorLon();
        this.user = user;
    }

    public void update(String nickname) {
        this.nickname = nickname;
    }
}

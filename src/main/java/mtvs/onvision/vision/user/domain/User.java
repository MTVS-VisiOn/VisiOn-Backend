package mtvs.onvision.vision.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false,  unique = true)
    private String phoneNumber;

    @Column(nullable = false)
    private UserRole role;

    @OneToOne
    private User ward;
    //피보호자일때
    public User (String email, String password, String nickname, String phoneNumber) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
        this.role = UserRole.WARD;
    }
    //보호자일때
    public User (String email, String password, String nickname, String phoneNumber, User ward) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
        this.role = UserRole.GUARDIAN;
        this.ward = ward;
    }
}

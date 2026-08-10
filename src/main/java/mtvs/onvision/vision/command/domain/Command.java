package mtvs.onvision.vision.command.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import mtvs.onvision.vision.common.domain.HistoryEntity;
import mtvs.onvision.vision.user.domain.User;

import java.time.Instant;

@Entity
@Getter
@Table(name = "commands")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Command extends HistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CommandType type;

    @Column(nullable = false)
    private Instant occurredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private User receiver;

    public Command(String content, CommandType type, User receiver) {
        this.content = content;
        this.type = type;
        this.occurredAt = Instant.now();
        this.receiver = receiver;
    }
}

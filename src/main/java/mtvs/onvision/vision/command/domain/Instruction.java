package mtvs.onvision.vision.command.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import mtvs.onvision.vision.user.domain.User;

@Entity
@Getter
@Table(name = "instructions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Instruction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    private String instruction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private User guardian;

    public Instruction(String instruction, User guardian) {
        this.instruction = instruction;
        this.guardian = guardian;
    }

    public void update(String instruction) {
        this.instruction = instruction;
    }
}

package mtvs.onvision.vision.command.repository;

import mtvs.onvision.vision.command.domain.Command;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommandRepository extends JpaRepository<Command, Long> {
    List<Command> findTop5ByReceiverIdOrderByCreatedAtDesc(Long receiverId);
}

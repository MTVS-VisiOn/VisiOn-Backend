package mtvs.onvision.vision.command.repository;

import mtvs.onvision.vision.command.domain.Command;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommandRepository extends JpaRepository<Command, Long> {
}

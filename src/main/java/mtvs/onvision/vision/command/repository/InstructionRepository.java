package mtvs.onvision.vision.command.repository;

import mtvs.onvision.vision.command.domain.Instruction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstructionRepository extends JpaRepository<Instruction, Long> {
}

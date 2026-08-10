package mtvs.onvision.vision.command.repository;

import mtvs.onvision.vision.command.domain.Instruction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstructionRepository extends JpaRepository<Instruction, Long> {
    List<Instruction> findAllByGuardianId(Long guardianId);
}

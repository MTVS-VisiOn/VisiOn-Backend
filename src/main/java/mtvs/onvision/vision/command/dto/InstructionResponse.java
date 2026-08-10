package mtvs.onvision.vision.command.dto;

import mtvs.onvision.vision.command.domain.Instruction;

public record InstructionResponse(
        Long id,
        String content
) {
    public static InstructionResponse from(Instruction instruction) {
        return new InstructionResponse(instruction.getId(), instruction.getInstruction());
    }
}

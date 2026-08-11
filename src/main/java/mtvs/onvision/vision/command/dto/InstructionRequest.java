package mtvs.onvision.vision.command.dto;

import jakarta.validation.constraints.NotBlank;

public record InstructionRequest(
        @NotBlank
        String content
) {
}

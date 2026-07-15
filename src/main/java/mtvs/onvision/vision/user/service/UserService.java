package mtvs.onvision.vision.user.service;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.util.PreConditions;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.dto.SignupRequest;
import mtvs.onvision.vision.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    @Transactional
    public void signup(SignupRequest request) {
        PreConditions.check(userRepository.existsByEmail(request.email()), ErrorCode.EXIST_EMAIL);
        User user;
        if (request.role() == UserRole.WARD) user = new User(request.email(), request.password(), request.userName(), request.phoneNumber());
        else {
            User ward = userRepository.findById(request.wardId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_WARD));
            user = new User(request.email(), request.password(), request.userName(), request.phoneNumber(),ward);
        }
        userRepository.save(user);
    }
}

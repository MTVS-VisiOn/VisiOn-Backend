package mtvs.onvision.vision.user.service;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.service.JwtTokenProvider;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.util.PreConditions;
import mtvs.onvision.vision.auth.dto.KeyPair;
import mtvs.onvision.vision.auth.dto.LoginRequest;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.dto.SignupRequest;
import mtvs.onvision.vision.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public void signup(SignupRequest request) {
        PreConditions.check(userRepository.existsByEmail(request.email()), ErrorCode.EXIST_EMAIL);
        PreConditions.check(userRepository.existsByPhoneNumber(request.phoneNumber()), ErrorCode.EXIST_PHONENUMBER);

        User user;
        if (request.role() == UserRole.WARD) user = new User(request.email(), request.password(), request.userName(), request.phoneNumber());
        else {
            User ward = userRepository.findById(request.wardId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_WARD));
            user = new User(request.email(), passwordEncoder.encode(request.password()), request.userName(), request.phoneNumber(),ward);
        }
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public KeyPair login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_USER));
        passwordEncoder.matches(request.password(), user.getPassword());
        return jwtTokenProvider.issueKeyPair(user.getId(), user.getEmail(), user.getRole());
    }
}

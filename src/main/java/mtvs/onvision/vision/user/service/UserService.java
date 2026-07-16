package mtvs.onvision.vision.user.service;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.*;
import mtvs.onvision.vision.auth.repository.RefreshTokenRepository;
import mtvs.onvision.vision.auth.service.JwtTokenProvider;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.util.PreConditions;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.dto.SignupRequest;
import mtvs.onvision.vision.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void signup(SignupRequest request) {
        PreConditions.check(userRepository.existsByEmail(request.email()), ErrorCode.EXIST_EMAIL);
        PreConditions.check(userRepository.existsByPhoneNumber(request.phoneNumber()), ErrorCode.EXIST_PHONENUMBER);

        User user;
        if (request.role() == UserRole.WARD) user = new User(request.email(), passwordEncoder.encode(request.password()), request.nickname(), request.phoneNumber());
        else {
            User ward = userRepository.findById(request.wardId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_WARD));
            user = new User(request.email(), passwordEncoder.encode(request.password()), request.nickname(), request.phoneNumber(),ward);
        }
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public KeyPair login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_USER));
        PreConditions.check(!passwordEncoder.matches(request.password(), user.getPassword()), ErrorCode.NOT_MATCH_PASSWORD);
        KeyPair keyPair = jwtTokenProvider.issueKeyPair(user.getId(), user.getEmail(), user.getRole());
        refreshTokenRepository.save(user.getId(), keyPair.refreshToken());
        return keyPair;
    }

    @Transactional
    public KeyPair refreshToken(RefreshRequest request) {
        TokenBody tokenBody = jwtTokenProvider.parseJwt(request.refreshToken());
        String repositoryToken = refreshTokenRepository.getToken(tokenBody.userId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_REFRESH));

        PreConditions.check(!repositoryToken.equals(request.refreshToken()), ErrorCode.INVALID_REFRESH_TOKEN);

        KeyPair keyPair = jwtTokenProvider.issueKeyPair(tokenBody.userId(), tokenBody.email(), tokenBody.role());
        refreshTokenRepository.save(tokenBody.userId(), keyPair.refreshToken());
        return keyPair;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_USER));
        return new CurrentUser(user.getId(), user.getEmail(), user.getRole());
    }

    public void logout(CurrentUser currentUser) {
        refreshTokenRepository.delete(currentUser.getId());
    }
}

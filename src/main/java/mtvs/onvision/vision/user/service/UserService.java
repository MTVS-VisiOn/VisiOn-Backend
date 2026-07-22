package mtvs.onvision.vision.user.service;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.*;
import mtvs.onvision.vision.auth.repository.RefreshTokenRepository;
import mtvs.onvision.vision.auth.service.JwtTokenProvider;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.util.PreConditions;
import mtvs.onvision.vision.user.domain.Relation;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.dto.ResisterGuardianResponse;
import mtvs.onvision.vision.user.dto.SignupRequest;
import mtvs.onvision.vision.user.repository.RegisterTokenRepository;
import mtvs.onvision.vision.user.repository.RelationRepository;
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
    private final RelationRepository relationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RegisterTokenRepository registerTokenRepository;

    @Transactional
    public void signup(SignupRequest request) {
        PreConditions.check(userRepository.existsByEmail(request.email()), ErrorCode.EXIST_EMAIL);
        PreConditions.check(userRepository.existsByPhoneNumber(request.phoneNumber()), ErrorCode.EXIST_PHONENUMBER);

        if (request.role() == UserRole.WARD) {
            User user = new User(request.email(), passwordEncoder.encode(request.password()), request.nickname(), request.phoneNumber(), UserRole.WARD);
            userRepository.save(user);
        }
        else {
            //토큰 확인
            Long wardId = jwtTokenProvider.parseId(request.registerToken());
            String repositoryToken = registerTokenRepository.getToken(wardId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_REGISTER));
            PreConditions.check(!repositoryToken.equals(request.registerToken()), ErrorCode.INVALID_REGISTER_TOKEN);

            //회원 등록
            User ward = userRepository.findByIdAndRole(wardId, UserRole.WARD).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_WARD));
            PreConditions.check(relationRepository.existsByWard(ward), ErrorCode.EXIST_GUARDIAN);
            User guardian = new User(request.email(), passwordEncoder.encode(request.password()), request.nickname(), request.phoneNumber(),UserRole.GUARDIAN);
            userRepository.save(guardian);
            Relation relation = new Relation(ward, guardian);
            relationRepository.save(relation);
        }
    }

    @Transactional
    public ResisterGuardianResponse getGuardianRegisterToken(CurrentUser currentUser) {
        String registerToken = jwtTokenProvider.issueRegisterToken(currentUser.getId(), currentUser.getEmail(), UserRole.WARD);
        registerTokenRepository.save(currentUser.getId(), registerToken);
        return new ResisterGuardianResponse(registerToken);
    }

    @Transactional
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

package mtvs.onvision.vision.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.auth.dto.*;
import mtvs.onvision.vision.auth.repository.RefreshTokenRepository;
import mtvs.onvision.vision.auth.service.JwtTokenProvider;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.util.PreConditions;
import mtvs.onvision.vision.user.domain.Fid;
import mtvs.onvision.vision.user.domain.Relation;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.dto.FidRequest;
import mtvs.onvision.vision.user.dto.RegisterGuardianResponse;
import mtvs.onvision.vision.user.dto.SignupRequest;
import mtvs.onvision.vision.user.dto.UserResponse;
import mtvs.onvision.vision.user.repository.FidRepository;
import mtvs.onvision.vision.user.repository.RegisterTokenRepository;
import mtvs.onvision.vision.user.repository.RelationRepository;
import mtvs.onvision.vision.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    private final UserRepository userRepository;
    private final RelationRepository relationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RegisterTokenRepository registerTokenRepository;
    private final FidRepository fidRepository;

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
    public RegisterGuardianResponse getGuardianRegisterToken(CurrentUser currentUser) {
        String registerToken = jwtTokenProvider.issueRegisterToken(currentUser.getId(), currentUser.getEmail(), UserRole.WARD);
        registerTokenRepository.save(currentUser.getId(), registerToken);
        return new RegisterGuardianResponse(registerToken);
    }

    @Transactional
    public KeyPair login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_USER));
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
        User user = userRepository.findByEmailAndDeletedAtIsNull(username).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_USER));
        return new CurrentUser(user.getId(), user.getEmail(), user.getRole());
    }

    @Transactional
    public void logout(LogoutRequest request, CurrentUser currentUser) {
        refreshTokenRepository.delete(currentUser.getId());

        // fid는 선택값이다. 없으면 refreshToken만 지우고 끝낸다
        if (request == null || request.fid() == null || request.fid().isBlank()) {
            return;
        }
        // 이미 지워졌거나 등록된 적 없는 fid는 무시한다. 로그아웃 자체는 성공해야 한다
        fidRepository.findByFid(request.fid()).ifPresent(target -> {
            PreConditions.check(!target.getUser().getId().equals(currentUser.getId()), ErrorCode.NOT_OWNER);
            fidRepository.delete(target);
            // 하드 삭제라 행이 남지 않는다. 등록 해제 이력은 이 로그가 유일하다
            log.info("Fid removed by logout: fid={}, userId={}", request.fid(), currentUser.getId());
        });
    }

    @Transactional
    public void checkDeviceFid(FidRequest request, CurrentUser currentUser) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_USER));

        fidRepository.findByFid(request.fid()).ifPresentOrElse(
                fid -> {
                    // 앱이 콜백마다 같은 값을 보낸다. 소유자가 실제로 바뀔 때만 남긴다
                    Long previousOwnerId = fid.getUser().getId();
                    if (!previousOwnerId.equals(user.getId())) {
                        fid.refresh(user);
                        log.info("Fid owner changed: fid={}, from userId={}, to userId={}",
                                request.fid(), previousOwnerId, user.getId());
                    }
                },
                () -> {
                    fidRepository.save(new Fid(request.fid(), user));
                    log.info("Fid registered: fid={}, userId={}", request.fid(), user.getId());
                }
        );
    }

    @Transactional(readOnly = true)
    public User currentUserToUser(Long userId) {
        return userRepository.getReferenceById(userId);
//        return userRepository.findById(currentUser.getId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_USER));
    }

    //보호자 -> 피보호자
    @Transactional(readOnly = true)
    public Long getWardIdFromGuardianId(Long guardianId) {
        Relation relation = relationRepository.findByGuardianId(guardianId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_RELATION));
        return relation.getWard().getId();
    }

    //피보호자 -> 보호자
    @Transactional(readOnly = true)
    public Long getGuardianIdFromWardId(Long wardId) {
        Relation relation = relationRepository.findByWardId(wardId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_RELATION));
        return relation.getGuardian().getId();
    }


    @Transactional(readOnly = true)
    public UserResponse getUserInfo(CurrentUser currentUser) {
        if (currentUser.getRole().equals(UserRole.GUARDIAN)) {
            User guardian = userRepository.findByIdAndDeletedAtIsNull(currentUser.getId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_USER));
            Long wardId = getWardIdFromGuardianId(currentUser.getId());
            User ward = userRepository.findByIdAndDeletedAtIsNull(wardId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_USER));
            return UserResponse.from(guardian, ward);
        }else {
            User ward = userRepository.findByIdAndDeletedAtIsNull(currentUser.getId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_USER));
            return UserResponse.from(ward);
        }
    }

    @Transactional(readOnly = true)
    public List<String> getFids(Long userId) {
        List<Fid> fids = fidRepository.findByUserId(userId);
        return fids.stream().map(Fid::getFid).toList();
    }

    @Transactional
    public void deleteFid(String fid) {
        fidRepository.deleteByFid(fid);
        // FCM이 UNREGISTERED를 돌려준 죽은 기기. 중복 푸시 추적의 근거가 된다
        log.info("Fid removed by FCM UNREGISTERED: fid={}", fid);
    }
}

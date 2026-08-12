package mtvs.onvision.vision.user.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.auth.dto.*;
import mtvs.onvision.vision.auth.repository.RefreshTokenRepository;
import mtvs.onvision.vision.auth.service.JwtTokenProvider;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.util.PreConditions;
import mtvs.onvision.vision.user.domain.*;
import mtvs.onvision.vision.user.dto.*;
import mtvs.onvision.vision.user.repository.FidRepository;
import mtvs.onvision.vision.user.repository.RegisterCodeRepository;
import mtvs.onvision.vision.user.repository.RelationRepository;
import mtvs.onvision.vision.user.repository.UserRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    private final UserRepository userRepository;
    private final RelationRepository relationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RegisterCodeRepository registerCodeRepository;
    private final FidRepository fidRepository;

    // I·O·Q·Z 제외 — 사람이 읽어서 전달하는 코드다
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPRSTUVWXY23456789".toCharArray();
    private static final Pattern CODE_PATTERN = Pattern.compile("^[" + new String(CODE_ALPHABET) + "]{6}$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_LENGTH = 6;
    private static final int ISSUE_MAX_ATTEMPTS = 5;

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
            PreConditions.check(!CODE_PATTERN.matcher(request.registerCode()).matches(), ErrorCode.INVALID_REGISTER_CODE);
            Long wardId = registerCodeRepository.getUserId(RegisterType.GUARDIAN, request.registerCode()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_REGISTER));

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
    public RegisterResponse getGuardianRegisterCode(CurrentUser currentUser) {
        return getRegisterCode(currentUser, RegisterType.GUARDIAN);
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
            PreConditions.check(!target.getUser().getId().equals(currentUser.getId()), ErrorCode.NOT_OWNER_FID);
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
    //보호자 -> 피보호자 객체
    @Transactional(readOnly = true)
    public User getWardFromGuardianId(Long guardianId) {
        Relation relation = relationRepository.findByGuardianId(guardianId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_RELATION));
        return relation.getWard();
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


    public RegisterResponse getDeviceRegisterCode(CurrentUser currentUser) {
        return getRegisterCode(currentUser, RegisterType.DEVICE);
    }

    public PairingDeviceResponse pairingDevice(@Valid DeviceRegisterRequest request) {
        Long wardId = registerCodeRepository.getUserId(RegisterType.DEVICE, request.code()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_REGISTER));
        User user = userRepository.findByIdAndDeletedAtIsNull(wardId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_USER));
        log.info("Device paired: wardId={}, deviceName={}, deviceSerialTail={}", wardId, request.deviceName(), request.deviceSerialTail());
        registerCodeRepository.delete(RegisterType.DEVICE, request.code());
        String token = jwtTokenProvider.issueAccessToken(wardId, user.getEmail(), user.getRole());
        return new PairingDeviceResponse(token);
    }


    private @NonNull RegisterResponse getRegisterCode(CurrentUser currentUser, RegisterType type) {
        for (int i = 0; i < ISSUE_MAX_ATTEMPTS; i++) {
            String code = generateRegisterCode();
            if (registerCodeRepository.saveIfAbsent(type, code, currentUser.getId())) {
                return new RegisterResponse(code);
            }
        }
        throw new BusinessException(ErrorCode.FAILED_ISSUE_REGISTER_CODE);
    }

    private String generateRegisterCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
                code.append(CODE_ALPHABET[SECURE_RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return code.toString();
    }
}

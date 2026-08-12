package mtvs.onvision.vision.user.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import mtvs.onvision.vision.auth.domain.TokenType;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.auth.dto.KeyPair;
import mtvs.onvision.vision.auth.dto.LoginRequest;
import mtvs.onvision.vision.auth.dto.LogoutRequest;
import mtvs.onvision.vision.auth.dto.RefreshRequest;
import mtvs.onvision.vision.auth.dto.TokenBody;
import mtvs.onvision.vision.auth.repository.RefreshTokenRepository;
import mtvs.onvision.vision.auth.service.JwtTokenProvider;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.user.domain.Fid;
import mtvs.onvision.vision.user.domain.Relation;
import mtvs.onvision.vision.user.domain.User;
import mtvs.onvision.vision.user.domain.RegisterType;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.dto.UserResponse;
import mtvs.onvision.vision.user.dto.DeviceRegisterRequest;
import mtvs.onvision.vision.user.dto.PairingDeviceResponse;
import mtvs.onvision.vision.user.dto.RegisterResponse;
import mtvs.onvision.vision.user.dto.SignupRequest;
import mtvs.onvision.vision.user.repository.FidRepository;
import mtvs.onvision.vision.user.repository.RegisterCodeRepository;
import mtvs.onvision.vision.user.repository.RelationRepository;
import mtvs.onvision.vision.user.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService의")
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RelationRepository relationRepository;

    @Mock
    private RegisterCodeRepository registerCodeRepository;

    @Mock
    private FidRepository fidRepository;

    Long userId = 1L;
    Long wardId = 2L;
    String fid = "test-fid-0001";
    String email = "user@test.com";
    String password = "password1234";
    String encodedPassword = "encodedPassword1234";
    String nickname = "테스트유저";
    String phoneNumber = "010-1234-5678";
    String registerCode = "TV8HYB";
    String malformedRegisterCode = "tv8hy";   // 소문자 + 5자리 — CODE_PATTERN 위반
    String registerCodePattern = "^[ABCDEFGHJKLMNPRSTUVWXY23456789]{6}$";
    int ISSUE_MAX_ATTEMPTS = 5;   // UserService의 같은 이름 상수와 맞춰야 한다

    SignupRequest signupRequest;
    LoginRequest loginRequest;
    RefreshRequest refreshRequest;
    User user;
    User ward;

    @Nested
    @DisplayName("Describe: signup 메서드는")
    class Describe_with_signup {

        @Nested
        @DisplayName("Context: 이미 존재하는 이메일이 주어지면")
        class Context_with_existing_email {
            @BeforeEach
            void setup() {
                signupRequest = new SignupRequest(email, password, nickname, phoneNumber, UserRole.WARD, null);
            }

            @Test
            @DisplayName("It : EXIST_EMAIL 오류 발생")
            void it_throws_exist_email() {
                //given
                given(userRepository.existsByEmail(email)).willReturn(true);
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.signup(signupRequest));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.EXIST_EMAIL.getMessage());
            }
        }

        @Nested
        @DisplayName("Context: 이미 존재하는 전화번호가 주어지면")
        class Context_with_existing_phone_number {
            @BeforeEach
            void setup() {
                signupRequest = new SignupRequest(email, password, nickname, phoneNumber, UserRole.WARD, null);
            }

            @Test
            @DisplayName("It : EXIST_PHONENUMBER 오류 발생")
            void it_throws_exist_phone_number() {
                //given
                given(userRepository.existsByEmail(email)).willReturn(false);
                given(userRepository.existsByPhoneNumber(phoneNumber)).willReturn(true);
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.signup(signupRequest));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.EXIST_PHONENUMBER.getMessage());
            }
        }

        @Nested
        @DisplayName("Context: role이 WARD이고 올바른 데이터가 주어지면")
        class Context_with_ward_role {
            @BeforeEach
            void setup() {
                signupRequest = new SignupRequest(email, password, nickname, phoneNumber, UserRole.WARD, null);
            }

            @Test
            @DisplayName("It : User 저장 성공")
            void it_success_signup_without_ward() {
                //given
                given(userRepository.existsByEmail(email)).willReturn(false);
                given(userRepository.existsByPhoneNumber(phoneNumber)).willReturn(false);
                given(passwordEncoder.encode(password)).willReturn(encodedPassword);
                //when
                userService.signup(signupRequest);

                //then
                ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
                verify(userRepository).save(captor.capture());
                User saved = captor.getValue();

                assertThat(saved.getEmail()).isEqualTo(email);
                assertThat(saved.getPassword()).isEqualTo(encodedPassword);
                assertThat(saved.getNickname()).isEqualTo(nickname);
                assertThat(saved.getPhoneNumber()).isEqualTo(phoneNumber);
                assertThat(saved.getRole()).isEqualTo(UserRole.WARD);
            }
        }

        @Nested
        @DisplayName("Context: role이 GUARDIAN이고 유효한 registerCode가 주어지면")
        class Context_with_available_register_code {
            @BeforeEach
            void setup() {
                signupRequest = new SignupRequest(email, password, nickname, phoneNumber, UserRole.GUARDIAN, registerCode);
                ward = new User("ward@test.com", encodedPassword, "피보호자", "010-9999-8888", UserRole.WARD);
                ReflectionTestUtils.setField(ward, "id", wardId);
            }

            @Test
            @DisplayName("It : registerCode로 wardId를 찾아 Guardian과 Relation 저장 성공")
            void it_success_signup_with_register_code() {
                //given
                given(userRepository.existsByEmail(email)).willReturn(false);
                given(userRepository.existsByPhoneNumber(phoneNumber)).willReturn(false);
                given(registerCodeRepository.getUserId(RegisterType.GUARDIAN, registerCode)).willReturn(Optional.of(wardId));
                given(userRepository.findByIdAndRole(wardId, UserRole.WARD)).willReturn(Optional.of(ward));
                given(relationRepository.existsByWard(ward)).willReturn(false);
                given(passwordEncoder.encode(password)).willReturn(encodedPassword);
                //when
                userService.signup(signupRequest);

                //then
                ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
                verify(userRepository).save(captor.capture());
                User saved = captor.getValue();

                assertThat(saved.getEmail()).isEqualTo(email);
                assertThat(saved.getPassword()).isEqualTo(encodedPassword);
                assertThat(saved.getRole()).isEqualTo(UserRole.GUARDIAN);

                ArgumentCaptor<Relation> relationCaptor = ArgumentCaptor.forClass(Relation.class);
                verify(relationRepository).save(relationCaptor.capture());
                Relation savedRelation = relationCaptor.getValue();
                assertThat(savedRelation.getWard()).isEqualTo(ward);
                assertThat(savedRelation.getGuardian()).isEqualTo(saved);
            }
        }

        @Nested
        @DisplayName("Context: role이 GUARDIAN이고 저장소에 registerCode가 존재하지 않으면")
        class Context_with_no_stored_register_code {
            @BeforeEach
            void setup() {
                signupRequest = new SignupRequest(email, password, nickname, phoneNumber, UserRole.GUARDIAN, registerCode);
            }

            @Test
            @DisplayName("It : NOT_FOUND_REGISTER 오류 발생")
            void it_throws_not_found_register() {
                //given
                given(userRepository.existsByEmail(email)).willReturn(false);
                given(userRepository.existsByPhoneNumber(phoneNumber)).willReturn(false);
                given(registerCodeRepository.getUserId(RegisterType.GUARDIAN, registerCode)).willReturn(Optional.empty());
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.signup(signupRequest));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.NOT_FOUND_REGISTER.getMessage());
            }
        }

        @Nested
        @DisplayName("Context: role이 GUARDIAN이고 code 형식이 올바르지 않으면")
        class Context_with_malformed_register_code {
            @BeforeEach
            void setup() {
                signupRequest = new SignupRequest(email, password, nickname, phoneNumber, UserRole.GUARDIAN, malformedRegisterCode);
            }

            @Test
            @DisplayName("It : 저장소를 조회하지 않고 INVALID_REGISTER_CODE 오류 발생")
            void it_throws_invalid_register_code() {
                //given
                given(userRepository.existsByEmail(email)).willReturn(false);
                given(userRepository.existsByPhoneNumber(phoneNumber)).willReturn(false);
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.signup(signupRequest));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.INVALID_REGISTER_CODE.getMessage());
                verifyNoInteractions(registerCodeRepository);
            }
        }

        @Nested
        @DisplayName("Context: role이 GUARDIAN이고 registerCode로 찾은 피보호자가 존재하지 않으면")
        class Context_with_unavailable_ward_id {
            @BeforeEach
            void setup() {
                signupRequest = new SignupRequest(email, password, nickname, phoneNumber, UserRole.GUARDIAN, registerCode);
            }

            @Test
            @DisplayName("It : NOT_FOUND_WARD 오류 발생")
            void it_throws_not_found_ward() {
                //given
                given(userRepository.existsByEmail(email)).willReturn(false);
                given(userRepository.existsByPhoneNumber(phoneNumber)).willReturn(false);
                given(registerCodeRepository.getUserId(RegisterType.GUARDIAN, registerCode)).willReturn(Optional.of(wardId));
                given(userRepository.findByIdAndRole(wardId, UserRole.WARD)).willReturn(Optional.empty());
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.signup(signupRequest));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.NOT_FOUND_WARD.getMessage());
            }
        }

        @Nested
        @DisplayName("Context: role이 GUARDIAN이고 해당 피보호자에게 이미 보호자가 등록되어 있으면")
        class Context_with_existing_guardian {
            @BeforeEach
            void setup() {
                signupRequest = new SignupRequest(email, password, nickname, phoneNumber, UserRole.GUARDIAN, registerCode);
                ward = new User("ward@test.com", encodedPassword, "피보호자", "010-9999-8888", UserRole.WARD);
                ReflectionTestUtils.setField(ward, "id", wardId);
            }

            @Test
            @DisplayName("It : EXIST_GUARDIAN 오류 발생")
            void it_throws_exist_guardian() {
                //given
                given(userRepository.existsByEmail(email)).willReturn(false);
                given(userRepository.existsByPhoneNumber(phoneNumber)).willReturn(false);
                given(registerCodeRepository.getUserId(RegisterType.GUARDIAN, registerCode)).willReturn(Optional.of(wardId));
                given(userRepository.findByIdAndRole(wardId, UserRole.WARD)).willReturn(Optional.of(ward));
                given(relationRepository.existsByWard(ward)).willReturn(true);
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.signup(signupRequest));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.EXIST_GUARDIAN.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Describe: login 메서드는")
    class Describe_with_login {

        KeyPair keyPair = new KeyPair("accessToken", "refreshToken");

        @Nested
        @DisplayName("Context: 올바른 이메일과 비밀번호가 주어지면")
        class Context_with_available_data {
            @BeforeEach
            void setup() {
                loginRequest = new LoginRequest(email, password);
                user = new User(email, encodedPassword, nickname, phoneNumber, UserRole.WARD);
                ReflectionTestUtils.setField(user, "id", userId);
            }

            @Test
            @DisplayName("It : 로그인 성공 및 KeyPair 반환")
            void it_success_login() {
                //given
                given(userRepository.findByEmailAndDeletedAtIsNull(email)).willReturn(Optional.of(user));
                given(passwordEncoder.matches(password, encodedPassword)).willReturn(true);
                given(jwtTokenProvider.issueKeyPair(userId, email, user.getRole())).willReturn(keyPair);
                //when
                KeyPair response = userService.login(loginRequest);

                //then
                assertThat(response).isEqualTo(keyPair);
                verify(refreshTokenRepository).save(userId, keyPair.refreshToken());
            }
        }

        @Nested
        @DisplayName("Context: 존재하지 않는 이메일이 주어지면")
        class Context_with_unavailable_email {
            @BeforeEach
            void setup() {
                loginRequest = new LoginRequest(email, password);
            }

            @Test
            @DisplayName("It : NOT_FOUND_USER 오류 발생")
            void it_throws_not_found_user() {
                //given
                given(userRepository.findByEmailAndDeletedAtIsNull(email)).willReturn(Optional.empty());
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.login(loginRequest));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.NOT_FOUND_USER.getMessage());
            }
        }

        @Nested
        @DisplayName("Context: 비밀번호가 일치하지 않으면")
        class Context_with_not_matching_password {
            @BeforeEach
            void setup() {
                loginRequest = new LoginRequest(email, password);
                user = new User(email, encodedPassword, nickname, phoneNumber, UserRole.WARD);
                ReflectionTestUtils.setField(user, "id", userId);
            }

            @Test
            @DisplayName("It : NOT_MATCH_PASSWORD 오류 발생")
            void it_throws_not_match_password() {
                //given
                given(userRepository.findByEmailAndDeletedAtIsNull(email)).willReturn(Optional.of(user));
                given(passwordEncoder.matches(password, encodedPassword)).willReturn(false);
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.login(loginRequest));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.NOT_MATCH_PASSWORD.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Describe: refreshToken 메서드는")
    class Describe_with_refreshToken {

        String requestToken = "requestRefreshToken";
        TokenBody tokenBody = new TokenBody(userId, email, UserRole.WARD, TokenType.ACCOUNT);
        KeyPair keyPair = new KeyPair("newAccessToken", "newRefreshToken");

        @Nested
        @DisplayName("Context: 올바른 refreshToken이 주어지면")
        class Context_with_available_data {
            @BeforeEach
            void setup() {
                refreshRequest = new RefreshRequest(requestToken);
            }

            @Test
            @DisplayName("It : 토큰 재발급 성공")
            void it_success_refresh_token() {
                //given
                given(jwtTokenProvider.parseJwt(requestToken)).willReturn(tokenBody);
                given(refreshTokenRepository.getToken(userId)).willReturn(Optional.of(requestToken));
                given(jwtTokenProvider.issueKeyPair(userId, email, UserRole.WARD)).willReturn(keyPair);
                //when
                KeyPair response = userService.refreshToken(refreshRequest);

                //then
                assertThat(response).isEqualTo(keyPair);
                verify(refreshTokenRepository).save(userId, keyPair.refreshToken());
            }
        }

        @Nested
        @DisplayName("Context: 저장소에 refreshToken이 존재하지 않으면")
        class Context_with_no_stored_token {
            @BeforeEach
            void setup() {
                refreshRequest = new RefreshRequest(requestToken);
            }

            @Test
            @DisplayName("It : NOT_FOUND_REFRESH 오류 발생")
            void it_throws_not_found_refresh() {
                //given
                given(jwtTokenProvider.parseJwt(requestToken)).willReturn(tokenBody);
                given(refreshTokenRepository.getToken(userId)).willReturn(Optional.empty());
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.refreshToken(refreshRequest));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.NOT_FOUND_REFRESH.getMessage());
            }
        }

        @Nested
        @DisplayName("Context: 저장된 토큰과 요청 토큰이 일치하지 않으면")
        class Context_with_mismatching_token {
            @BeforeEach
            void setup() {
                refreshRequest = new RefreshRequest(requestToken);
            }

            @Test
            @DisplayName("It : INVALID_REFRESH_TOKEN 오류 발생")
            void it_throws_invalid_refresh_token() {
                //given
                given(jwtTokenProvider.parseJwt(requestToken)).willReturn(tokenBody);
                given(refreshTokenRepository.getToken(userId)).willReturn(Optional.of("differentToken"));
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.refreshToken(refreshRequest));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Describe: loadUserByUsername 메서드는")
    class Describe_with_loadUserByUsername {

        @Nested
        @DisplayName("Context: 존재하는 이메일이라면")
        class Context_with_available_email {
            @BeforeEach
            void setup() {
                user = new User(email, encodedPassword, nickname, phoneNumber, UserRole.WARD);
                ReflectionTestUtils.setField(user, "id", userId);
            }

            @Test
            @DisplayName("It : CurrentUser 조회 성공")
            void it_success_load_user() {
                //given
                given(userRepository.findByEmailAndDeletedAtIsNull(email)).willReturn(Optional.of(user));
                //when
                UserDetails response = userService.loadUserByUsername(email);

                //then
                Assertions.assertNotNull(response);
                CurrentUser currentUser = (CurrentUser) response;
                assertThat(currentUser.getId()).isEqualTo(userId);
                assertThat(currentUser.getEmail()).isEqualTo(email);
            }
        }

        @Nested
        @DisplayName("Context: 존재하지 않는 이메일이라면")
        class Context_with_unavailable_email {
            @BeforeEach
            void setup() {
            }

            @Test
            @DisplayName("It : NOT_FOUND_USER 오류 발생")
            void it_throws_not_found_user() {
                //given
                given(userRepository.findByEmailAndDeletedAtIsNull(email)).willReturn(Optional.empty());
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.loadUserByUsername(email));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.NOT_FOUND_USER.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Describe: logout 메서드는")
    class Describe_with_logout {

        CurrentUser currentUser = new CurrentUser(userId, email, UserRole.WARD);

        @Nested
        @DisplayName("Context: 본인 소유의 fid가 함께 주어지면")
        class Context_with_own_fid {

            @Test
            @DisplayName("It : refreshToken과 해당 fid를 삭제한다")
            void it_success_logout() {
                //given
                User owner = mock(User.class);
                given(owner.getId()).willReturn(userId);
                Fid target = mock(Fid.class);
                given(target.getUser()).willReturn(owner);
                given(fidRepository.findByFid(fid)).willReturn(Optional.of(target));

                //when
                userService.logout(new LogoutRequest(fid), currentUser);

                //then
                verify(refreshTokenRepository).delete(userId);
                verify(fidRepository).delete(target);
            }
        }

        @Nested
        @DisplayName("Context: fid 없이 주어지면")
        class Context_without_fid {

            @Test
            @DisplayName("It : refreshToken만 삭제하고 fid는 건드리지 않는다")
            void it_success_logout_without_fid() {
                //when
                userService.logout(null, currentUser);

                //then
                verify(refreshTokenRepository).delete(userId);
                verifyNoInteractions(fidRepository);
            }
        }

        @Nested
        @DisplayName("Context: 등록되지 않은 fid가 주어지면")
        class Context_with_unknown_fid {

            @Test
            @DisplayName("It : 예외 없이 refreshToken만 삭제한다")
            void it_ignores_unknown_fid() {
                //given
                given(fidRepository.findByFid(fid)).willReturn(Optional.empty());

                //when
                userService.logout(new LogoutRequest(fid), currentUser);

                //then
                verify(refreshTokenRepository).delete(userId);
                verify(fidRepository, never()).delete(any(Fid.class));
            }
        }

        @Nested
        @DisplayName("Context: 남의 fid가 주어지면")
        class Context_with_others_fid {

            @Test
            @DisplayName("It : NOT_OWNER 예외를 던진다")
            void it_throws_not_owner() {
                //given
                User other = mock(User.class);
                given(other.getId()).willReturn(wardId);
                Fid target = mock(Fid.class);
                given(target.getUser()).willReturn(other);
                given(fidRepository.findByFid(fid)).willReturn(Optional.of(target));

                //when-then
                assertThatThrownBy(() -> userService.logout(new LogoutRequest(fid), currentUser))
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_OWNER_FID);

                verify(fidRepository, never()).delete(any(Fid.class));
            }
        }
    }

    @Nested
    @DisplayName("Describe: getGuardianRegisterCode 메서드는")
    class Describe_with_getGuardianRegisterCode {

        CurrentUser currentUser = new CurrentUser(userId, email, UserRole.WARD);

        @Nested
        @DisplayName("Context: 인증된 피보호자가 주어지면")
        class Context_with_available_data {

            @Test
            @DisplayName("It : 규격에 맞는 registerCode를 발급하고 저장한 뒤 응답으로 반환한다")
            void it_success_issue_register_code() {
                //given
                given(registerCodeRepository.saveIfAbsent(eq(RegisterType.GUARDIAN), anyString(), eq(userId))).willReturn(true);

                //when
                RegisterResponse response = userService.getGuardianRegisterCode(currentUser);

                //then : 코드가 무작위라 값을 고정할 수 없다. 저장한 값과 응답이 같은지, 규격에 맞는지만 본다
                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(registerCodeRepository).saveIfAbsent(eq(RegisterType.GUARDIAN), captor.capture(), eq(userId));

                assertThat(response.registerCode()).isEqualTo(captor.getValue());
                assertThat(response.registerCode()).matches(registerCodePattern);
            }
        }

        @Nested
        @DisplayName("Context: 발급한 코드가 매번 이미 선점되어 있으면")
        class Context_with_code_collision {

            @Test
            @DisplayName("It : 재시도 상한까지 시도한 뒤 FAILED_ISSUE_REGISTER_CODE 오류 발생")
            void it_throws_failed_issue_register_code() {
                //given
                given(registerCodeRepository.saveIfAbsent(eq(RegisterType.GUARDIAN), anyString(), eq(userId))).willReturn(false);

                //when&then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> userService.getGuardianRegisterCode(currentUser));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.FAILED_ISSUE_REGISTER_CODE.getMessage());
                verify(registerCodeRepository, times(ISSUE_MAX_ATTEMPTS)).saveIfAbsent(eq(RegisterType.GUARDIAN), anyString(), eq(userId));
            }
        }
    }

    @Nested
    @DisplayName("Describe: getUserInfo 메서드는")
    class Describe_with_getUserInfo {

        CurrentUser currentUser = new CurrentUser(userId, email, UserRole.GUARDIAN);
        User guardian;
        Relation relation;

        @BeforeEach
        void setup() {
            guardian = new User(email, encodedPassword, nickname, phoneNumber, UserRole.GUARDIAN);
            ReflectionTestUtils.setField(guardian, "id", userId);
            ward = new User("ward@test.com", encodedPassword, "피보호자", "010-9999-8888", UserRole.WARD);
            ReflectionTestUtils.setField(ward, "id", wardId);
            relation = new Relation(ward, guardian);
        }

        @Nested
        @DisplayName("Context: 보호자와 연결된 피보호자가 모두 존재하면")
        class Context_with_available_data {

            @Test
            @DisplayName("It : 보호자 정보와 피보호자 정보를 함께 반환한다")
            void it_success_get_guardian_info() {
                //given
                given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(guardian));
                given(relationRepository.findByGuardianId(userId)).willReturn(Optional.of(relation));
                given(userRepository.findByIdAndDeletedAtIsNull(wardId)).willReturn(Optional.of(ward));
                //when
                UserResponse response = userService.getUserInfo(currentUser);

                //then
                assertThat(response.id()).isEqualTo(userId);
                assertThat(response.role()).isEqualTo(UserRole.GUARDIAN);
                assertThat(response.nickname()).isEqualTo(nickname);
                assertThat(response.ward().id()).isEqualTo(wardId);
                assertThat(response.ward().nickname()).isEqualTo(ward.getNickname());
                assertThat(response.ward().phoneNumber()).isEqualTo(ward.getPhoneNumber());
            }
        }

        @Nested
        @DisplayName("Context: 보호자 계정이 존재하지 않으면")
        class Context_with_unavailable_guardian {

            @Test
            @DisplayName("It : NOT_FOUND_USER 오류 발생")
            void it_throws_not_found_user() {
                //given
                given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.getUserInfo(currentUser));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.NOT_FOUND_USER.getMessage());
            }
        }

        @Nested
        @DisplayName("Context: 보호자의 관계가 존재하지 않으면")
        class Context_with_no_relation {

            @Test
            @DisplayName("It : NOT_FOUND_RELATION 오류 발생")
            void it_throws_not_found_relation() {
                //given
                given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(guardian));
                given(relationRepository.findByGuardianId(userId)).willReturn(Optional.empty());
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.getUserInfo(currentUser));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.NOT_FOUND_RELATION.getMessage());
            }
        }

        @Nested
        @DisplayName("Context: 연결된 피보호자 계정이 존재하지 않으면")
        class Context_with_unavailable_ward {

            @Test
            @DisplayName("It : NOT_FOUND_USER 오류 발생")
            void it_throws_not_found_user() {
                //given
                given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(guardian));
                given(relationRepository.findByGuardianId(userId)).willReturn(Optional.of(relation));
                given(userRepository.findByIdAndDeletedAtIsNull(wardId)).willReturn(Optional.empty());
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.getUserInfo(currentUser));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.NOT_FOUND_USER.getMessage());
            }
        }

        @Nested
        @DisplayName("Context: 호출자가 WARD면")
        class Context_with_ward_role {

            CurrentUser wardUser = new CurrentUser(wardId, "ward@test.com", UserRole.WARD);

            @Test
            @DisplayName("It : 관계를 조회하지 않고 ward가 null인 본인 정보만 반환한다")
            void it_returns_self_without_ward() {
                //given
                given(userRepository.findByIdAndDeletedAtIsNull(wardId)).willReturn(Optional.of(ward));
                //when
                UserResponse response = userService.getUserInfo(wardUser);

                //then
                assertThat(response.id()).isEqualTo(wardId);
                assertThat(response.role()).isEqualTo(UserRole.WARD);
                assertThat(response.nickname()).isEqualTo(ward.getNickname());
                assertThat(response.ward()).isNull();
                verify(relationRepository, never()).findByGuardianId(anyLong());
            }
        }
    }

    @Nested
    @DisplayName("Describe: getDeviceRegisterCode 메서드는")
    class Describe_with_getDeviceRegisterCode {

        CurrentUser currentUser = new CurrentUser(userId, email, UserRole.WARD);

        @Nested
        @DisplayName("Context: 인증된 피보호자가 주어지면")
        class Context_with_available_data {

            @Test
            @DisplayName("It : DEVICE 타입으로 저장하고 규격에 맞는 registerCode를 반환한다")
            void it_success_issue_device_register_code() {
                //given
                given(registerCodeRepository.saveIfAbsent(eq(RegisterType.DEVICE), anyString(), eq(userId)))
                        .willReturn(true);

                //when
                RegisterResponse response = userService.getDeviceRegisterCode(currentUser);

                //then : 보호자 코드와 발급 로직을 공유한다. 타입이 DEVICE로 넘어가는지가 이 테스트의 핵심이다
                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(registerCodeRepository).saveIfAbsent(eq(RegisterType.DEVICE), captor.capture(), eq(userId));

                assertThat(response.registerCode()).isEqualTo(captor.getValue());
                assertThat(response.registerCode()).matches(registerCodePattern);
                verify(registerCodeRepository, never())
                        .saveIfAbsent(eq(RegisterType.GUARDIAN), anyString(), anyLong());
            }
        }

        @Nested
        @DisplayName("Context: 발급한 코드가 매번 이미 선점되어 있으면")
        class Context_with_device_code_collision {

            @Test
            @DisplayName("It : 재시도 상한까지 시도한 뒤 FAILED_ISSUE_REGISTER_CODE 오류 발생")
            void it_throws_failed_issue_register_code() {
                //given
                given(registerCodeRepository.saveIfAbsent(eq(RegisterType.DEVICE), anyString(), eq(userId)))
                        .willReturn(false);

                //when&then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> userService.getDeviceRegisterCode(currentUser));

                assertThat(exception.getMessage()).isEqualTo(ErrorCode.FAILED_ISSUE_REGISTER_CODE.getMessage());
                verify(registerCodeRepository, times(ISSUE_MAX_ATTEMPTS))
                        .saveIfAbsent(eq(RegisterType.DEVICE), anyString(), eq(userId));
            }
        }
    }

    @Nested
    @DisplayName("Describe: pairingDevice 메서드는")
    class Describe_with_pairingDevice {

        String deviceName = "Meta Quest 3";
        String deviceSerialTail = "7F2C";
        String deviceAccessToken = "device.access.token";
        DeviceRegisterRequest request =
                new DeviceRegisterRequest(registerCode, deviceName, deviceSerialTail);

        @Nested
        @DisplayName("Context: 유효한 기기 등록 코드가 주어지면")
        class Context_with_valid_code {

            @Test
            @DisplayName("It : 코드가 가리키는 피보호자의 기기 토큰을 발급한다")
            void it_success_issue_device_access_token() {
                //given
                ward = new User("ward@test.com", encodedPassword, "피보호자", "010-9999-8888", UserRole.WARD);
                given(registerCodeRepository.getUserId(RegisterType.DEVICE, registerCode))
                        .willReturn(Optional.of(wardId));
                given(userRepository.findByIdAndDeletedAtIsNull(wardId)).willReturn(Optional.of(ward));
                given(jwtTokenProvider.issueDeviceToken(wardId, ward.getEmail(), UserRole.WARD))
                        .willReturn(deviceAccessToken);

                //when
                PairingDeviceResponse response = userService.pairingDevice(request);

                //then : 보호자 저장소가 아니라 DEVICE 저장소를 봐야 한다
                assertThat(response.accessToken()).isEqualTo(deviceAccessToken);
                verify(registerCodeRepository, never())
                        .getUserId(eq(RegisterType.GUARDIAN), anyString());

                // 한 번 쓴 코드는 즉시 폐기된다. 남으면 TTL 동안 다른 기기도 같은 코드로 붙는다
                verify(registerCodeRepository).delete(RegisterType.DEVICE, registerCode);
            }
        }

        @Nested
        @DisplayName("Context: 만료됐거나 존재하지 않는 코드가 주어지면")
        class Context_with_unknown_code {

            @Test
            @DisplayName("It : NOT_FOUND_REGISTER 오류 발생")
            void it_throws_not_found_register() {
                //given
                given(registerCodeRepository.getUserId(RegisterType.DEVICE, registerCode))
                        .willReturn(Optional.empty());

                //when&then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> userService.pairingDevice(request));

                assertThat(exception.getMessage()).isEqualTo(ErrorCode.NOT_FOUND_REGISTER.getMessage());
                verifyNoInteractions(jwtTokenProvider);
                verify(registerCodeRepository, never()).delete(any(RegisterType.class), anyString());
            }
        }

        @Nested
        @DisplayName("Context: 코드는 있지만 피보호자가 탈퇴한 상태이면")
        class Context_with_deleted_ward {

            @Test
            @DisplayName("It : NOT_FOUND_USER 오류 발생")
            void it_throws_not_found_user() {
                //given
                given(registerCodeRepository.getUserId(RegisterType.DEVICE, registerCode))
                        .willReturn(Optional.of(wardId));
                given(userRepository.findByIdAndDeletedAtIsNull(wardId)).willReturn(Optional.empty());

                //when&then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> userService.pairingDevice(request));

                assertThat(exception.getMessage()).isEqualTo(ErrorCode.NOT_FOUND_USER.getMessage());
                verifyNoInteractions(jwtTokenProvider);
                verify(registerCodeRepository, never()).delete(any(RegisterType.class), anyString());
            }
        }
    }

    @Nested
    @DisplayName("Describe: getDeviceAccessToken 메서드는")
    class Describe_with_getDeviceAccessToken {

        String reissuedToken = "reissued.device.token";
        CurrentUser wardUser = new CurrentUser(wardId, "ward@test.com", UserRole.WARD);

        @Nested
        @DisplayName("Context: 로그인한 피보호자가 요청하면")
        class Context_with_authenticated_ward {

            @Test
            @DisplayName("It : 기기(DEVICE) 토큰을 발급한다")
            void it_issues_device_token() {
                //given
                given(jwtTokenProvider.issueDeviceToken(wardId, "ward@test.com", UserRole.WARD))
                        .willReturn(reissuedToken);

                //when
                PairingDeviceResponse response = userService.getDeviceAccessToken(wardUser);

                //then : 페어링 코드를 거치지 않는 재발급 경로다. 등록 코드 저장소를 보지 않는다
                assertThat(response.accessToken()).isEqualTo(reissuedToken);
                verifyNoInteractions(registerCodeRepository);
                verify(jwtTokenProvider, never()).issueAccessToken(anyLong(), anyString(), any(UserRole.class));
            }
        }
    }
}

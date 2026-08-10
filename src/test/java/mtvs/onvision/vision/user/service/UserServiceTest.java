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
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.dto.UserResponse;
import mtvs.onvision.vision.user.dto.RegisterGuardianResponse;
import mtvs.onvision.vision.user.dto.SignupRequest;
import mtvs.onvision.vision.user.repository.FidRepository;
import mtvs.onvision.vision.user.repository.RegisterTokenRepository;
import mtvs.onvision.vision.user.repository.RelationRepository;
import mtvs.onvision.vision.user.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private RegisterTokenRepository registerTokenRepository;

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
    String registerToken = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIyIiwiZW1haWwiOiJ0ZXN0MUBuYXZlci5jb20iLCJyb2xlIjoiR1VBUkRJQU4iLCJpYXQiOjE3ODQ2OTc2OTgsImV4cCI6MTc4NDY5ODU5OH0.JdRlH8l-sMTe9Z7QQQmxtLbgT9qNWWkuabcFkw8cpEWVgPGihH8u1HqLofCr80ejBYGA5hIfY6Buzu9-r5IyQA";

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
        @DisplayName("Context: role이 GUARDIAN이고 유효한 registerToken이 주어지면")
        class Context_with_available_register_token {
            @BeforeEach
            void setup() {
                signupRequest = new SignupRequest(email, password, nickname, phoneNumber, UserRole.GUARDIAN, registerToken);
                ward = new User("ward@test.com", encodedPassword, "피보호자", "010-9999-8888", UserRole.WARD);
                ReflectionTestUtils.setField(ward, "id", wardId);
            }

            @Test
            @DisplayName("It : registerToken으로 wardId를 찾아 Guardian과 Relation 저장 성공")
            void it_success_signup_with_register_token() {
                //given
                given(userRepository.existsByEmail(email)).willReturn(false);
                given(userRepository.existsByPhoneNumber(phoneNumber)).willReturn(false);
                given(jwtTokenProvider.parseId(registerToken)).willReturn(wardId);
                given(registerTokenRepository.getToken(wardId)).willReturn(Optional.of(registerToken));
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
        @DisplayName("Context: role이 GUARDIAN이고 저장소에 registerToken이 존재하지 않으면")
        class Context_with_no_stored_register_token {
            @BeforeEach
            void setup() {
                signupRequest = new SignupRequest(email, password, nickname, phoneNumber, UserRole.GUARDIAN, registerToken);
            }

            @Test
            @DisplayName("It : NOT_FOUND_REGISTER 오류 발생")
            void it_throws_not_found_register() {
                //given
                given(userRepository.existsByEmail(email)).willReturn(false);
                given(userRepository.existsByPhoneNumber(phoneNumber)).willReturn(false);
                given(jwtTokenProvider.parseId(registerToken)).willReturn(wardId);
                given(registerTokenRepository.getToken(wardId)).willReturn(Optional.empty());
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.signup(signupRequest));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.NOT_FOUND_REGISTER.getMessage());
            }
        }

        @Nested
        @DisplayName("Context: role이 GUARDIAN이고 저장된 토큰과 요청 토큰이 일치하지 않으면")
        class Context_with_mismatching_register_token {
            @BeforeEach
            void setup() {
                signupRequest = new SignupRequest(email, password, nickname, phoneNumber, UserRole.GUARDIAN, registerToken);
            }

            @Test
            @DisplayName("It : INVALID_REGISTER_TOKEN 오류 발생")
            void it_throws_invalid_register_token() {
                //given
                given(userRepository.existsByEmail(email)).willReturn(false);
                given(userRepository.existsByPhoneNumber(phoneNumber)).willReturn(false);
                given(jwtTokenProvider.parseId(registerToken)).willReturn(wardId);
                given(registerTokenRepository.getToken(wardId)).willReturn(Optional.of("differentToken"));
                //when&then
                BusinessException exception = assertThrows(BusinessException.class, () -> userService.signup(signupRequest));
                assertThat(exception.getMessage()).isEqualTo(ErrorCode.INVALID_REGISTER_TOKEN.getMessage());
            }
        }

        @Nested
        @DisplayName("Context: role이 GUARDIAN이고 registerToken으로 찾은 피보호자가 존재하지 않으면")
        class Context_with_unavailable_ward_id {
            @BeforeEach
            void setup() {
                signupRequest = new SignupRequest(email, password, nickname, phoneNumber, UserRole.GUARDIAN, registerToken);
            }

            @Test
            @DisplayName("It : NOT_FOUND_WARD 오류 발생")
            void it_throws_not_found_ward() {
                //given
                given(userRepository.existsByEmail(email)).willReturn(false);
                given(userRepository.existsByPhoneNumber(phoneNumber)).willReturn(false);
                given(jwtTokenProvider.parseId(registerToken)).willReturn(wardId);
                given(registerTokenRepository.getToken(wardId)).willReturn(Optional.of(registerToken));
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
                signupRequest = new SignupRequest(email, password, nickname, phoneNumber, UserRole.GUARDIAN, registerToken);
                ward = new User("ward@test.com", encodedPassword, "피보호자", "010-9999-8888", UserRole.WARD);
                ReflectionTestUtils.setField(ward, "id", wardId);
            }

            @Test
            @DisplayName("It : EXIST_GUARDIAN 오류 발생")
            void it_throws_exist_guardian() {
                //given
                given(userRepository.existsByEmail(email)).willReturn(false);
                given(userRepository.existsByPhoneNumber(phoneNumber)).willReturn(false);
                given(jwtTokenProvider.parseId(registerToken)).willReturn(wardId);
                given(registerTokenRepository.getToken(wardId)).willReturn(Optional.of(registerToken));
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
        TokenBody tokenBody = new TokenBody(userId, email, UserRole.WARD);
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
                        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_OWNER);

                verify(fidRepository, never()).delete(any(Fid.class));
            }
        }
    }

    @Nested
    @DisplayName("Describe: getGuardianRegisterToken 메서드는")
    class Describe_with_getGuardianRegisterToken {

        CurrentUser currentUser = new CurrentUser(userId, email, UserRole.WARD);

        @Nested
        @DisplayName("Context: 인증된 피보호자가 주어지면")
        class Context_with_available_data {

            @Test
            @DisplayName("It : registerToken을 발급하고 저장한 뒤 응답으로 반환한다")
            void it_success_issue_register_token() {
                //given
                given(jwtTokenProvider.issueRegisterToken(userId, email, UserRole.WARD)).willReturn(registerToken);
                //when
                RegisterGuardianResponse response = userService.getGuardianRegisterToken(currentUser);

                //then
                assertThat(response.registerToken()).isEqualTo(registerToken);
                verify(registerTokenRepository).save(userId, registerToken);
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
}

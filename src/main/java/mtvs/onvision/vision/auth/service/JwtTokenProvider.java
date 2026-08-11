package mtvs.onvision.vision.auth.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.auth.dto.KeyPair;
import mtvs.onvision.vision.auth.dto.TokenBody;
import mtvs.onvision.vision.common.config.properties.JwtProperties;
import mtvs.onvision.vision.user.domain.UserRole;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenProvider {
    private final JwtProperties jwtProperties;

    //keypair 생성
    public KeyPair issueKeyPair(
            Long id,
            String email,
            UserRole role
    ) {
        return new KeyPair(
                issueAccessToken(id, email, role),
                issueRefreshToken(id, email, role)
        );
    }


    // access & refresh 토큰 만들기
    public String issueAccessToken(Long id, String email, UserRole role) {
        return issue(id, email, role, jwtProperties.getValidations().getAccess());
    }
    public String issueRefreshToken(Long id, String email, UserRole role) {
        return issue(id, email, role, jwtProperties.getValidations().getRefresh());
    }

    //jwt 토큰 만들기
    private String issue(Long id, String email, UserRole role, Long validTime) {
        return Jwts.builder()
                .subject(id.toString())
                .claim("email", email)
                .claim("role",role.name())
                .issuedAt(new Date())
                .expiration(new Date(new Date().getTime() + validTime))
                .signWith(getSecretKey())
                .compact();
    }


    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(
                jwtProperties.getSecrets().getAppKey().getBytes(StandardCharsets.UTF_8)
        );
    }

    //토큰 검사
    public boolean validate(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("Invalid JWT signature, 유효하지 않는 JWT 서명 입니다.");
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token, 만료된 JWT token 입니다.");
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token, 지원되지 않는 JWT 토큰 입니다.");
        } catch (IllegalArgumentException e) {
            log.error("JWT claims is empty, 잘못된 JWT 토큰 입니다.");
        } catch ( Exception e ) {
            log.error("Unexpected error during token validation: {}", e.getMessage());
        }

        return false;
    }

    //토큰 상세값 파싱
    public Jws<Claims> parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token);
    }

    public Date parseExpiration(String token) {
        Jws<Claims> claims = parseClaims(token);
        return claims.getPayload().getExpiration();
    }

    public Long parseId(String token) {
        Jws<Claims> claims = parseClaims(token);
        return Long.parseLong(claims.getPayload().getSubject());
    }

    //토큰 파싱
    public TokenBody parseJwt(String token) {
        Jws<Claims> claims = parseClaims(token);

        String sub =  claims.getPayload().getSubject();
        String email = claims.getPayload().get("email", String.class);
        UserRole role = UserRole.of(
                claims.getPayload().get("role", String.class)
        );
        log.info("User ::: email : {}, role : {}", email, role);
        return new TokenBody(Long.parseLong(sub),email, role);
    }

}

package mtvs.onvision.vision.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.KeyPair;
import mtvs.onvision.vision.common.config.properties.JwtProperties;
import mtvs.onvision.vision.user.domain.UserRole;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

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

}

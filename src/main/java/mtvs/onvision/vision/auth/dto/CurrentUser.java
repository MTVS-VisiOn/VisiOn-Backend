package mtvs.onvision.vision.auth.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.domain.TokenType;
import mtvs.onvision.vision.user.domain.UserRole;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class CurrentUser implements UserDetails {

    private final Long id;
    private final String email;
    private final UserRole role;
    private final TokenType tokenType;

    public CurrentUser(Long id, String email, UserRole role) {
        this(id, email, role, TokenType.ACCOUNT);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
                new SimpleGrantedAuthority(tokenType == TokenType.DEVICE? TokenType.DEVICE.name() : role.name())
        );
    }

    @Override
    public @Nullable String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }
}

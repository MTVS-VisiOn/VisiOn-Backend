package mtvs.onvision.vision.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.auth.dto.TokenBody;
import mtvs.onvision.vision.auth.service.JwtTokenProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
	private final JwtTokenProvider tokenProvider;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		String token = resolveToken(request);

		if (token != null && tokenProvider.validate(token)) {
			TokenBody tokenBody = tokenProvider.parseJwt(token);
			// 토큰이 userId·email·role·type을 다 실고 있어 요청마다 users 를 다시 읽지 않는다
			CurrentUser userDetails = new CurrentUser(tokenBody.userId(), tokenBody.email(), tokenBody.role(), tokenBody.type());
			Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
			SecurityContextHolder.getContext().setAuthentication(authentication);

		}
		//비인증 유저
		filterChain.doFilter(request, response);
	}

	//토큰 헤더에서 가져오기
	private static String resolveToken(HttpServletRequest request) {
		String bearerToken = request.getHeader("Authorization");
		if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
			return bearerToken.substring(7);
		}
		return null;
	}
}

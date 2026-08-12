package mtvs.onvision.vision.user.repository;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.user.domain.RegisterType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RegisterCodeRepository {
    private final StringRedisTemplate redisTemplate;

    @Value("${redis.expired.guardian-register}")
    Long gExpiredTime;
    @Value("${redis.expired.device-register}")
    Long dExpiredTime;

    private final String KEY_PREFIX_GUARDIAN = "registerCode:guardian:";
    private final String KEY_PREFIX_DEVICE = "registerCode:device:";

    public boolean saveIfAbsent(RegisterType type, String code, Long userId) {
        String prefix = type.equals(RegisterType.GUARDIAN) ? KEY_PREFIX_GUARDIAN : KEY_PREFIX_DEVICE;
        Long expiredTime = type.equals(RegisterType.GUARDIAN) ? gExpiredTime : dExpiredTime;
        return Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(prefix + code, userId.toString(), Duration.ofMillis(expiredTime)));
    }
    public Optional<Long> getToken(RegisterType type, String code) {
        String prefix = type.equals(RegisterType.GUARDIAN) ? KEY_PREFIX_GUARDIAN : KEY_PREFIX_DEVICE;
        return Optional.ofNullable(redisTemplate.opsForValue().get(prefix + code)).map(Long::valueOf);
    }

    public void delete(RegisterType type, String code) {
        String prefix = type.equals(RegisterType.GUARDIAN) ? KEY_PREFIX_GUARDIAN : KEY_PREFIX_DEVICE;
        redisTemplate.delete(prefix +code);
    }
}

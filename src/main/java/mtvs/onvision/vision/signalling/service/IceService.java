package mtvs.onvision.vision.signalling.service;

import lombok.RequiredArgsConstructor;
import mtvs.onvision.vision.auth.dto.CurrentUser;
import mtvs.onvision.vision.common.config.properties.IceProperties;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.signalling.dto.IceServer;
import mtvs.onvision.vision.signalling.dto.IceServersResponse;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IceService {

    private static final String HMAC_SHA1 = "HmacSHA1";
    private final IceProperties iceProperties;

    // coturn 의 use-auth-secret(TURN REST API) 규약
    // username   = <만료 unix timestamp>:<userId>
    // credential = base64(HMAC-SHA1(username, static-auth-secret))
    public IceServersResponse getIceServers(CurrentUser currentUser) {
        IceProperties.Turn turn = iceProperties.getTurn();
        long expiry = Instant.now().getEpochSecond() + turn.getTtl();
        String username = expiry + ":" + currentUser.getId();

        //연결 후보들 지정, sturn 해서 안되면 turn 연결(동시 연결 시도 후 되는 것중 host > srflx > relay순으로 연결)
        return new IceServersResponse(List.of(
                IceServer.stun(iceProperties.getStunUrls()),
                new IceServer(turn.getUrls(), username, sign(username, turn.getSecret()))
        ));
    }

    private String sign(String username, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA1));
            return Base64.getEncoder()
                    .encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new BusinessException(ErrorCode.NOT_CREATE_TURN_CREDENTIAL);
        }
    }
}

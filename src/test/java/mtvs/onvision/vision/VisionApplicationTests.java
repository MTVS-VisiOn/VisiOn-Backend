package mtvs.onvision.vision;

import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class VisionApplicationTests {

    // firebase.enabled=false라 FirebaseConfig가 올라오지 않는다.
    // FcmService가 요구하는 FirebaseMessaging만 목으로 채운다
    @MockitoBean
    FirebaseMessaging firebaseMessaging;

    @Test
    void contextLoads() {
    }

}

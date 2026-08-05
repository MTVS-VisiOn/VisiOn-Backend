package mtvs.onvision.vision.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseConfig {
    private final ResourceLoader resourceLoader;
    @Value("${firebase.service-account.path}")
    private String serviceAccountPath;

    @Bean
    public FirebaseApp firebaseApp() {
        // 이미 초기화된 앱이 있으면 재사용한다. initializeApp을 두 번 부르면 IllegalStateException이 난다
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        try {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setCredentials(GoogleCredentials.fromStream(resourceLoader.getResource(serviceAccountPath).getInputStream()))
                    .build();
            return FirebaseApp.initializeApp(options);
        } catch (IOException e) {
            // null을 반환하면 빈이 등록되지 않아 FirebaseMessaging 주입 실패로 번져 원인이 가려진다
            throw new IllegalStateException(
                    "Firebase 초기화 실패. 서비스 계정 키 경로를 확인하세요: " + serviceAccountPath, e);
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp fireBaseApp) {
        return FirebaseMessaging.getInstance(fireBaseApp);
    }
}

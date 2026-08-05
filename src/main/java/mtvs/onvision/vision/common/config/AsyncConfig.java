package mtvs.onvision.vision.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("async 실패: {}", method.getName(), ex);
    }

    @Bean
    public Executor taskExecutor() {
        //스레드 풀 기반 비동기 실행기
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        //기본 스레드 수: 5개
        executor.setCorePoolSize(5);
        //최대 스레드 수 : 10개(큐가 가득 차면 증가)
        executor.setMaxPoolSize(10);
        //대기 큐 크기: 100개(초과 요청 대기열)
        executor.setQueueCapacity(100);
        // 스레드 이름 접두사: 로그에서 스레드 구분 용이
        executor.setThreadNamePrefix("AsyncThread-");
        // 작업 거부 정책: 큐와 풀이 가득 찼을 때
        executor.setRejectedExecutionHandler((r, e) -> {
            log.error("작업 거부: {}, 풀 상태: {}", r, e.getActiveCount());
        });
        // 초기화: 설정 적용 및 실행 준비
        executor.initialize();

        return executor; // Spring 컨텍스트에 등록
    }
}

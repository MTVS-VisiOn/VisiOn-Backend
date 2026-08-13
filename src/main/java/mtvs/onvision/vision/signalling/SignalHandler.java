package mtvs.onvision.vision.signalling;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mtvs.onvision.vision.common.exception.BusinessException;
import mtvs.onvision.vision.common.exception.ErrorCode;
import mtvs.onvision.vision.common.util.PreConditions;
import mtvs.onvision.vision.signalling.event.GuardianEntered;
import mtvs.onvision.vision.user.domain.Relation;
import mtvs.onvision.vision.user.domain.UserRole;
import mtvs.onvision.vision.user.repository.RelationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@RequiredArgsConstructor
public class SignalHandler extends TextWebSocketHandler {

    private final RelationRepository relationRepository;
    private final ApplicationEventPublisher eventPublisher;
    //어떤 방에 어떤 세션아이디가 들어있는지 저장 -> {roomId : [{userId: sessionId},..] ,...}
    private final Map<Long, List<Map<Long, String>>> roomInfo = new ConcurrentHashMap<>();
    //세션아이디 기준 어떤 방에 들어있는지 저장 -> { userUUID1 : 방번호, userUUID2 : 방번호, ... }
    private final Map<String, Long> userInfo = new ConcurrentHashMap<>();

    // relationId → 예약된 삭제 작업 (취소하려고 보관)
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<Long, ScheduledFuture<?>> pendingCleanup = new ConcurrentHashMap<>();

    private static final long GRACE_SECONDS = 60;

    //터널(cloudflared ~100초)과 이동통신사 NAT(5~30분)가 유휴 WS 를 끊는 것을 막는다.
    //프로덕션 nginx 는 3600초라 여기서는 여유가 있다.
    private static final long PING_INTERVAL_SECONDS = 30;

    // 방의 최대 인원수
    private static final int MAXIMUM = 2;

    //세션 정보 저장-> { sessionId1 : 세션객체, sessionId2 : 세션객체, ... }
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    //웹소켓 연결시
    @Override
    public void afterConnectionEstablished(WebSocketSession session){
        log.info(">>> [ws] 클라이언트 접속 : 사용자 - {}, 세션 - {}", session.getAttributes().get("userId"), session.getId());
    }

    //빈이 만들어진 직후 시작됨
    //보호자가 방열고 일정시간 이상 기다리면 프록시가 끊고 방이 끊기는 것을 막음
    //브라우저 RN이 프로토콜 레벨에서 Pong을 돌려주므로 클라이언트 변경이 0이다.
    //scheduleAtFixedRate() : 예외가 밖으로 새면 scheduleAtFixedRate 는 반복을 영구 취소한다(로그도 없음).
    //그래서 pingAll 안에서 세션 단위로 잡는다.
    @PostConstruct
    public void startKeepAlive() {
        scheduler.scheduleAtFixedRate(this::pingAll,
                PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    //컨테이너가 빈을 버리기 직전 스케줄러 종료
    @PreDestroy
    public void stopScheduler() {
        scheduler.shutdownNow();
    }


    private void pingAll() {
        for (WebSocketSession session : sessions.values()) {
            if(!session.isOpen()) continue;
            try {
                session.sendMessage(new PingMessage());
            }catch (Exception e) {
                log.info(">>> [ws] ping 실패, 세션 {} : {}", session.getId(), e.getMessage());
            }
        }
    }

    //양방향 데이터 통신

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        try {

            //todo : 마지막 heartbeat로 연결 여부 판별 및 FCM 푸시로 연결한다고 가정하고 보내기
            //웹소켓으로 전달받은 메세지를 역직렬화
            SessionMessage message = WebSocketUtils.getObject(textMessage.getPayload());
            String sessionId = session.getId();
            Long userId = (Long)session.getAttributes().get("userId");
            UserRole userRole = (UserRole)session.getAttributes().get("role");
            Long roomId;
            log.info(">>> [ws] 메시지 타입 {}, 보낸 사람 {}, 세션 아이디 {}", message.getType(), userId, sessionId);


            //메시지 타입에 다라 서비의 역할이 달라짐
            switch (message.getType()) {

                //보호자 또는 피보호자가 들어옴(보호자가 방을 만들고 피보호자가 offer를 보냄)
                case JOIN_ROOM:
                    //세션에 roomId 추가
                    Relation relation;
                    if (userRole == UserRole.WARD)
                        relation = relationRepository.findByWardId(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_RELATION));
                    else
                        relation = relationRepository.findByGuardianId(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_GUARDIAN));
                    roomId = relation.getId();
                    //roomId를 세션에 저장
                    session.getAttributes().put("roomId", roomId);

                    //피보호자라면
                    if (userRole == UserRole.WARD) {
                        //이미 보호자가 방을 열어뒀음 -> 방이 없으면 오류
                        PreConditions.check(!roomInfo.containsKey(roomId), ErrorCode.NOT_FOUND_ROOM);
                        //방에 2명 이상 있다고 하면 오류
                        PreConditions.check(roomInfo.get(roomId).size() >= MAXIMUM, ErrorCode.MAX_CONNECT);
                        // 여분의 자리가 있다면 해당 방 배열에 추가
                        Map<Long, String> userDetail = new HashMap<>();
                        userDetail.put(userId, sessionId);
                        roomInfo.get(roomId).add(userDetail);
                        log.info(">>> [ws] #{}번 방의 세션들 {}", roomId, roomInfo.get(roomId));

                    // 보호자이면 방을 열어야 한다
                    } else {
                        // 1분 안에 돌아온다면 삭제 예약 취소
                        ScheduledFuture<?> pending = pendingCleanup.remove(roomId);
                        if (pending != null) {
                            pending.cancel(false);
                            log.info(">>> {} 재연결, 방 {} 삭제 취소", userId, roomId);
                        }

                        Map<Long, String> userDetail = new HashMap<>();
                        userDetail.put(userId, sessionId);

                        if (roomInfo.containsKey(roomId)) {
                            // 재연결: 기존 보호자 entry만 교체, 피보호자는 유지
                            // entry 만 지우면 닫힌 옛 세션이 sessions/userInfo 에 영구히 남는다.
                            // OFFER/ANSWER 는 sessions 전체를 순회하므로 재접속마다 비용이 늘어난다.
                            for (Map<Long, String> old : roomInfo.get(roomId)) {
                                String oldSessionId = old.get(userId);
                                if (oldSessionId != null && !oldSessionId.equals(sessionId)) {
                                    sessions.remove(oldSessionId);
                                    userInfo.remove(oldSessionId);
                                }
                            }
                            roomInfo.get(roomId).removeIf(m -> m.containsKey(userId));
                            roomInfo.get(roomId).add(userDetail);
                            log.info(">>> [ws] 보호자 {} 재연결, 세션 교체", userId);
                        } else {
                            // 최초 개설
                            List<Map<Long, String>> newRoom = new ArrayList<>();
                            newRoom.add(userDetail);
                            roomInfo.put(roomId, newRoom);
                            // 방이 열렸으니 피보호자에게 붙으라고 알린다.
                            // 앱이 먼저 붙으면 방이 없어 NOT_FOUND_ROOM 이므로 순서를 푸시로 강제한다
                            eventPublisher.publishEvent(new GuardianEntered(relation.getWard().getId(), Instant.now()));
                        }
                    }

                    //세션 저장, user 정보 저장 -> 방 입장
                    sessions.put(sessionId, session);
                    userInfo.put(sessionId, roomId);
                    // 방에 상대가 있으면 서로에게 상대 sessionId를 알려줌
                    for (Map<Long, String> entry : roomInfo.get(roomId)) {
                        String peerSessionId = entry.values().iterator().next();
                        if (peerSessionId.equals(sessionId)) continue;   // 나 자신은 건너뜀

                        // 방금 들어온 나에게 → 상대 sessionId 전달
                        session.sendMessage(new TextMessage(WebSocketUtils.getString(
                                SessionMessage.builder()
                                        .type(MessageType.MATCH)
                                        .receiver(peerSessionId)   // 앞으로 이 값으로 offer/answer 보내면 됨
                                        .build())));

                        //기존 상대에게 → 내가 들어왔다 + 내 sessionId 전달
                        WebSocketSession peer = sessions.get(peerSessionId);
                        if (peer != null && peer.isOpen()) {
                            peer.sendMessage(new TextMessage(WebSocketUtils.getString(
                                    SessionMessage.builder()
                                            .type(MessageType.MATCH)
                                            .receiver(sessionId)
                                            .build())));
                        }
                    }
                    break;
                // 클라이언트에게서 받은 멤세지 타입에 따른 signal 프로세스
                case OFFER:
                case ANSWER:
                case CANDIDATE:
                    String receiver = message.getReceiver();
                    // sessions 는 sessionId 로 키를 잡으므로 전체 순회할 이유가 없다.
                    // 기존 코드는 receiver 가 어디에도 없으면 아무 로그 없이 사라져 "보냈는데 안 왔다"를 가릴 수 없었다.
                    WebSocketSession target = sessions.get(receiver);
                    if (target == null || !target.isOpen()) {
                        log.warn(">>> [ws] {} 전달 실패 : receiver {} 세션 없음/닫힘", message.getType(), receiver);
                        break;
                    }
                    try {
                        target.sendMessage(new TextMessage(WebSocketUtils.getString(
                                SessionMessage.builder()
                                        .type(message.getType())
                                        .sdp(message.getSdp())
                                        .candidate(message.getCandidate())
                                        .sender(sessionId)
                                        .receiver(receiver)
                                        .build())));
                    } catch (Exception e) {
                        log.warn(">>> [ws] {} 전달 실패 : {} -> {}", message.getType(), sessionId, receiver, e);
                    }
                    break;
                    //사용자가 앱을 종료시킬 경우
                case USER_EXIT:
                    //피보호자는 불가능
                    if (userRole == UserRole.WARD) {
                        throw new BusinessException(ErrorCode.FORBIDDEN_WARD);
                    }
                //보호자면 의도적으로 끊은 것을 표시
                    session.getAttributes().put("intentional", true);
                    break;
                //메세지 타입이 잘 못되었을 경우
                default:
                    log.info(">>> [ws] 잘못된 메시지 타입 {}", message.getType());
            }
        } catch (BusinessException e) {
            // 여기서 안 잡으면 ExceptionWebSocketHandlerDecorator 가 세션을 1011 로 닫는다.
            // 클라이언트는 이유를 못 받고 서버 로그에도 ErrorCode 가 안 남는다.
            // ward 가 보호자보다 먼저 붙는 경우(NOT_FOUND_ROOM)가 이 경로다.
            log.warn(">>> [ws] 메시지 처리 실패 : 세션 - {}, 코드 - {}, 사유 - {}",
                    session.getId(), e.getErrorCode(), e.getMessage());
        } catch (IOException e) {
            log.info(">>> 에러 발생 : 양방향 데이터 통신 실패 {}", e.getMessage());
        }

    }


    //소켓 연결 종료

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        UserRole role = (UserRole)session.getAttributes().get("role");
        log.info(">>> [ws] 클라이언트 접속 해제 : 세션 - {}, role- {}, 상태코드 - {}", session, role, status.getCode());

        //userId와 roomId 저장
        String sessionId = session.getId();
        Long userId = (Long)session.getAttributes().get("userId");
        Long roomId = userInfo.get(sessionId);

        // 방이 없으면(JOIN 전 끊김 or 이미 삭제됨) 세션 흔적만 정리하고 종료
        if (roomId == null || !roomInfo.containsKey(roomId)) {
            sessions.remove(sessionId);
            userInfo.remove(sessionId);
            log.info(">>> [ws] 방 없이 종료된 세션 {} 정리", sessionId);
            return;
        }

        //상대방 정보 찾기
        Long otherUserId;
        String otherSessionId = "";
        for (Map<Long, String> entry : roomInfo.get(roomId)) {
            Long uid = entry.keySet().iterator().next();
            if (!uid.equals(userId)) {          // 나 아닌 사람
                otherUserId = uid;                        // 상대 userId
                otherSessionId = entry.get(uid);        // 상대 sessionId
            }
        }

        //intentional이 true면 보호자의 의도적 끊음 -> 피보호자에게 알린 후 방 자체를 삭제
        boolean intentional = Boolean.TRUE.equals(session.getAttributes().get("intentional"));
        if (intentional) {
            deleteRoom(otherSessionId, sessionId, roomId, MessageType.OTHER_EXIT);
            return;
        }

        //의도치 않게 끊김
        //보호자일경우 1분후에도 안오면 다시 연결할 의사가 없다고 판별 -> 방 지움
        if (role == UserRole.GUARDIAN) {
            // 예상치 못한 끊김 → GRACE_SECONDS 뒤 삭제 예약
            String finalOtherSessionId = otherSessionId;
            ScheduledFuture<?> future = scheduler.schedule(() -> {
                try {
                    deleteRoom(finalOtherSessionId, sessionId, roomId, MessageType.DISCONNECT);
                } catch (Exception e) {
                    throw new BusinessException(ErrorCode.NOT_DELETE_ROOM);
                }
                pendingCleanup.remove(roomId);
                log.info(">>> grace 만료, 방 {} 삭제", roomId);
            }, GRACE_SECONDS, TimeUnit.SECONDS);

            pendingCleanup.put(roomId, future);
        }
        else {
            //피보호자라면 보호자에게 알린 후 방은 남기고 피보호자의 세션만 삭제
            // 보호자에게 연결 끊김을 알림 (상대가 있을 때만)
            WebSocketSession other = sessions.get(otherSessionId);
            if (other != null && other.isOpen()) {
                other.sendMessage(new TextMessage(WebSocketUtils.getString(SessionMessage.builder()
                        .type(MessageType.DISCONNECT)
                        .sender(sessionId).build())));
            }
            // 연결이 종료되면 sessions 와 userInfo 에서 해당 유저 삭제
            sessions.remove(sessionId);
            userInfo.remove(sessionId);
            // 해당하는 방의 value 인 user list에서 userId의 값을 지움
            roomInfo.get(roomId).removeIf(m -> m.containsKey(userId));
            log.info(">>> [ws] #{}번 방에서 {} 사용자의 세션 {} 삭제 완료", roomId, userId, sessionId);
            log.info(">>> [ws] #{}번 방에 남은 유저 {}", roomId, roomInfo.get(roomId));
        }


    }

    //소켓 통신 예러
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception){
        // getMessage() 만 찍으면 EOFException 처럼 메시지가 없는 예외가 "null" 로만 남아 원인이 사라진다.
        // 마지막 인자로 넘긴 Throwable 은 SLF4J 가 스택트레이스로 출력한다.
        log.warn(">>> [ws] 소켓 통신 에러 : 세션 - {}, 사용자 - {}",
                session.getId(), session.getAttributes().get("userId"), exception);
    }

    private void deleteRoom(String otherSessionId, String sessionId, Long roomId, MessageType messageType) throws Exception {
        // 피보호자측에 알림
        WebSocketSession other = sessions.get(otherSessionId);
        if (other != null && other.isOpen()) {
            sessions.get(otherSessionId).sendMessage(new TextMessage(WebSocketUtils.getString(SessionMessage.builder()
                    .type(messageType)
                    .sender(otherSessionId).build())));
        }
        //방 자체를 삭제하고 둘다 연결 끊음
        sessions.remove(sessionId);
        sessions.remove(otherSessionId);
        userInfo.remove(sessionId);
        userInfo.remove(otherSessionId);
        roomInfo.remove(roomId);
        log.info(">>> grace 만료, 방 {} 삭제", roomId);
    }
}

package mtvs.onvision.vision.navigation.dto;

/**
 * 티맵에 보낸 출발 좌표가 어디서 왔는지.
 *
 * <p>클라이언트가 「위치 확인 중」을 띄울지, 안내를 그대로 믿을지 판단하는 근거다.
 * 응답에 함께 실어 보낸다.
 */
public enum StartSource {
    /** 경로 요청에 실려 온 좌표. 모바일이 방금 측정해 BLE로 중계한 값이다 */
    REQUEST,
    /** 요청 좌표가 문턱을 못 넘어 서버에 저장된 최신 위치로 폴백한 것 */
    SERVER_CACHE
}

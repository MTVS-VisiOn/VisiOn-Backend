package mtvs.onvision.vision.common.util;

public final class GeoUtils {
    private static final double EARTH_RADIUS_M = 6_371_000;

    private GeoUtils() {}

    /**
     * 한국 안의 쓸 만한 좌표인지. null·0.0·범위 밖을 한꺼번에 거른다.
     *
     * 티맵이 값 없음을 `0.0`으로 채워 보내는 자리가 있어(`nearPoiX`/`nearPoiY` 실측)
     * null 검사만으로는 부족하다. 위도 33~38 · 경도 124~132는 한국 범위이며
     * 자릿수가 겹치지 않아 위경도가 뒤집혀 들어와도 여기서 걸린다.
     */
    public static boolean isInKorea(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) return false;
        return latitude >= 33 && latitude <= 38 && longitude >= 124 && longitude <= 132;
    }

    /** 두 좌표 사이의 직선 거리(m) */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}

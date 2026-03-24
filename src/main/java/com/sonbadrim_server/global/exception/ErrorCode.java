package com.sonbadrim_server.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Global
    INVALID_INPUT_VALUE(400, "GLOBAL_400_1", "입력값이 유효하지 않습니다."),
    METHOD_NOT_ALLOWED(405, "GLOBAL_400_2", "지원하지 않는 HTTP 메서드입니다."),
    BAD_REQUEST(400, "GLOBAL_400_3", "필수 파라미터가 누락되었습니다."),
    INTERNAL_SERVER_ERROR(500, "GLOBAL_500_1", "서버 내부 오류가 발생했습니다."),
    EXTERNAL_API_ERROR(503, "GLOBAL_503_1", "외부 API 서버 호출 중 오류가 발생했습니다."),
    UNAUTHORIZED(401, "GLOBAL_401_1", "인증되지 않은 사용자입니다."),

    // User
    USER_NOT_FOUND(404, "USER_404_1", "유저를 찾을 수 없습니다."),
    USER_WITHDRAWN(403, "USER_403_1", "탈퇴한 사용자입니다."),
    KAKAO_AUTH_FAILED(401, "USER_401_1", "카카오 인가 코드가 유효하지 않습니다."),
    ALREADY_SIGNED_UP_USER(400, "USER_409_1", "이미 회원가입 된 사용자입니다."),
    KAKAO_LOGIN_FAILED(502, "USER_502_1", "카카오 로그인 처리 중 오류가 발생했습니다."),
    KAKAO_API_ERROR(502, "USER_502_2", "카카오 API 응답에 실패했습니다."),

    // Region
    REGION_NOT_FOUND(404, "Region_404_1", "지역을 찾을 수 없습니다.");

    private final int status;
    private final String code;
    private final String message;
}

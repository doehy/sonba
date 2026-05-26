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
    FORBIDDEN(403, "GLOBAL_403_1", "허용되지 않은 요청입니다."),

    // NORMALIZE
    INVALID_PHONE_NUMBER(400, "NORMALIZE_400_1", "유효하지 않은 전화번호 형식입니다."),

    //Category
    INVALID_ENGINEER_CATEGORY(400, "ENGINEER_400_1", "유효하지 않은 시공분야가 포함되어 있습니다."),


    // User
    USER_NOT_FOUND(404, "USER_404_1", "유저를 찾을 수 없습니다."),
    USER_WITHDRAWN(403, "USER_403_1", "탈퇴한 사용자입니다."),
    SIGNUP_FORBIDDEN(403, "USER_403_2", "회원가입 대상 사용자가 아닙니다."),
    KAKAO_AUTH_FAILED(401, "USER_401_1", "카카오 인가 코드가 유효하지 않습니다."),
    ALREADY_SIGNED_UP_USER(400, "USER_409_1", "이미 회원가입 된 사용자입니다."),
    KAKAO_LOGIN_FAILED(502, "USER_502_1", "카카오 로그인 처리 중 오류가 발생했습니다."),
    KAKAO_API_ERROR(502, "USER_502_2", "카카오 API 응답에 실패했습니다."),

    // Region
    REGION_NOT_FOUND(404, "Region_404_1", "지역을 찾을 수 없습니다."),

    // Engineer
    ENGINEER_NOT_FOUND(404, "ENGINEER_404_1", "기술자를 찾을 수 없습니다."),
    ENGINEER_ALREADY_REGISTERED(409, "ENGINEER_409_1", "이미 기술자 등록이 완료된 사용자입니다."),
    CANNOT_MODIFY_ENGINEER(403,"ENGINEER_403_1", "기술자 정보를 수정할 권한이 없습니다."),
    CANNOT_UNJOIN_ENGINEER(403, "ENGINEER_403_2", "기술자 탈퇴할 권한이 없습니다."),

    // Inquiry
    INQUIRY_NOT_FOUND(404, "INQUIRY_404_1", "문의방을 찾을 수 없습니다."),
    INQUIRY_ALREADY_EXISTS(409, "INQUIRY_409_1", "이미 문의방이 존재합니다."),
    INQUIRY_FORBIDDEN(403, "INQUIRY_403_1", "해당 문의방에 접근할 권한이 없습니다."),
    INVALID_INQUIRY_REQUEST(400, "INQUIRY_400_1", "유효하지 않은 문의 요청입니다."),
    INQUIRY_MESSAGE_TOO_LONG(400, "INQUIRY_400_2", "문의 메시지는 500자 이하만 가능합니다."),

    // Notification
    NOTIFICATION_NOT_FOUND(404, "NOTIFICATION_404_1", "알림을 찾을 수 없습니다."),

    // Image
    INVALID_PROFILE_IMAGE(400, "IMAGE_400_1", "유효하지 않은 프로필 이미지입니다."),
    INVALID_PORTFOLIO_IMAGE(400, "IMAGE_400_2", "유효하지 않은 포트폴리오 이미지입니다."),

    // Image
    INVALID_IMAGE_FOLDER_TYPE(400, "IMAGE_400_1", "유효하지 않은 이미지 폴더 타입입니다."),
    INVALID_IMAGE_CONTENT_TYPE(400, "IMAGE_400_2", "지원하지 않는 이미지 형식입니다."),
    INVALID_IMAGE_SIZE(400, "IMAGE_400_3", "이미지 크기가 유효하지 않습니다."),
    IMAGE_SIZE_EXCEEDED(400, "IMAGE_400_4", "이미지 크기는 10MB 이하여야 합니다.");

    private final int status;
    private final String code;
    private final String message;
}

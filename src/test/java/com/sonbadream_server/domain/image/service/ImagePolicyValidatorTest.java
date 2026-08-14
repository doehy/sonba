package com.sonbadream_server.domain.image.service;

import com.sonbadream_server.global.exception.CustomException;
import com.sonbadream_server.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImagePolicyValidatorTest {

    private S3Client s3Client;
    private ImagePolicyValidator imagePolicyValidator;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        imagePolicyValidator = new ImagePolicyValidator(s3Client);
        ReflectionTestUtils.setField(imagePolicyValidator, "bucketName", "sonbadream-image");
        ReflectionTestUtils.setField(imagePolicyValidator, "publicDomain", "https://image.test");
    }

    @Test
    @DisplayName("허용된 이미지 확장자와 contentType이 일치하면 확장자를 반환한다")
    void validateUploadFile_success() {
        // when
        String extension = imagePolicyValidator.validateUploadFile("profile.JPG", "image/jpeg");

        // then
        assertThat(extension).isEqualTo(".jpg");
    }

    @Test
    @DisplayName("이미지 확장자가 아니면 업로드 파일 검증에 실패한다")
    void validateUploadFile_invalidExtension() {
        // when & then
        assertThatThrownBy(() -> imagePolicyValidator.validateUploadFile("profile.html", "image/png"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_IMAGE_CONTENT_TYPE);
    }

    @Test
    @DisplayName("확장자와 contentType이 맞지 않으면 업로드 파일 검증에 실패한다")
    void validateUploadFile_mismatchedContentType() {
        // when & then
        assertThatThrownBy(() -> imagePolicyValidator.validateUploadFile("profile.png", "image/jpeg"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_IMAGE_CONTENT_TYPE);
    }

    @Test
    @DisplayName("우리 서비스 engineer 이미지 URL이면 저장을 허용한다")
    void validatePortfolioImageUrl_success() {
        // given
        mockImageHeader(webpHeader());

        // when
        String imageUrl = imagePolicyValidator.validatePortfolioImageUrl(
                " https://image.test/engineer/profile.webp "
        );

        // then
        assertThat(imageUrl).isEqualTo("https://image.test/engineer/profile.webp");
    }

    @Test
    @DisplayName("외부 URL은 포트폴리오 이미지로 저장할 수 없다")
    void validatePortfolioImageUrl_rejectExternalUrl() {
        // when & then
        assertThatThrownBy(() -> imagePolicyValidator.validatePortfolioImageUrl("https://evil.example.com/a.png"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PORTFOLIO_IMAGE);
    }

    @Test
    @DisplayName("R2 endpoint URL은 포트폴리오 이미지로 저장할 수 없다")
    void validatePortfolioImageUrl_rejectEndpointUrl() {
        // when & then
        assertThatThrownBy(() -> imagePolicyValidator.validatePortfolioImageUrl(
                        "https://r2.test/sonbadream-image/engineer/profile.png"
                ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PORTFOLIO_IMAGE);
    }

    @Test
    @DisplayName("확장자가 이미지여도 실제 파일 내용이 이미지가 아니면 저장할 수 없다")
    void validatePortfolioImageUrl_rejectInvalidMagicNumber() {
        // given
        mockImageHeader("not image".getBytes());

        // when & then
        assertThatThrownBy(() -> imagePolicyValidator.validatePortfolioImageUrl(
                        "https://image.test/engineer/profile.png"
                ))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PORTFOLIO_IMAGE);
    }

    @Test
    @DisplayName("프로필 이미지는 비어 있으면 null로 정규화한다")
    void validateProfileImageUrl_blankToNull() {
        // when
        String imageUrl = imagePolicyValidator.validateProfileImageUrl(" ");

        // then
        assertThat(imageUrl).isNull();
    }

    @Test
    @DisplayName("외부 URL은 프로필 이미지로 저장할 수 없다")
    void validateProfileImageUrl_rejectExternalUrl() {
        // when & then
        assertThatThrownBy(() -> imagePolicyValidator.validateProfileImageUrl("https://evil.example.com/a.png"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROFILE_IMAGE);
    }

    @Test
    @DisplayName("우리 서비스 프로필 이미지 URL이고 실제 파일 내용이 JPEG이면 저장을 허용한다")
    void validateProfileImageUrl_success() {
        // given
        mockImageHeader(jpegHeader());

        // when
        String imageUrl = imagePolicyValidator.validateProfileImageUrl(
                "https://image.test/engineer/profile.jpg"
        );

        // then
        assertThat(imageUrl).isEqualTo("https://image.test/engineer/profile.jpg");
    }

    private void mockImageHeader(byte[] header) {
        when(s3Client.getObject(
                any(GetObjectRequest.class),
                any(ResponseTransformer.class)
        )).thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), header));
    }

    private byte[] jpegHeader() {
        return new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00
        };
    }

    private byte[] webpHeader() {
        return new byte[] {
                0x52, 0x49, 0x46, 0x46,
                0x00, 0x00, 0x00, 0x00,
                0x57, 0x45, 0x42, 0x50
        };
    }
}

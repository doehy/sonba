package com.sonbadrim_server.domain.image.service;

import com.sonbadrim_server.domain.engineer.repository.EngineerRepository;
import com.sonbadrim_server.domain.portfolio.repository.PortfolioRepository;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageResizeServiceTest {

    private static final String PROFILE_IMAGE_URL = "https://image.test/engineer/profile.jpg";

    private S3Client s3Client;
    private EngineerRepository engineerRepository;
    private PortfolioRepository portfolioRepository;
    private ImageResizeService imageResizeService;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        engineerRepository = mock(EngineerRepository.class);
        portfolioRepository = mock(PortfolioRepository.class);
        imageResizeService = new ImageResizeService(s3Client, engineerRepository, portfolioRepository);
        ReflectionTestUtils.setField(imageResizeService, "bucketName", "sonbadream-image");
        ReflectionTestUtils.setField(imageResizeService, "publicDomain", "https://image.test");
    }

    @Test
    @DisplayName("프로필 원본 이미지를 리사이징해서 썸네일로 업로드하고 DB에 반영한다")
    void createProfileThumbnail_success() throws Exception {
        // given
        when(s3Client.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(),
                        jpegBytes(1200, 800)
                ));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // when
        imageResizeService.createProfileThumbnail(1L, PROFILE_IMAGE_URL);

        // then
        ArgumentCaptor<GetObjectRequest> getRequestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(getRequestCaptor.capture(), any(ResponseTransformer.class));
        assertThat(getRequestCaptor.getValue().bucket()).isEqualTo("sonbadream-image");
        assertThat(getRequestCaptor.getValue().key()).isEqualTo("engineer/profile.jpg");

        ArgumentCaptor<PutObjectRequest> putRequestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(putRequestCaptor.capture(), any(RequestBody.class));
        assertThat(putRequestCaptor.getValue().bucket()).isEqualTo("sonbadream-image");
        assertThat(putRequestCaptor.getValue().key()).isEqualTo("engineer/thumbnail/profile.jpg");
        assertThat(putRequestCaptor.getValue().contentType()).isEqualTo("image/jpeg");

        verify(engineerRepository).updateProfileThumbnailImageIfProfileUnchanged(
                1L,
                PROFILE_IMAGE_URL,
                "https://image.test/engineer/thumbnail/profile.jpg"
        );
    }

    @Test
    @DisplayName("지원하지 않는 확장자면 썸네일을 만들지 않는다")
    void createProfileThumbnail_unsupportedExtension() {
        // when
        imageResizeService.createProfileThumbnail(1L, "https://image.test/engineer/profile.gif");

        // then
        verify(s3Client, never()).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(engineerRepository, never()).updateProfileThumbnailImageIfProfileUnchanged(any(), any(), any());
    }

    @Test
    @DisplayName("PNG 원본도 JPG 썸네일로 통일해서 저장한다")
    void createProfileThumbnail_pngOriginalCreatesJpgThumbnail() throws Exception {
        // given
        when(s3Client.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(),
                        pngBytes(1200, 800)
                ));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // when
        imageResizeService.createProfileThumbnail(1L, "https://image.test/engineer/profile.png");

        // then
        ArgumentCaptor<PutObjectRequest> putRequestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(putRequestCaptor.capture(), any(RequestBody.class));
        assertThat(putRequestCaptor.getValue().key()).isEqualTo("engineer/thumbnail/profile.jpg");
        assertThat(putRequestCaptor.getValue().contentType()).isEqualTo("image/jpeg");

        verify(engineerRepository).updateProfileThumbnailImageIfProfileUnchanged(
                1L,
                "https://image.test/engineer/profile.png",
                "https://image.test/engineer/thumbnail/profile.jpg"
        );
    }

    @Test
    @DisplayName("포트폴리오 원본 이미지를 리사이징해서 썸네일로 업로드하고 DB에 반영한다")
    void createPortfolioThumbnail_success() throws Exception {
        // given
        String portfolioImageUrl = "https://image.test/engineer/portfolio.jpg";
        when(s3Client.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(),
                        jpegBytes(1600, 900)
                ));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        // when
        imageResizeService.createPortfolioThumbnail(10L, portfolioImageUrl);

        // then
        ArgumentCaptor<GetObjectRequest> getRequestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(getRequestCaptor.capture(), any(ResponseTransformer.class));
        assertThat(getRequestCaptor.getValue().bucket()).isEqualTo("sonbadream-image");
        assertThat(getRequestCaptor.getValue().key()).isEqualTo("engineer/portfolio.jpg");

        ArgumentCaptor<PutObjectRequest> putRequestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(putRequestCaptor.capture(), any(RequestBody.class));
        assertThat(putRequestCaptor.getValue().bucket()).isEqualTo("sonbadream-image");
        assertThat(putRequestCaptor.getValue().key()).isEqualTo("engineer/portfolio-thumbnail/portfolio.jpg");
        assertThat(putRequestCaptor.getValue().contentType()).isEqualTo("image/jpeg");

        verify(portfolioRepository).updatePortfolioThumbnailImageIfPortfolioUnchanged(
                10L,
                portfolioImageUrl,
                "https://image.test/engineer/portfolio-thumbnail/portfolio.jpg"
        );
    }

    private byte[] jpegBytes(int width, int height) throws Exception {
        return imageBytes(width, height, "jpg");
    }

    private byte[] pngBytes(int width, int height) throws Exception {
        return imageBytes(width, height, "png");
    }

    private byte[] imageBytes(int width, int height, String formatName) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, formatName, outputStream);
        return outputStream.toByteArray();
    }
}

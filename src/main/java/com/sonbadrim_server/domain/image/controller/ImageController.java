package com.sonbadrim_server.domain.image.controller;

import com.sonbadrim_server.domain.image.dto.request.PresignedUrlRequestDto;
import com.sonbadrim_server.domain.image.dto.response.PresignedUrlResponseDto;
import com.sonbadrim_server.domain.image.service.ImageService;
import com.sonbadrim_server.global.common.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Image", description = "기술자 이미지 업로드 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/images")
public class ImageController {

    private final ImageService imageService;

    @Operation(
            summary = "Presigned URL 생성",
            description = "R2에 기술자 이미지 업로드를 위한 Presigned URL을 생성합니다. imageFolderType: ENGINEER_PROFILE | ENGINEER_PORTFOLIO"
    )
    @PostMapping("/{imageFolderType}/presigned-url")
    public ResponseEntity<CommonResponse<PresignedUrlResponseDto>> generatePresignedUrl(
            @PathVariable String imageFolderType,
            @Valid @RequestBody PresignedUrlRequestDto request
    ) {
        PresignedUrlResponseDto response = imageService.generatePresignedUrlWithImageUrl(
                imageFolderType,
                request.getFileName(),
                request.getContentType(),
                request.getImageSize()
        );

        return ResponseEntity.ok(CommonResponse.success(response));
    }
}

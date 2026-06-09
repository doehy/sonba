package com.sonbadrim_server.domain.inquiry.controller;

import com.sonbadrim_server.domain.inquiry.dto.request.CreateInquiryRequestDto;
import com.sonbadrim_server.domain.inquiry.dto.request.SendInquiryMessageRequestDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryDetailMessageResponseDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryDetailResponseDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryListResponseDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryMessagePageResponseDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryMessageResponseDto;
import com.sonbadrim_server.domain.inquiry.service.InquiryService;
import com.sonbadrim_server.global.common.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Inquiry", description = "문의/문의방 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/inquiries")
public class InquiryController {

    private final InquiryService inquiryService;

    @Operation(summary = "문의 목록 조회", description = "내 문의방 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<?> getInquiries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        InquiryListResponseDto response = inquiryService.getInquiries(page, size);
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @Operation(summary = "문의 상세 조회", description = "문의방 메타 정보를 조회합니다.")
    @GetMapping("/{inquiryId}")
    public ResponseEntity<?> getInquiryDetail(
            @PathVariable Long inquiryId
    ) {
        InquiryDetailResponseDto response = inquiryService.getInquiryDetail(inquiryId);
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @Operation(summary = "문의 메시지 조회", description = "문의방 메시지를 커서 기반으로 조회합니다.")
    @GetMapping("/{inquiryId}/messages")
    public ResponseEntity<?> getInquiryMessages(
            @PathVariable Long inquiryId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "15") int size
    ) {
        InquiryMessagePageResponseDto response = inquiryService.getInquiryMessages(inquiryId, cursor, size);
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @Operation(summary = "문의하기", description = "기술자에게 처음 문의할 때 문의방을 생성하고 첫 메시지를 전송합니다.")
    @PostMapping
    public ResponseEntity<?> createInquiry(
            @Valid @RequestBody CreateInquiryRequestDto requestDto
    ) {
        InquiryMessageResponseDto response = inquiryService.createInquiry(requestDto);
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @Operation(summary = "문의방 메시지 전송", description = "기존 문의방에 메시지를 추가합니다.")
    @PostMapping("/{inquiryId}/messages")
    public ResponseEntity<?> sendMessageInRoom(
            @PathVariable Long inquiryId,
            @Valid @RequestBody SendInquiryMessageRequestDto requestDto
    ) {
        InquiryDetailMessageResponseDto response = inquiryService.sendMessageInRoom(inquiryId, requestDto);
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @Operation(summary = "문의방 나가기", description = "문의방에서 나갑니다. 양쪽 모두 나가면 상태가 CLOSED로 전환됩니다.")
    @PostMapping("/{inquiryId}/leave")
    public ResponseEntity<?> leaveInquiry(@PathVariable Long inquiryId) {
        inquiryService.leaveInquiry(inquiryId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}

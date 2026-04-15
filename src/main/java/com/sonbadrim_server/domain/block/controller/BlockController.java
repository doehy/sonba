package com.sonbadrim_server.domain.block.controller;

import com.sonbadrim_server.domain.block.dto.request.BlockCreateRequestDto;
import com.sonbadrim_server.domain.block.dto.response.BlockCreateResponseDto;
import com.sonbadrim_server.domain.block.dto.response.BlockListResponseDto;
import com.sonbadrim_server.domain.block.service.BlockService;
import com.sonbadrim_server.global.common.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Block", description = "차단 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/blocks")
public class BlockController {

    private final BlockService blockService;

    @Operation(summary = "유저 차단", description = "상대 유저를 차단합니다.")
    @PostMapping
    public ResponseEntity<?> createBlock(@Valid @RequestBody BlockCreateRequestDto requestDto) {
        BlockCreateResponseDto response = blockService.blockUser(requestDto);
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @Operation(summary = "내 차단 목록 조회", description = "현재 로그인한 사용자가 차단한 유저 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<?> getMyBlockedUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        BlockListResponseDto response = blockService.getMyBlockedUsers(page, size);
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @Operation(summary = "유저 차단 해제", description = "상대 유저 차단을 해제합니다.")
    @DeleteMapping("/{blockedUserId}")
    public ResponseEntity<?> deleteBlock(@PathVariable Long blockedUserId) {
        blockService.unblockUser(blockedUserId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}

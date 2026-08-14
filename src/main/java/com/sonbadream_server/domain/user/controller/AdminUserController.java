package com.sonbadream_server.domain.user.controller;

import com.sonbadream_server.domain.user.dto.response.UserListItemResponseDto;
import com.sonbadream_server.domain.user.service.UserService;
import com.sonbadream_server.global.common.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 유저 API", description = "관리자 유저 상태 관리 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;

    @Operation(summary = "관리자 유저 목록 조회", description = "관리자가 유저 목록을 최신 가입순으로 조회합니다.")
    @GetMapping
    public ResponseEntity<?> getAdminUsers(
            @RequestParam(name = "keyword", required = false)
            String keyword,
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
            int page,
            @RequestParam(name = "size", defaultValue = "20")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
            int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("userId"))
        );
        Page<UserListItemResponseDto> response = userService.getAdminUsers(keyword, pageable);
        return ResponseEntity.ok(CommonResponse.success(response));
    }

    @Operation(summary = "유저 일시 정지", description = "관리자가 활성 유저를 일시 정지합니다.")
    @PatchMapping("/{userId}/suspend")
    public ResponseEntity<?> suspendUser(@PathVariable Long userId) {
        userService.suspendUserByAdmin(userId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @Operation(summary = "유저 정지 해제", description = "관리자가 정지된 유저를 활성 상태로 되돌립니다.")
    @PatchMapping("/{userId}/activate")
    public ResponseEntity<?> activateUser(@PathVariable Long userId) {
        userService.activateUserByAdmin(userId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}

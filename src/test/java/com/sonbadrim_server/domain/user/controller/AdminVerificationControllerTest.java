package com.sonbadrim_server.domain.user.controller;

import com.sonbadrim_server.domain.user.dto.request.AdminVerificationRequestDto;
import com.sonbadrim_server.domain.user.dto.response.AdminVerificationResponseDto;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import com.sonbadrim_server.domain.user.service.UserService;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AdminVerificationController.class)
class AdminVerificationControllerTest {

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("관리자 재인증")
    class VerifyAdmin {

        @Test
        @DisplayName("관리자 비밀번호가 일치하면 관리자 인증 토큰을 발급한다")
        void verifyAdmin_success() throws Exception {
            // given
            given(userService.verifyAdminPassword(any(AdminVerificationRequestDto.class)))
                    .willReturn(new AdminVerificationResponseDto("admin-verified-token"));

            // when & then
            mockMvc.perform(post("/admin/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new PasswordRequest("admin-password"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.adminVerifiedToken").value("admin-verified-token"));
        }

        @Test
        @DisplayName("관리자 비밀번호가 일치하지 않으면 재인증에 실패한다")
        void verifyAdmin_failed() throws Exception {
            // given
            given(userService.verifyAdminPassword(any(AdminVerificationRequestDto.class)))
                    .willThrow(new CustomException(ErrorCode.ADMIN_VERIFICATION_FAILED));

            // when & then
            mockMvc.perform(post("/admin/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new PasswordRequest("wrong-password"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("ADMIN_VERIFICATION_FAILED"));
        }
    }

    private record PasswordRequest(String password) {
    }
}

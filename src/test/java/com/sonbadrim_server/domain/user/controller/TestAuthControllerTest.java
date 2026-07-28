package com.sonbadrim_server.domain.user.controller;

import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.entity.UserRole;
import com.sonbadrim_server.domain.user.entity.UserStatus;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import com.sonbadrim_server.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(TestAuthController.class)
@Import(TestAuthController.class)
@TestPropertySource(properties = "app.test-login.password=test1234")
class TestAuthControllerTest {

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private UserService userService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("테스트 계정으로 로그인할 수 있다")
    void testLogin_success() throws Exception {
        // given
        User user = User.builder()
                .userId(1L)
                .kakaoId(900000000001L)
                .nickname("테스트유저")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userService.generateTokenPair(user))
                .willReturn(Map.of("accessToken", "access-token", "refreshToken", "refresh-token"));

        // when & then
        mockMvc.perform(post("/auth/test-login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId": 1,
                                  "password": "test1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
    }

    @Test
    @DisplayName("테스트 로그인 비밀번호가 틀리면 로그인할 수 없다")
    void testLogin_invalidPassword() throws Exception {
        // when & then
        mockMvc.perform(post("/auth/test-login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId": 1,
                                  "password": "wrong"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("UNAUTHORIZED"));

        verify(userRepository, never()).findById(1L);
    }

    @Test
    @DisplayName("존재하지 않는 사용자 ID로는 테스트 로그인할 수 없다")
    void testLogin_userNotFound() throws Exception {
        // given
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        mockMvc.perform(post("/auth/test-login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId": 999,
                                  "password": "test1234"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("USER_NOT_FOUND"));
    }
}

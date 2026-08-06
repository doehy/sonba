package com.sonbadrim_server.domain.user.controller;

import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.entity.UserRole;
import com.sonbadrim_server.domain.user.entity.UserStatus;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import com.sonbadrim_server.domain.user.service.KakaoService;
import com.sonbadrim_server.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(UserController.class)
class UserControllerTest {

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private KakaoService kakaoService;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("내 정보 조회")
    class GetCurrentUser {

        @Test
        @DisplayName("현재 로그인한 사용자의 role을 함께 조회할 수 있다")
        void getCurrentUser_containsRole() throws Exception {
            // given
            User currentUser = User.builder()
                    .userId(1L)
                    .kakaoId(900000000001L)
                    .nickname("관리자")
                    .status(UserStatus.ACTIVE)
                    .role(UserRole.ADMIN)
                    .build();
            given(userService.getCurrentUser()).willReturn(currentUser);

            // when & then
            mockMvc.perform(get("/users/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.userId").value(1L))
                    .andExpect(jsonPath("$.data.kakaoId").value(900000000001L))
                    .andExpect(jsonPath("$.data.nickname").value("관리자"))
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.role").value("ADMIN"));
        }
    }
}

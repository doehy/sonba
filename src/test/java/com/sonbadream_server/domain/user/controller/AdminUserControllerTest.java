package com.sonbadream_server.domain.user.controller;

import com.sonbadream_server.domain.user.dto.response.UserListItemResponseDto;
import com.sonbadream_server.domain.user.entity.UserRole;
import com.sonbadream_server.domain.user.entity.UserStatus;
import com.sonbadream_server.domain.user.repository.UserRepository;
import com.sonbadream_server.domain.user.service.UserService;
import com.sonbadream_server.global.exception.CustomException;
import com.sonbadream_server.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AdminUserController.class)
class AdminUserControllerTest {

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("관리자 유저 목록 조회")
    class GetAdminUsers {

        @Test
        @DisplayName("관리자는 유저 목록을 조회할 수 있다")
        void getAdminUsers_success() throws Exception {
            // given
            UserListItemResponseDto response = UserListItemResponseDto.builder()
                    .userId(7L)
                    .nickname("테스트유저")
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .engineerRegistered(true)
                    .build();
            given(userService.getAdminUsers(any(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(response)));

            // when & then
            mockMvc.perform(get("/admin/users")
                            .param("page", "0")
                            .param("keyword", "테스트")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].userId").value(7L))
                    .andExpect(jsonPath("$.data.content[0].nickname").value("테스트유저"))
                    .andExpect(jsonPath("$.data.content[0].role").value("USER"))
                    .andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.content[0].engineerRegistered").value(true));

            verify(userService).getAdminUsers(any(), any(Pageable.class));
        }

        @Test
        @DisplayName("관리자가 아니면 유저 목록을 조회할 수 없다")
        void getAdminUsers_forbidden() throws Exception {
            // given
            given(userService.getAdminUsers(any(), any(Pageable.class)))
                    .willThrow(new CustomException(ErrorCode.FORBIDDEN));

            // when & then
            mockMvc.perform(get("/admin/users"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("관리자 유저 일시 정지")
    class SuspendUser {

        @Test
        @DisplayName("관리자는 유저를 일시 정지할 수 있다")
        void suspendUser_success() throws Exception {
            // when & then
            mockMvc.perform(patch("/admin/users/{userId}/suspend", 7L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(userService).suspendUserByAdmin(7L);
        }

        @Test
        @DisplayName("관리자가 아니면 유저를 일시 정지할 수 없다")
        void suspendUser_forbidden() throws Exception {
            // given
            willThrow(new CustomException(ErrorCode.FORBIDDEN))
                    .given(userService)
                    .suspendUserByAdmin(7L);

            // when & then
            mockMvc.perform(patch("/admin/users/{userId}/suspend", 7L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("관리자 유저 정지 해제")
    class ActivateUser {

        @Test
        @DisplayName("관리자는 유저 정지를 해제할 수 있다")
        void activateUser_success() throws Exception {
            // when & then
            mockMvc.perform(patch("/admin/users/{userId}/activate", 7L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(userService).activateUserByAdmin(7L);
        }

        @Test
        @DisplayName("존재하지 않는 유저는 정지 해제할 수 없다")
        void activateUser_notFound() throws Exception {
            // given
            willThrow(new CustomException(ErrorCode.USER_NOT_FOUND))
                    .given(userService)
                    .activateUserByAdmin(999L);

            // when & then
            mockMvc.perform(patch("/admin/users/{userId}/activate", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("USER_NOT_FOUND"));
        }
    }
}

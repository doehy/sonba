package com.sonbadrim_server.domain.engineer.controller;

import com.sonbadrim_server.domain.engineer.dto.response.EngineerClaimRequestResponseDto;
import com.sonbadrim_server.domain.engineer.entity.EngineerClaimStatus;
import com.sonbadrim_server.domain.engineer.service.EngineerService;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AdminEngineerClaimController.class)
class AdminEngineerClaimControllerTest {

    @MockitoBean
    private EngineerService engineerService;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("기술자 프로필 연결 요청 목록 조회")
    class GetEngineerClaimRequests {

        @Test
        @DisplayName("관리자는 기술자 프로필 연결 요청 목록을 조회할 수 있다")
        void getEngineerClaimRequests_success() throws Exception {
            // given
            EngineerClaimRequestResponseDto response = EngineerClaimRequestResponseDto.builder()
                    .engineerId(7L)
                    .engineerName("수동기술자")
                    .phone("01012345678")
                    .regionName("서울특별시 강남구")
                    .claimStatus(EngineerClaimStatus.REQUESTED)
                    .requesterUserId(5L)
                    .requesterNickname("요청자")
                    .build();
            given(engineerService.getEngineerClaimRequests(any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

            // when & then
            mockMvc.perform(get("/admin/engineer-claims"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].engineerId").value(7L))
                    .andExpect(jsonPath("$.data.content[0].engineerName").value("수동기술자"))
                    .andExpect(jsonPath("$.data.content[0].claimStatus").value("REQUESTED"))
                    .andExpect(jsonPath("$.data.content[0].requesterUserId").value(5L));

            verify(engineerService).getEngineerClaimRequests(any(Pageable.class));
        }

        @Test
        @DisplayName("관리자가 아니면 기술자 프로필 연결 요청 목록을 조회할 수 없다")
        void getEngineerClaimRequests_forbidden() throws Exception {
            // given
            given(engineerService.getEngineerClaimRequests(any(Pageable.class)))
                    .willThrow(new CustomException(ErrorCode.FORBIDDEN));

            // when & then
            mockMvc.perform(get("/admin/engineer-claims"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("기술자 프로필 연결 요청 승인")
    class ApproveEngineerClaim {

        @Test
        @DisplayName("관리자는 기술자 프로필 연결 요청을 승인할 수 있다")
        void approveEngineerClaim_success() throws Exception {
            // when & then
            mockMvc.perform(post("/admin/engineer-claims/{engineerId}/approve", 7L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(engineerService).approveEngineerClaim(7L);
        }

        @Test
        @DisplayName("요청 대기 상태가 아닌 기술자 프로필은 승인할 수 없다")
        void approveEngineerClaim_notRequested() throws Exception {
            // given
            willThrow(new CustomException(ErrorCode.ENGINEER_CLAIM_NOT_REQUESTED))
                    .given(engineerService)
                    .approveEngineerClaim(7L);

            // when & then
            mockMvc.perform(post("/admin/engineer-claims/{engineerId}/approve", 7L))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("ENGINEER_CLAIM_NOT_REQUESTED"));
        }
    }

    @Nested
    @DisplayName("기술자 프로필 연결 요청 거절")
    class RejectEngineerClaim {

        @Test
        @DisplayName("관리자는 기술자 프로필 연결 요청을 거절할 수 있다")
        void rejectEngineerClaim_success() throws Exception {
            // when & then
            mockMvc.perform(post("/admin/engineer-claims/{engineerId}/reject", 7L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(engineerService).rejectEngineerClaim(7L);
        }

        @Test
        @DisplayName("존재하지 않는 기술자 프로필은 거절할 수 없다")
        void rejectEngineerClaim_engineerNotFound() throws Exception {
            // given
            willThrow(new CustomException(ErrorCode.ENGINEER_NOT_FOUND))
                    .given(engineerService)
                    .rejectEngineerClaim(999L);

            // when & then
            mockMvc.perform(post("/admin/engineer-claims/{engineerId}/reject", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("ENGINEER_NOT_FOUND"));
        }
    }
}

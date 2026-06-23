package com.sonbadrim_server.domain.inquiry.controller;

import com.sonbadrim_server.domain.inquiry.dto.response.InquiryDetailResponseDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryListItemResponseDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryListResponseDto;
import com.sonbadrim_server.domain.inquiry.entity.InquiryStatus;
import com.sonbadrim_server.domain.inquiry.service.InquiryService;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(InquiryController.class)
class InquiryControllerTest {

    @MockitoBean
    private InquiryService inquiryService;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("문의 목록 조회")
    class GetInquiries {

        @Test
        @DisplayName("문의 목록을 기본 페이지 설정으로 조회할 수 있다")
        void getInquiries_defaultPage() throws Exception {
            // given
            LocalDateTime lastMessageAt = LocalDateTime.of(2026, 6, 23, 12, 0);
            InquiryListItemResponseDto item = new InquiryListItemResponseDto(
                    1L,
                    "김기술",
                    "profile.jpg",
                    "문의 답변입니다.",
                    lastMessageAt,
                    2L
            );
            given(inquiryService.getInquiries(0, 10))
                    .willReturn(new InquiryListResponseDto(List.of(item), false));

            // when & then
            mockMvc.perform(get("/inquiries"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.items[0].inquiryId").value(1L))
                    .andExpect(jsonPath("$.data.items[0].otherUserNickname").value("김기술"))
                    .andExpect(jsonPath("$.data.items[0].otherUserProfileImage").value("profile.jpg"))
                    .andExpect(jsonPath("$.data.items[0].lastMessage").value("문의 답변입니다."))
                    .andExpect(jsonPath("$.data.items[0].unreadCount").value(2L))
                    .andExpect(jsonPath("$.data.hasNext").value(false));

            verify(inquiryService).getInquiries(0, 10);
        }

        @Test
        @DisplayName("페이지 번호와 크기를 지정하여 문의 목록을 조회할 수 있다")
        void getInquiries_customPage() throws Exception {
            // given
            given(inquiryService.getInquiries(2, 5))
                    .willReturn(new InquiryListResponseDto(List.of(), true));

            // when & then
            mockMvc.perform(get("/inquiries")
                            .param("page", "2")
                            .param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.items").isEmpty())
                    .andExpect(jsonPath("$.data.hasNext").value(true));

            verify(inquiryService).getInquiries(2, 5);
        }
    }

    @Nested
    @DisplayName("문의 상세 조회")
    class GetInquiryDetail {

        @Test
        @DisplayName("문의 참여자는 문의 상세 정보를 조회할 수 있다")
        void getInquiryDetail_success() throws Exception {
            // given
            InquiryDetailResponseDto response = new InquiryDetailResponseDto(
                    7L,
                    InquiryStatus.OPEN,
                    1L,
                    2L
            );
            given(inquiryService.getInquiryDetail(7L))
                    .willReturn(response);

            // when & then
            mockMvc.perform(get("/inquiries/{inquiryId}", 7L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.inquiryId").value(7L))
                    .andExpect(jsonPath("$.data.status").value("OPEN"))
                    .andExpect(jsonPath("$.data.requesterUserId").value(1L))
                    .andExpect(jsonPath("$.data.targetUserId").value(2L));

            verify(inquiryService).getInquiryDetail(7L);
        }

        @Test
        @DisplayName("조회할 수 없는 문의 상세 정보는 찾을 수 없음으로 응답한다")
        void getInquiryDetail_inquiryNotFound() throws Exception {
            // given
            given(inquiryService.getInquiryDetail(999L))
                    .willThrow(new CustomException(ErrorCode.INQUIRY_NOT_FOUND));

            // when & then
            mockMvc.perform(get("/inquiries/{inquiryId}", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("INQUIRY_NOT_FOUND"));
        }
    }
}

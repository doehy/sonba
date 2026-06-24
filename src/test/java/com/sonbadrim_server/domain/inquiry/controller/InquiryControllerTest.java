package com.sonbadrim_server.domain.inquiry.controller;

import com.sonbadrim_server.domain.inquiry.dto.request.CreateInquiryRequestDto;
import com.sonbadrim_server.domain.inquiry.dto.request.SendInquiryMessageRequestDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryDetailMessageResponseDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryDetailResponseDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryListItemResponseDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryListResponseDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryMessagePageResponseDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryMessageResponseDto;
import com.sonbadrim_server.domain.inquiry.entity.InquiryStatus;
import com.sonbadrim_server.domain.inquiry.service.InquiryService;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("문의하기")
    class CreateInquiry {

        @Test
        @DisplayName("기술자에게 처음 문의할 수 있다")
        void createInquiry_success() throws Exception {
            // given
            Map<String, Object> request = Map.of(
                    "targetUserId", 2L,
                    "message", "처음 문의드립니다."
            );
            given(inquiryService.createInquiry(any(CreateInquiryRequestDto.class)))
                    .willReturn(new InquiryMessageResponseDto(7L, 10L));

            // when & then
            mockMvc.perform(post("/inquiries")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.inquiryId").value(7L))
                    .andExpect(jsonPath("$.data.messageId").value(10L));

            ArgumentCaptor<CreateInquiryRequestDto> captor =
                    ArgumentCaptor.forClass(CreateInquiryRequestDto.class);
            verify(inquiryService).createInquiry(captor.capture());

            CreateInquiryRequestDto capturedRequest = captor.getValue();
            assertThat(capturedRequest.getTargetUserId()).isEqualTo(2L);
            assertThat(capturedRequest.getMessage()).isEqualTo("처음 문의드립니다.");
        }

        @Test
        @DisplayName("문의 대상이 없으면 문의할 수 없다")
        void createInquiry_targetUserIdIsMissing() throws Exception {
            // given
            Map<String, Object> request = Map.of(
                    "message", "처음 문의드립니다."
            );

            // when & then
            expectCreateInquiryValidationFailure(request, "targetUserId");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("문의 메시지가 없으면 문의할 수 없다")
        void createInquiry_messageIsBlank(String message) throws Exception {
            // given
            Map<String, Object> request = Map.of(
                    "targetUserId", 2L,
                    "message", message
            );

            // when & then
            expectCreateInquiryValidationFailure(request, "message");
        }

        @Test
        @DisplayName("문의 메시지는 500자까지만 작성할 수 있다")
        void createInquiry_messageTooLong() throws Exception {
            // given
            Map<String, Object> request = Map.of(
                    "targetUserId", 2L,
                    "message", "가".repeat(501)
            );

            // when & then
            expectCreateInquiryValidationFailure(request, "message");
        }

        @Test
        @DisplayName("이미 문의방이 있으면 새 문의방을 만들 수 없다")
        void createInquiry_alreadyExists() throws Exception {
            // given
            Map<String, Object> request = Map.of(
                    "targetUserId", 2L,
                    "message", "다시 문의드립니다."
            );
            given(inquiryService.createInquiry(any(CreateInquiryRequestDto.class)))
                    .willThrow(new CustomException(ErrorCode.INQUIRY_ALREADY_EXISTS));

            // when & then
            mockMvc.perform(post("/inquiries")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("INQUIRY_ALREADY_EXISTS"));
        }
    }

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

    @Nested
    @DisplayName("문의 메시지 조회")
    class GetInquiryMessages {

        @Test
        @DisplayName("문의 메시지를 기본 커서와 크기로 조회할 수 있다")
        void getInquiryMessages_defaultCursorAndSize() throws Exception {
            // given
            LocalDateTime createdAt = LocalDateTime.of(2026, 6, 23, 13, 0);
            InquiryDetailMessageResponseDto message =
                    new InquiryDetailMessageResponseDto(
                            10L,
                            1L,
                            "문의 메시지",
                            createdAt
                    );
            given(inquiryService.getInquiryMessages(7L, null, 15))
                    .willReturn(new InquiryMessagePageResponseDto(
                            List.of(message),
                            false,
                            null
                    ));

            // when & then
            mockMvc.perform(get("/inquiries/{inquiryId}/messages", 7L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.items[0].messageId").value(10L))
                    .andExpect(jsonPath("$.data.items[0].senderUserId").value(1L))
                    .andExpect(jsonPath("$.data.items[0].content").value("문의 메시지"))
                    .andExpect(jsonPath("$.data.hasNext").value(false))
                    .andExpect(jsonPath("$.data.nextCursor").doesNotExist());

            verify(inquiryService).getInquiryMessages(7L, null, 15);
        }

        @Test
        @DisplayName("커서와 페이지 크기를 지정하여 이전 문의 메시지를 조회할 수 있다")
        void getInquiryMessages_customCursorAndSize() throws Exception {
            // given
            given(inquiryService.getInquiryMessages(7L, 100L, 5))
                    .willReturn(new InquiryMessagePageResponseDto(
                            List.of(),
                            true,
                            95L
                    ));

            // when & then
            mockMvc.perform(get("/inquiries/{inquiryId}/messages", 7L)
                            .param("cursor", "100")
                            .param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.items").isEmpty())
                    .andExpect(jsonPath("$.data.hasNext").value(true))
                    .andExpect(jsonPath("$.data.nextCursor").value(95L));

            verify(inquiryService).getInquiryMessages(7L, 100L, 5);
        }

        @Test
        @DisplayName("조회할 수 없는 문의방의 메시지는 찾을 수 없음으로 응답한다")
        void getInquiryMessages_inquiryNotFound() throws Exception {
            // given
            given(inquiryService.getInquiryMessages(999L, null, 15))
                    .willThrow(new CustomException(ErrorCode.INQUIRY_NOT_FOUND));

            // when & then
            mockMvc.perform(get("/inquiries/{inquiryId}/messages", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("INQUIRY_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("문의방 메시지 전송")
    class SendMessageInRoom {

        @Test
        @DisplayName("문의방에 메시지를 보낼 수 있다")
        void sendMessageInRoom_success() throws Exception {
            // given
            LocalDateTime createdAt = LocalDateTime.of(2026, 6, 24, 10, 0);
            Map<String, Object> request = Map.of(
                    "message", "새 메시지입니다."
            );
            given(inquiryService.sendMessageInRoom(eq(7L), any(SendInquiryMessageRequestDto.class)))
                    .willReturn(new InquiryDetailMessageResponseDto(
                            10L,
                            1L,
                            "새 메시지입니다.",
                            createdAt
                    ));

            // when & then
            mockMvc.perform(post("/inquiries/{inquiryId}/messages", 7L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.messageId").value(10L))
                    .andExpect(jsonPath("$.data.senderUserId").value(1L))
                    .andExpect(jsonPath("$.data.content").value("새 메시지입니다."));

            ArgumentCaptor<SendInquiryMessageRequestDto> captor =
                    ArgumentCaptor.forClass(SendInquiryMessageRequestDto.class);
            verify(inquiryService).sendMessageInRoom(eq(7L), captor.capture());

            assertThat(captor.getValue().getMessage()).isEqualTo("새 메시지입니다.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("메시지가 없으면 문의방에 메시지를 보낼 수 없다")
        void sendMessageInRoom_messageIsBlank(String message) throws Exception {
            // given
            Map<String, Object> request = Map.of(
                    "message", message
            );

            // when & then
            expectSendMessageValidationFailure(request, "message");
        }

        @Test
        @DisplayName("메시지는 500자까지만 보낼 수 있다")
        void sendMessageInRoom_messageTooLong() throws Exception {
            // given
            Map<String, Object> request = Map.of(
                    "message", "가".repeat(501)
            );

            // when & then
            expectSendMessageValidationFailure(request, "message");
        }

        @Test
        @DisplayName("조회할 수 없는 문의방에는 메시지를 보낼 수 없다")
        void sendMessageInRoom_inquiryNotFound() throws Exception {
            // given
            Map<String, Object> request = Map.of(
                    "message", "새 메시지입니다."
            );
            given(inquiryService.sendMessageInRoom(eq(999L), any(SendInquiryMessageRequestDto.class)))
                    .willThrow(new CustomException(ErrorCode.INQUIRY_NOT_FOUND));

            // when & then
            mockMvc.perform(post("/inquiries/{inquiryId}/messages", 999L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("INQUIRY_NOT_FOUND"));
        }

        @Test
        @DisplayName("참여하지 않은 문의방에는 메시지를 보낼 수 없다")
        void sendMessageInRoom_forbidden() throws Exception {
            // given
            Map<String, Object> request = Map.of(
                    "message", "새 메시지입니다."
            );
            given(inquiryService.sendMessageInRoom(eq(7L), any(SendInquiryMessageRequestDto.class)))
                    .willThrow(new CustomException(ErrorCode.INQUIRY_FORBIDDEN));

            // when & then
            mockMvc.perform(post("/inquiries/{inquiryId}/messages", 7L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("INQUIRY_FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("문의방 나가기")
    class LeaveInquiry {

        @Test
        @DisplayName("문의방을 나갈 수 있다")
        void leaveInquiry_success() throws Exception {
            // when & then
            mockMvc.perform(post("/inquiries/{inquiryId}/leave", 7L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").doesNotExist());

            verify(inquiryService).leaveInquiry(7L);
        }

        @Test
        @DisplayName("조회할 수 없는 문의방은 나갈 수 없다")
        void leaveInquiry_inquiryNotFound() throws Exception {
            // given
            willThrow(new CustomException(ErrorCode.INQUIRY_NOT_FOUND))
                    .given(inquiryService)
                    .leaveInquiry(999L);

            // when & then
            mockMvc.perform(post("/inquiries/{inquiryId}/leave", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("INQUIRY_NOT_FOUND"));
        }
    }

    private void expectCreateInquiryValidationFailure(
            Map<String, Object> request,
            String fieldName
    ) throws Exception {
        mockMvc.perform(post("/inquiries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("GLOBAL_400_1"))
                .andExpect(jsonPath("$.data.validation." + fieldName).exists());

        verify(inquiryService, never())
                .createInquiry(any(CreateInquiryRequestDto.class));
    }

    private void expectSendMessageValidationFailure(
            Map<String, Object> request,
            String fieldName
    ) throws Exception {
        mockMvc.perform(post("/inquiries/{inquiryId}/messages", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("GLOBAL_400_1"))
                .andExpect(jsonPath("$.data.validation." + fieldName).exists());

        verify(inquiryService, never())
                .sendMessageInRoom(eq(7L), any(SendInquiryMessageRequestDto.class));
    }
}

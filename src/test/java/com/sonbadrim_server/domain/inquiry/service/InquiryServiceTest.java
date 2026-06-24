package com.sonbadrim_server.domain.inquiry.service;

import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.engineer.repository.EngineerRepository;
import com.sonbadrim_server.domain.inquiry.dto.request.CreateInquiryRequestDto;
import com.sonbadrim_server.domain.inquiry.dto.request.SendInquiryMessageRequestDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryDetailMessageResponseDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryMessageResponseDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryMessagePageResponseDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryDetailResponseDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryListResponseDto;
import com.sonbadrim_server.domain.inquiry.entity.Inquiry;
import com.sonbadrim_server.domain.inquiry.entity.InquiryMessage;
import com.sonbadrim_server.domain.inquiry.entity.InquiryStatus;
import com.sonbadrim_server.domain.inquiry.repository.InquiryMessageRepository;
import com.sonbadrim_server.domain.inquiry.repository.InquiryRepository;
import com.sonbadrim_server.domain.notification.service.NotificationService;
import com.sonbadrim_server.domain.region.entity.Region;
import com.sonbadrim_server.domain.region.repository.RegionRepository;
import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.entity.UserRole;
import com.sonbadrim_server.domain.user.entity.UserStatus;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import com.sonbadrim_server.domain.user.service.UserService;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/sql/engineer-test-data.sql")
class InquiryServiceTest {

    @Autowired
    private InquiryService inquiryService;

    @Autowired
    private InquiryRepository inquiryRepository;

    @Autowired
    private InquiryMessageRepository inquiryMessageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private EngineerRepository engineerRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private NotificationService notificationService;

    private Region region;
    private User currentUser;

    @BeforeEach
    void setUp() {
        region = regionRepository.findAllByOrderByRegionIdAsc().getFirst();
        currentUser = saveUser(900000000001L, "현재사용자");

        given(userService.getCurrentUser()).willReturn(currentUser);
    }

    @Nested
    @DisplayName("문의 목록 조회")
    class GetInquiries {

        @Test
        @DisplayName("현재 사용자의 문의 목록에서 상대방과 마지막 메시지 및 읽지 않은 메시지 수를 조회할 수 있다")
        void getInquiries_success() {
            // given
            User engineerUser = saveUser(900000000002L, "김기술");
            saveEngineer(engineerUser, "01011112222", "profile.jpg");
            Inquiry sentInquiry = saveInquiry(currentUser, engineerUser);

            InquiryMessage sentFirstMessage =
                    saveMessage(sentInquiry, currentUser, "첫 문의입니다.");
            sentInquiry.updateLastReadMessageId(
                    currentUser.getUserId(),
                    sentFirstMessage.getInquiryMessageId()
            );
            InquiryMessage sentLastMessage =
                    saveMessage(sentInquiry, engineerUser, "답변드립니다.");
            sentInquiry.updateLastMessage(sentLastMessage.getInquiryMessageId());

            User requester = saveUser(900000000003L, "문의사용자");
            Inquiry receivedInquiry = saveInquiry(requester, currentUser);
            InquiryMessage receivedLastMessage =
                    saveMessage(receivedInquiry, requester, "문의드려요.");
            receivedInquiry.updateLastMessage(receivedLastMessage.getInquiryMessageId());

            entityManager.flush();
            entityManager.clear();

            // when
            InquiryListResponseDto result = inquiryService.getInquiries(0, 10);

            // then
            assertThat(result.getItems()).hasSize(2);
            assertThat(result.isHasNext()).isFalse();

            assertThat(result.getItems())
                    .filteredOn(item -> item.getInquiryId().equals(sentInquiry.getInquiryId()))
                    .singleElement()
                    .satisfies(item -> {
                        assertThat(item.getOtherUserNickname()).isEqualTo("김기술");
                        assertThat(item.getOtherUserProfileImage()).isEqualTo("profile.jpg");
                        assertThat(item.getLastMessage()).isEqualTo("답변드립니다.");
                        assertThat(item.getLastMessageAt()).isNotNull();
                        assertThat(item.getUnreadCount()).isEqualTo(1L);
                    });

            assertThat(result.getItems())
                    .filteredOn(item -> item.getInquiryId().equals(receivedInquiry.getInquiryId()))
                    .singleElement()
                    .satisfies(item -> {
                        assertThat(item.getOtherUserNickname()).isEqualTo("문의사용자");
                        assertThat(item.getOtherUserProfileImage()).isNull();
                        assertThat(item.getLastMessage()).isEqualTo("문의드려요.");
                        assertThat(item.getUnreadCount()).isEqualTo(1L);
                    });
        }

        @Test
        @DisplayName("현재 사용자가 나간 문의방은 문의 목록에서 조회되지 않는다")
        void getInquiries_excludesLeftInquiry() {
            // given
            User visibleTarget = saveUser(900000000002L, "보이는상대");
            Inquiry visibleInquiry = saveInquiry(currentUser, visibleTarget);
            InquiryMessage visibleMessage =
                    saveMessage(visibleInquiry, currentUser, "보이는 메시지");
            visibleInquiry.updateLastMessage(visibleMessage.getInquiryMessageId());

            User leftTarget = saveUser(900000000003L, "나간상대");
            Inquiry leftInquiry = saveInquiry(currentUser, leftTarget);
            InquiryMessage leftMessage =
                    saveMessage(leftInquiry, currentUser, "보이지 않는 메시지");
            leftInquiry.updateLastMessage(leftMessage.getInquiryMessageId());
            leftInquiry.leave(currentUser.getUserId());

            entityManager.flush();
            entityManager.clear();

            // when
            InquiryListResponseDto result = inquiryService.getInquiries(0, 10);

            // then
            assertThat(result.getItems())
                    .extracting(item -> item.getInquiryId())
                    .containsExactly(visibleInquiry.getInquiryId());
        }

        @Test
        @DisplayName("다음 페이지가 있으면 문의 목록에 다음 페이지 존재 여부를 함께 반환한다")
        void getInquiries_hasNext() {
            // given
            User firstTarget = saveUser(900000000002L, "첫번째상대");
            Inquiry firstInquiry = saveInquiry(currentUser, firstTarget);
            InquiryMessage firstMessage =
                    saveMessage(firstInquiry, currentUser, "첫 번째 메시지");
            firstInquiry.updateLastMessage(firstMessage.getInquiryMessageId());

            User secondTarget = saveUser(900000000003L, "두번째상대");
            Inquiry secondInquiry = saveInquiry(currentUser, secondTarget);
            InquiryMessage secondMessage =
                    saveMessage(secondInquiry, currentUser, "두 번째 메시지");
            secondInquiry.updateLastMessage(secondMessage.getInquiryMessageId());

            entityManager.flush();
            entityManager.clear();

            // when
            InquiryListResponseDto result = inquiryService.getInquiries(0, 1);

            // then
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.isHasNext()).isTrue();
        }
    }

    @Nested
    @DisplayName("문의하기")
    class CreateInquiry {

        @Test
        @DisplayName("기술자에게 처음 문의하면 문의방과 첫 메시지를 만들 수 있다")
        void createInquiry_success() {
            // given
            User targetUser = saveUser(900000000002L, "김기술");
            saveEngineer(targetUser, "01011112222", "profile.jpg");
            CreateInquiryRequestDto request =
                    createInquiryRequest(targetUser.getUserId(), "  처음 문의드립니다.  ");

            // when
            InquiryMessageResponseDto result = inquiryService.createInquiry(request);

            // then
            assertThat(result.getInquiryId()).isNotNull();
            assertThat(result.getMessageId()).isNotNull();

            Inquiry inquiry = inquiryRepository.findById(result.getInquiryId())
                    .orElseThrow();
            assertThat(inquiry.getRequester().getUserId()).isEqualTo(currentUser.getUserId());
            assertThat(inquiry.getTarget().getUserId()).isEqualTo(targetUser.getUserId());
            assertThat(inquiry.getLastMessageId()).isEqualTo(result.getMessageId());
            assertThat(inquiry.getRequesterLastReadMessageId()).isEqualTo(result.getMessageId());
            assertThat(inquiry.getTargetLastReadMessageId()).isNull();
            assertThat(inquiry.getLastMessageAt()).isNotNull();

            InquiryMessage message = inquiryMessageRepository.findById(result.getMessageId())
                    .orElseThrow();
            assertThat(message.getInquiry().getInquiryId()).isEqualTo(result.getInquiryId());
            assertThat(message.getSender().getUserId()).isEqualTo(currentUser.getUserId());
            assertThat(message.getContent()).isEqualTo("처음 문의드립니다.");

            verify(notificationService)
                    .createInquiryNotification(targetUser.getUserId(), currentUser.getNickname());
        }

        @Test
        @DisplayName("본인에게 문의할 수 없다")
        void createInquiry_selfTarget() {
            // given
            CreateInquiryRequestDto request =
                    createInquiryRequest(currentUser.getUserId(), "문의드립니다.");

            // when & then
            assertThatThrownBy(() -> inquiryService.createInquiry(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INQUIRY_REQUEST);
        }

        @Test
        @DisplayName("존재하지 않는 사용자에게 문의할 수 없다")
        void createInquiry_targetUserNotFound() {
            // given
            CreateInquiryRequestDto request =
                    createInquiryRequest(999L, "문의드립니다.");

            // when & then
            assertThatThrownBy(() -> inquiryService.createInquiry(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("기술자가 아닌 사용자에게 문의할 수 없다")
        void createInquiry_targetIsNotEngineer() {
            // given
            User targetUser = saveUser(900000000002L, "일반사용자");
            CreateInquiryRequestDto request =
                    createInquiryRequest(targetUser.getUserId(), "문의드립니다.");

            // when & then
            assertThatThrownBy(() -> inquiryService.createInquiry(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ENGINEER_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 문의방이 있으면 새 문의방을 만들 수 없다")
        void createInquiry_alreadyExists() {
            // given
            User targetUser = saveUser(900000000002L, "김기술");
            saveEngineer(targetUser, "01011112222", "profile.jpg");
            saveInquiry(targetUser, currentUser);
            CreateInquiryRequestDto request =
                    createInquiryRequest(targetUser.getUserId(), "다시 문의드립니다.");

            // when & then
            assertThatThrownBy(() -> inquiryService.createInquiry(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INQUIRY_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("내용이 없는 메시지로 문의할 수 없다")
        void createInquiry_blankMessage() {
            // given
            User targetUser = saveUser(900000000002L, "김기술");
            saveEngineer(targetUser, "01011112222", "profile.jpg");
            CreateInquiryRequestDto request =
                    createInquiryRequest(targetUser.getUserId(), "   ");

            // when & then
            assertThatThrownBy(() -> inquiryService.createInquiry(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INQUIRY_REQUEST);
        }
    }

    @Nested
    @DisplayName("문의방 메시지 전송")
    class SendMessageInRoom {

        @Test
        @DisplayName("문의 참여자는 문의방에 메시지를 보낼 수 있다")
        void sendMessageInRoom_success() {
            // given
            User targetUser = saveUser(900000000002L, "문의대상");
            Inquiry inquiry = saveInquiry(currentUser, targetUser);
            InquiryMessage firstMessage = saveMessage(inquiry, targetUser, "기존 메시지");
            inquiry.updateLastMessage(firstMessage.getInquiryMessageId());
            SendInquiryMessageRequestDto request =
                    sendInquiryMessageRequest("  새 메시지입니다.  ");

            entityManager.flush();
            entityManager.clear();

            // when
            InquiryDetailMessageResponseDto result =
                    inquiryService.sendMessageInRoom(inquiry.getInquiryId(), request);

            // then
            assertThat(result.getMessageId()).isNotNull();
            assertThat(result.getSenderUserId()).isEqualTo(currentUser.getUserId());
            assertThat(result.getContent()).isEqualTo("새 메시지입니다.");
            assertThat(result.getCreatedAt()).isNotNull();

            Inquiry updatedInquiry = inquiryRepository.findById(inquiry.getInquiryId())
                    .orElseThrow();
            assertThat(updatedInquiry.getLastMessageId()).isEqualTo(result.getMessageId());
            assertThat(updatedInquiry.getRequesterLastReadMessageId()).isEqualTo(result.getMessageId());
            assertThat(updatedInquiry.getTargetLastReadMessageId()).isNull();
            assertThat(updatedInquiry.getLastMessageAt()).isNotNull();

            verify(notificationService)
                    .createInquiryNotification(targetUser.getUserId(), currentUser.getNickname());
        }

        @Test
        @DisplayName("문의 참여자가 아닌 사용자는 문의방에 메시지를 보낼 수 없다")
        void sendMessageInRoom_notParticipant() {
            // given
            User requester = saveUser(900000000002L, "문의요청자");
            User target = saveUser(900000000003L, "문의대상");
            Inquiry inquiry = saveInquiry(requester, target);
            SendInquiryMessageRequestDto request =
                    sendInquiryMessageRequest("메시지입니다.");

            // when & then
            assertThatThrownBy(() ->
                    inquiryService.sendMessageInRoom(inquiry.getInquiryId(), request)
            )
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INQUIRY_FORBIDDEN);
        }

        @Test
        @DisplayName("존재하지 않는 문의방에는 메시지를 보낼 수 없다")
        void sendMessageInRoom_inquiryNotFound() {
            // given
            SendInquiryMessageRequestDto request =
                    sendInquiryMessageRequest("메시지입니다.");

            // when & then
            assertThatThrownBy(() -> inquiryService.sendMessageInRoom(999L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
        }

        @Test
        @DisplayName("내용이 없는 메시지는 문의방에 보낼 수 없다")
        void sendMessageInRoom_blankMessage() {
            // given
            User target = saveUser(900000000002L, "문의대상");
            Inquiry inquiry = saveInquiry(currentUser, target);
            SendInquiryMessageRequestDto request =
                    sendInquiryMessageRequest("   ");

            // when & then
            assertThatThrownBy(() ->
                    inquiryService.sendMessageInRoom(inquiry.getInquiryId(), request)
            )
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_INQUIRY_REQUEST);
        }
    }

    @Nested
    @DisplayName("문의방 나가기")
    class LeaveInquiry {

        @Test
        @DisplayName("문의 요청자는 문의방을 나갈 수 있다")
        void leaveInquiry_requester() {
            // given
            User target = saveUser(900000000002L, "문의대상");
            Inquiry inquiry = saveInquiry(currentUser, target);

            // when
            inquiryService.leaveInquiry(inquiry.getInquiryId());

            // then
            Inquiry updatedInquiry = inquiryRepository.findById(inquiry.getInquiryId())
                    .orElseThrow();
            assertThat(updatedInquiry.getRequesterLeftAt()).isNotNull();
            assertThat(updatedInquiry.getTargetLeftAt()).isNull();
            assertThat(updatedInquiry.getInquiryStatus()).isEqualTo(InquiryStatus.OPEN);
        }

        @Test
        @DisplayName("문의 대상자는 문의방을 나갈 수 있다")
        void leaveInquiry_target() {
            // given
            User requester = saveUser(900000000002L, "문의요청자");
            Inquiry inquiry = saveInquiry(requester, currentUser);

            // when
            inquiryService.leaveInquiry(inquiry.getInquiryId());

            // then
            Inquiry updatedInquiry = inquiryRepository.findById(inquiry.getInquiryId())
                    .orElseThrow();
            assertThat(updatedInquiry.getRequesterLeftAt()).isNull();
            assertThat(updatedInquiry.getTargetLeftAt()).isNotNull();
            assertThat(updatedInquiry.getInquiryStatus()).isEqualTo(InquiryStatus.OPEN);
        }

        @Test
        @DisplayName("양쪽 사용자가 모두 나가면 문의방은 종료된다")
        void leaveInquiry_closesWhenBothParticipantsLeave() {
            // given
            User target = saveUser(900000000002L, "문의대상");
            Inquiry inquiry = saveInquiry(currentUser, target);
            inquiry.leave(target.getUserId());

            // when
            inquiryService.leaveInquiry(inquiry.getInquiryId());

            // then
            Inquiry updatedInquiry = inquiryRepository.findById(inquiry.getInquiryId())
                    .orElseThrow();
            assertThat(updatedInquiry.getRequesterLeftAt()).isNotNull();
            assertThat(updatedInquiry.getTargetLeftAt()).isNotNull();
            assertThat(updatedInquiry.getInquiryStatus()).isEqualTo(InquiryStatus.CLOSED);
        }

        @Test
        @DisplayName("문의 참여자가 아닌 사용자는 문의방을 나갈 수 없다")
        void leaveInquiry_notParticipant() {
            // given
            User requester = saveUser(900000000002L, "문의요청자");
            User target = saveUser(900000000003L, "문의대상");
            Inquiry inquiry = saveInquiry(requester, target);

            // when & then
            assertThatThrownBy(() -> inquiryService.leaveInquiry(inquiry.getInquiryId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
        }

        @Test
        @DisplayName("존재하지 않는 문의방은 나갈 수 없다")
        void leaveInquiry_inquiryNotFound() {
            // when & then
            assertThatThrownBy(() -> inquiryService.leaveInquiry(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("문의 상세 조회")
    class GetInquiryDetail {

        @Test
        @DisplayName("문의 요청자는 문의 상세 정보를 조회할 수 있다")
        void getInquiryDetail_requester() {
            // given
            User target = saveUser(900000000002L, "문의대상");
            Inquiry inquiry = saveInquiry(currentUser, target);

            // when
            InquiryDetailResponseDto result =
                    inquiryService.getInquiryDetail(inquiry.getInquiryId());

            // then
            assertThat(result.getInquiryId()).isEqualTo(inquiry.getInquiryId());
            assertThat(result.getStatus()).isEqualTo(InquiryStatus.OPEN);
            assertThat(result.getRequesterUserId()).isEqualTo(currentUser.getUserId());
            assertThat(result.getTargetUserId()).isEqualTo(target.getUserId());
        }

        @Test
        @DisplayName("문의 대상자는 문의 상세 정보를 조회할 수 있다")
        void getInquiryDetail_target() {
            // given
            User requester = saveUser(900000000002L, "문의요청자");
            Inquiry inquiry = saveInquiry(requester, currentUser);

            // when
            InquiryDetailResponseDto result =
                    inquiryService.getInquiryDetail(inquiry.getInquiryId());

            // then
            assertThat(result.getInquiryId()).isEqualTo(inquiry.getInquiryId());
            assertThat(result.getRequesterUserId()).isEqualTo(requester.getUserId());
            assertThat(result.getTargetUserId()).isEqualTo(currentUser.getUserId());
        }

        @Test
        @DisplayName("문의 참여자가 아닌 사용자는 문의 상세 정보를 조회할 수 없다")
        void getInquiryDetail_notParticipant() {
            // given
            User requester = saveUser(900000000002L, "문의요청자");
            User target = saveUser(900000000003L, "문의대상");
            Inquiry inquiry = saveInquiry(requester, target);

            // when & then
            assertThatThrownBy(() -> inquiryService.getInquiryDetail(inquiry.getInquiryId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
        }

        @Test
        @DisplayName("존재하지 않는 문의 상세 정보는 조회할 수 없다")
        void getInquiryDetail_inquiryNotFound() {
            // when & then
            assertThatThrownBy(() -> inquiryService.getInquiryDetail(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("문의 메시지 조회")
    class GetInquiryMessages {

        @Test
        @DisplayName("최신 문의 메시지를 시간순으로 조회하고 다음 커서와 읽음 위치를 갱신한다")
        void getInquiryMessages_firstPage() {
            // given
            User target = saveUser(900000000002L, "문의대상");
            Inquiry inquiry = saveInquiry(currentUser, target);
            InquiryMessage firstMessage =
                    saveMessage(inquiry, currentUser, "첫 번째 메시지");
            InquiryMessage secondMessage =
                    saveMessage(inquiry, target, "두 번째 메시지");
            InquiryMessage thirdMessage =
                    saveMessage(inquiry, currentUser, "세 번째 메시지");

            entityManager.flush();
            entityManager.clear();

            // when
            InquiryMessagePageResponseDto result =
                    inquiryService.getInquiryMessages(inquiry.getInquiryId(), null, 2);

            // then
            assertThat(result.getItems())
                    .extracting(item -> item.getMessageId())
                    .containsExactly(
                            secondMessage.getInquiryMessageId(),
                            thirdMessage.getInquiryMessageId()
                    );
            assertThat(result.getItems())
                    .extracting(item -> item.getContent())
                    .containsExactly("두 번째 메시지", "세 번째 메시지");
            assertThat(result.getItems())
                    .extracting(item -> item.getSenderUserId())
                    .containsExactly(target.getUserId(), currentUser.getUserId());
            assertThat(result.getItems())
                    .allSatisfy(item -> assertThat(item.getCreatedAt()).isNotNull());
            assertThat(result.isHasNext()).isTrue();
            assertThat(result.getNextCursor()).isEqualTo(secondMessage.getInquiryMessageId());

            Inquiry updatedInquiry = inquiryRepository.findById(inquiry.getInquiryId())
                    .orElseThrow();
            assertThat(updatedInquiry.getRequesterLastReadMessageId())
                    .isEqualTo(thirdMessage.getInquiryMessageId());
            assertThat(firstMessage.getInquiryMessageId())
                    .isLessThan(result.getNextCursor());
        }

        @Test
        @DisplayName("다음 커서로 이전 문의 메시지를 중복 없이 조회할 수 있다")
        void getInquiryMessages_nextCursor() {
            // given
            User target = saveUser(900000000002L, "문의대상");
            Inquiry inquiry = saveInquiry(currentUser, target);
            InquiryMessage firstMessage =
                    saveMessage(inquiry, currentUser, "첫 번째 메시지");
            InquiryMessage secondMessage =
                    saveMessage(inquiry, target, "두 번째 메시지");
            InquiryMessage thirdMessage =
                    saveMessage(inquiry, currentUser, "세 번째 메시지");
            InquiryMessage fourthMessage =
                    saveMessage(inquiry, target, "네 번째 메시지");

            entityManager.flush();
            entityManager.clear();

            InquiryMessagePageResponseDto firstPage =
                    inquiryService.getInquiryMessages(inquiry.getInquiryId(), null, 2);

            // when
            InquiryMessagePageResponseDto secondPage =
                    inquiryService.getInquiryMessages(
                            inquiry.getInquiryId(),
                            firstPage.getNextCursor(),
                            2
                    );

            // then
            assertThat(firstPage.getItems())
                    .extracting(item -> item.getMessageId())
                    .containsExactly(
                            thirdMessage.getInquiryMessageId(),
                            fourthMessage.getInquiryMessageId()
                    );
            assertThat(secondPage.getItems())
                    .extracting(item -> item.getMessageId())
                    .containsExactly(
                            firstMessage.getInquiryMessageId(),
                            secondMessage.getInquiryMessageId()
                    );
            assertThat(secondPage.isHasNext()).isFalse();
            assertThat(secondPage.getNextCursor()).isNull();
        }

        @Test
        @DisplayName("문의 대상자가 메시지를 조회하면 대상자의 읽음 위치가 갱신된다")
        void getInquiryMessages_updatesTargetReadPosition() {
            // given
            User requester = saveUser(900000000002L, "문의요청자");
            Inquiry inquiry = saveInquiry(requester, currentUser);
            InquiryMessage message =
                    saveMessage(inquiry, requester, "문의 메시지");

            entityManager.flush();
            entityManager.clear();

            // when
            InquiryMessagePageResponseDto result =
                    inquiryService.getInquiryMessages(inquiry.getInquiryId(), null, 15);

            // then
            assertThat(result.getItems()).hasSize(1);
            Inquiry updatedInquiry = inquiryRepository.findById(inquiry.getInquiryId())
                    .orElseThrow();
            assertThat(updatedInquiry.getTargetLastReadMessageId())
                    .isEqualTo(message.getInquiryMessageId());
        }

        @Test
        @DisplayName("문의 참여자가 아닌 사용자는 문의 메시지를 조회할 수 없다")
        void getInquiryMessages_notParticipant() {
            // given
            User requester = saveUser(900000000002L, "문의요청자");
            User target = saveUser(900000000003L, "문의대상");
            Inquiry inquiry = saveInquiry(requester, target);

            // when & then
            assertThatThrownBy(() ->
                    inquiryService.getInquiryMessages(inquiry.getInquiryId(), null, 15)
            )
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
        }

        @Test
        @DisplayName("존재하지 않는 문의방의 메시지는 조회할 수 없다")
        void getInquiryMessages_inquiryNotFound() {
            // when & then
            assertThatThrownBy(() -> inquiryService.getInquiryMessages(999L, null, 15))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
        }
    }

    private Inquiry saveInquiry(User requester, User target) {
        return inquiryRepository.save(Inquiry.create(requester, target));
    }

    private InquiryMessage saveMessage(Inquiry inquiry, User sender, String content) {
        return inquiryMessageRepository.save(
                InquiryMessage.create(inquiry, sender, content)
        );
    }

    private User saveUser(Long kakaoId, String nickname) {
        return userRepository.save(User.builder()
                .region(region)
                .kakaoId(kakaoId)
                .nickname(nickname)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());
    }

    private Engineer saveEngineer(User user, String phone, String profileImage) {
        return engineerRepository.save(Engineer.create(
                user,
                region,
                profileImage,
                "기존 소개",
                "기존 상호",
                "기존 주소",
                phone,
                true,
                "기술자",
                "기술자 소개"
        ));
    }

    private CreateInquiryRequestDto createInquiryRequest(Long targetUserId, String message) {
        CreateInquiryRequestDto request = new CreateInquiryRequestDto();
        ReflectionTestUtils.setField(request, "targetUserId", targetUserId);
        ReflectionTestUtils.setField(request, "message", message);
        return request;
    }

    private SendInquiryMessageRequestDto sendInquiryMessageRequest(String message) {
        SendInquiryMessageRequestDto request = new SendInquiryMessageRequestDto();
        ReflectionTestUtils.setField(request, "message", message);
        return request;
    }
}

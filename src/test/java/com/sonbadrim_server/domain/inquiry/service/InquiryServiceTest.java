package com.sonbadrim_server.domain.inquiry.service;

import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.engineer.repository.EngineerRepository;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryMessagePageResponseDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryDetailResponseDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryListResponseDto;
import com.sonbadrim_server.domain.inquiry.entity.Inquiry;
import com.sonbadrim_server.domain.inquiry.entity.InquiryMessage;
import com.sonbadrim_server.domain.inquiry.entity.InquiryStatus;
import com.sonbadrim_server.domain.inquiry.repository.InquiryMessageRepository;
import com.sonbadrim_server.domain.inquiry.repository.InquiryRepository;
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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

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
}

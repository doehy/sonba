package com.sonbadream_server.domain.inquiry.service;

import com.sonbadream_server.domain.engineer.repository.EngineerRepository;
import com.sonbadream_server.domain.inquiry.dto.request.CreateInquiryRequestDto;
import com.sonbadream_server.domain.inquiry.dto.request.SendInquiryMessageRequestDto;
import com.sonbadream_server.domain.inquiry.dto.response.InquiryDetailMessageResponseDto;
import com.sonbadream_server.domain.inquiry.dto.response.InquiryDetailResponseDto;
import com.sonbadream_server.domain.inquiry.dto.response.InquiryListItemResponseDto;
import com.sonbadream_server.domain.inquiry.dto.response.InquiryListResponseDto;
import com.sonbadream_server.domain.inquiry.dto.response.InquiryMessagePageResponseDto;
import com.sonbadream_server.domain.inquiry.dto.response.InquiryMessageResponseDto;
import com.sonbadream_server.domain.inquiry.entity.Inquiry;
import com.sonbadream_server.domain.inquiry.entity.InquiryMessage;
import com.sonbadream_server.domain.inquiry.repository.InquiryMessageRepository;
import com.sonbadream_server.domain.inquiry.repository.InquiryRepository;
import com.sonbadream_server.domain.notification.service.NotificationService;
import com.sonbadream_server.domain.user.entity.User;
import com.sonbadream_server.domain.user.repository.UserRepository;
import com.sonbadream_server.domain.user.service.UserService;
import com.sonbadream_server.global.exception.CustomException;
import com.sonbadream_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InquiryService {
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final InquiryRepository inquiryRepository;
    private final InquiryMessageRepository inquiryMessageRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final EngineerRepository engineerRepository;
    private final NotificationService notificationService;
    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 15;
    private static final int MAX_MESSAGE_PAGE_SIZE = 50;

    /**
     * 문의 목록을 조회
     */
    @Transactional(readOnly = true)
    public InquiryListResponseDto getInquiries(int page, int size) {
        User currentUser = userService.getCurrentUser(); // 현재 사용자가 누구인지

        Page<Inquiry> inquiryPage = inquiryRepository
                .findVisibleByUserId(currentUser.getUserId(), PageRequest.of(page, size));
        //마지막 메시지 id를 다 가져와라
        Set<Long> lastMessageIds = inquiryPage.getContent().stream()
                .map(Inquiry::getLastMessageId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 마지막 메시지가 비어있으면 빈 맵을 그게 아니라면 마지막 메시지 id에 해당하는 마지막 메시지를 가져와라 그걸 기반으로 map 만들기
        Map<Long, InquiryMessage> messageMap = lastMessageIds.isEmpty()
                ? Map.of()
                : inquiryMessageRepository.findByInquiryMessageIdIn(lastMessageIds)
                .stream()
                .collect(Collectors.toMap(InquiryMessage::getInquiryMessageId, Function.identity()));

        Set<Long> inquiryIds = inquiryPage.getContent().stream()
                .map(Inquiry::getInquiryId)
                .collect(Collectors.toSet());

        Map<Long, Long> unreadCountMap = inquiryIds.isEmpty()
                ? Map.of()
                : inquiryMessageRepository.countUnreadByInquiryIds(inquiryIds, currentUser.getUserId())
                .stream()
                .collect(Collectors.toMap(
                        InquiryMessageRepository.UnreadCountProjection::getInquiryId,
                        InquiryMessageRepository.UnreadCountProjection::getUnreadCount
                ));

        List<InquiryListItemResponseDto> items = inquiryPage.getContent().stream()
                .map(inquiry -> {
                    User otherUser = inquiry.getRequester().getUserId().equals(currentUser.getUserId())
                            ? inquiry.getTarget()
                            : inquiry.getRequester(); // 상대방이 누구인지 찾아라.

                    Long lastMessageId = inquiry.getLastMessageId(); // 마지막 메시지 아이디를 꺼내
                    InquiryMessage lastMessage = messageMap.get(lastMessageId); // 마지막 메시지를 꺼내
                    long unreadCount = unreadCountMap.getOrDefault(inquiry.getInquiryId(), 0L);

                    String otherUserProfileImage = otherUser.getEngineer() != null // 프로필 나타내주기
                            ? otherUser.getEngineer().getProfileImage()
                            : null;

                    return new InquiryListItemResponseDto( // dto
                            inquiry.getInquiryId(),
                            otherUser.getNickname(),
                            otherUserProfileImage,
                            lastMessage.getContent(),
                            inquiry.getLastMessageAt(),
                            unreadCount
                    );
                })
                .toList();

        return new InquiryListResponseDto(
                items,
                inquiryPage.hasNext()
        );
    }

    /**
     * 문의 상세 조회 (메타 정보만)
     */
    public InquiryDetailResponseDto getInquiryDetail(Long inquiryId) {
        User currentUser = userService.getCurrentUser();

        Inquiry inquiry = inquiryRepository.findByInquiryIdAndParticipant(inquiryId, currentUser.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.INQUIRY_NOT_FOUND));

        return new InquiryDetailResponseDto(
                inquiry.getInquiryId(),
                inquiry.getInquiryStatus(),
                inquiry.getRequester().getUserId(),
                inquiry.getTarget().getUserId()
        );
    }

    /**
     * 문의 상세 조회(메시지 페이징)
     */
    public InquiryMessagePageResponseDto getInquiryMessages(Long inquiryId, Long cursor, int size) {
        User currentUser = userService.getCurrentUser();

        Inquiry inquiry = inquiryRepository.findByInquiryIdAndParticipant(inquiryId, currentUser.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.INQUIRY_NOT_FOUND));

        int pageSize = normalizeMessagePageSize(size);
        Pageable pageable = PageRequest.of(0, pageSize + 1);

        List<InquiryMessage> queryResult = cursor == null
                ? inquiryMessageRepository.findByInquiry_InquiryIdOrderByInquiryMessageIdDesc(inquiryId, pageable)
                : inquiryMessageRepository.findByInquiry_InquiryIdAndInquiryMessageIdLessThanOrderByInquiryMessageIdDesc(
                inquiryId, cursor, pageable
        );

        boolean hasNext = queryResult.size() > pageSize;
        List<InquiryMessage> pageMessages = hasNext
                ? queryResult.subList(0, pageSize)
                : queryResult;

        pageMessages.sort(Comparator.comparing(InquiryMessage::getInquiryMessageId));

        List<InquiryDetailMessageResponseDto> items = pageMessages.stream()
                .map(this::toDetailMessageResponse)
                .toList();

        if (!pageMessages.isEmpty()) {
            Long lastMessageId = pageMessages.get(pageMessages.size() - 1).getInquiryMessageId();
            Long currentLastReadMessageId = inquiry.getLastReadMessageIdOf(currentUser.getUserId());
            if (currentLastReadMessageId == null || lastMessageId > currentLastReadMessageId) {
                inquiry.updateLastReadMessageId(currentUser.getUserId(), lastMessageId);
            }
        }

        Long nextCursor = hasNext
                ? pageMessages.get(0).getInquiryMessageId()
                : null;

        return new InquiryMessagePageResponseDto(items, hasNext, nextCursor);
    }

    /**
     * 최초 문의 보내기
     */
    public InquiryMessageResponseDto createInquiry(CreateInquiryRequestDto requestDto) {
        User requester = userService.getCurrentUser();

        if (requester.getUserId().equals(requestDto.getTargetUserId())) {
            throw new CustomException(ErrorCode.INVALID_INQUIRY_REQUEST);
        }

        User targetUser = userRepository.findById(requestDto.getTargetUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!engineerRepository.existsByUser_UserId(targetUser.getUserId())) {
            throw new CustomException(ErrorCode.ENGINEER_NOT_FOUND);
        }

        boolean existsForward = inquiryRepository
                .findByRequester_UserIdAndTarget_UserId(requester.getUserId(), targetUser.getUserId())
                .isPresent();
        boolean existsReverse = inquiryRepository
                .findByRequester_UserIdAndTarget_UserId(targetUser.getUserId(), requester.getUserId())
                .isPresent();
        if (existsForward || existsReverse) {
            throw new CustomException(ErrorCode.INQUIRY_ALREADY_EXISTS);
        }

        Inquiry inquiry = Inquiry.create(requester, targetUser);
        inquiryRepository.save(inquiry);

        String normalizedMessage = validateAndNormalizeMessage(requestDto.getMessage());
        InquiryMessage firstMessage = InquiryMessage.create(inquiry, requester, normalizedMessage);
        inquiryMessageRepository.save(firstMessage);

        inquiry.updateLastMessage(firstMessage.getInquiryMessageId());
        inquiry.updateLastReadMessageId(requester.getUserId(), firstMessage.getInquiryMessageId());
        notificationService.createInquiryNotification(targetUser.getUserId(), requester.getNickname());

        return new InquiryMessageResponseDto(inquiry.getInquiryId(), firstMessage.getInquiryMessageId());
    }

    /**
     * 문의방 안에서 문의 보내기
     */
    public InquiryDetailMessageResponseDto sendMessageInRoom(Long inquiryId, SendInquiryMessageRequestDto requestDto) {
        User sender = userService.getCurrentUser();

        Inquiry inquiry = inquiryRepository.findByIdForUpdate(inquiryId)
                .orElseThrow(() -> new CustomException(ErrorCode.INQUIRY_NOT_FOUND));

        boolean isParticipant = inquiry.getRequester().getUserId().equals(sender.getUserId())
                || inquiry.getTarget().getUserId().equals(sender.getUserId());
        if (!isParticipant) {
            throw new CustomException(ErrorCode.INQUIRY_FORBIDDEN);
        }

        String normalizedMessage = validateAndNormalizeMessage(requestDto.getMessage());
        InquiryMessage message = InquiryMessage.create(inquiry, sender, normalizedMessage);
        inquiryMessageRepository.save(message);

        inquiry.updateLastMessage(message.getInquiryMessageId());
        inquiry.updateLastReadMessageId(sender.getUserId(), message.getInquiryMessageId());
        User receiver = inquiry.getRequester().getUserId().equals(sender.getUserId())
                ? inquiry.getTarget()
                : inquiry.getRequester();
        notificationService.createInquiryNotification(receiver.getUserId(), sender.getNickname());

        return toDetailMessageResponse(message);
    }

    /**
     * 문의방 나가기
     */
    public void leaveInquiry(Long inquiryId) {
        User currentUser = userService.getCurrentUser();
        Inquiry inquiry = inquiryRepository.findByInquiryIdAndParticipant(inquiryId, currentUser.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.INQUIRY_NOT_FOUND));

        inquiry.leave(currentUser.getUserId());
    }

    private String validateAndNormalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INQUIRY_REQUEST);
        }

        String normalized = message.trim();
        int length = normalized.codePointCount(0, normalized.length());
        if (length > MAX_MESSAGE_LENGTH) {
            throw new CustomException(ErrorCode.INQUIRY_MESSAGE_TOO_LONG);
        }
        return normalized;
    }

    private int normalizeMessagePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_MESSAGE_PAGE_SIZE;
        }
        return Math.min(size, MAX_MESSAGE_PAGE_SIZE);
    }

    private InquiryDetailMessageResponseDto toDetailMessageResponse(InquiryMessage message) {
        return new InquiryDetailMessageResponseDto(
                message.getInquiryMessageId(),
                message.getSender().getUserId(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}

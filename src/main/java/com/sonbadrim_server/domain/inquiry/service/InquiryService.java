package com.sonbadrim_server.domain.inquiry.service;

import com.sonbadrim_server.domain.engineer.repository.EngineerRepository;
import com.sonbadrim_server.domain.inquiry.dto.request.CreateInquiryRequestDto;
import com.sonbadrim_server.domain.inquiry.dto.request.SendInquiryMessageRequestDto;
import com.sonbadrim_server.domain.inquiry.dto.response.InquiryMessageResponseDto;
import com.sonbadrim_server.domain.inquiry.entity.Inquiry;
import com.sonbadrim_server.domain.inquiry.entity.InquiryMessage;
import com.sonbadrim_server.domain.inquiry.repository.InquiryMessageRepository;
import com.sonbadrim_server.domain.inquiry.repository.InquiryRepository;
import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import com.sonbadrim_server.domain.user.service.UserService;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        return new InquiryMessageResponseDto(inquiry.getInquiryId(), firstMessage.getInquiryMessageId());
    }

    public InquiryMessageResponseDto sendMessageInRoom(Long inquiryId, SendInquiryMessageRequestDto requestDto) {
        User sender = userService.getCurrentUser();

        Inquiry inquiry = inquiryRepository.findById(inquiryId)
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

        return new InquiryMessageResponseDto(inquiry.getInquiryId(), message.getInquiryMessageId());
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
}

package com.sonbadream_server.domain.block.service;

import com.sonbadream_server.domain.block.dto.request.BlockCreateRequestDto;
import com.sonbadream_server.domain.block.dto.response.BlockCreateResponseDto;
import com.sonbadream_server.domain.block.dto.response.BlockListItemResponseDto;
import com.sonbadream_server.domain.block.dto.response.BlockListResponseDto;
import com.sonbadream_server.domain.block.entity.Block;
import com.sonbadream_server.domain.block.repository.BlockRepository;
import com.sonbadream_server.domain.user.entity.User;
import com.sonbadream_server.domain.user.repository.UserRepository;
import com.sonbadream_server.domain.user.service.UserService;
import com.sonbadream_server.global.exception.CustomException;
import com.sonbadream_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BlockService {

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public BlockCreateResponseDto blockUser(BlockCreateRequestDto requestDto) {
        User blocker = userService.getCurrentUser();

        if (blocker.getUserId().equals(requestDto.getBlockedUserId())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        User blocked = userRepository.findById(requestDto.getBlockedUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        boolean alreadyBlocked = blockRepository.existsByBlocker_UserIdAndBlocked_UserId(
                blocker.getUserId(),
                blocked.getUserId()
        );

        if (!alreadyBlocked) {
            blockRepository.save(Block.create(blocker, blocked));
        }

        return new BlockCreateResponseDto(blocked.getUserId(), !alreadyBlocked);
    }

    public void unblockUser(Long blockedUserId) {
        User blocker = userService.getCurrentUser();
        blockRepository.deleteByBlocker_UserIdAndBlocked_UserId(blocker.getUserId(), blockedUserId);
    }

    @Transactional(readOnly = true)
    public BlockListResponseDto getMyBlockedUsers(int page, int size) {
        User blocker = userService.getCurrentUser();
        Page<Block> blockPage = blockRepository.findByBlocker_UserIdOrderByCreatedAtDesc(
                blocker.getUserId(),
                PageRequest.of(page, size)
        );

        List<BlockListItemResponseDto> items = blockPage.getContent().stream()
                .map(block -> new BlockListItemResponseDto(
                        block.getBlocked().getUserId(),
                        block.getBlocked().getEngineer() != null
                                ? block.getBlocked().getEngineer().getEngineerName()
                                : block.getBlocked().getNickname(),
                        block.getBlocked().getEngineer() != null ? block.getBlocked().getEngineer().getProfileImage() : null,
                        block.getCreatedAt()
                ))
                .toList();

        return new BlockListResponseDto(items, blockPage.hasNext());
    }
}

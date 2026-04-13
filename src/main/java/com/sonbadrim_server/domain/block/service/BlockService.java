package com.sonbadrim_server.domain.block.service;

import com.sonbadrim_server.domain.block.dto.request.BlockCreateRequestDto;
import com.sonbadrim_server.domain.block.dto.response.BlockCreateResponseDto;
import com.sonbadrim_server.domain.block.entity.Block;
import com.sonbadrim_server.domain.block.repository.BlockRepository;
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
}

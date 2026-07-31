package com.sonbadrim_server.domain.user.dto.response;

import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.entity.UserRole;
import com.sonbadrim_server.domain.user.entity.UserStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserListItemResponseDto {

    private Long userId;
    private String nickname;
    private UserRole role;
    private UserStatus status;
    private boolean engineerRegistered;
    private LocalDateTime createdAt;

    public static UserListItemResponseDto from(User user) {
        Engineer engineer = user.getEngineer();

        return UserListItemResponseDto.builder()
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .role(user.getRole())
                .status(user.getStatus())
                .engineerRegistered(engineer != null)
                .createdAt(user.getCreatedAt())
                .build();
    }
}

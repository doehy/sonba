package com.sonbadrim_server.domain.user.dto.response;

import com.sonbadrim_server.domain.user.entity.Gender;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserProfileResponseDto {
    private String city;
    private String district;
    private String nickname;
    private LocalDate birth;
    private Gender gender;
    private boolean engineerRegistered;
}

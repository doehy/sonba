package com.sonbadrim_server.domain.engineer.service;

import com.sonbadrim_server.domain.category.repository.CategoryRepository;
import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.engineer.entity.InterestedEngineer;
import com.sonbadrim_server.domain.engineer.repository.EngineerCategoryRepository;
import com.sonbadrim_server.domain.engineer.repository.EngineerRepository;
import com.sonbadrim_server.domain.engineer.repository.InterestedEngineerRepository;
import com.sonbadrim_server.domain.image.service.ImagePolicyValidator;
import com.sonbadrim_server.domain.portfolio.repository.PortfolioRepository;
import com.sonbadrim_server.domain.region.repository.RegionRepository;
import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.entity.UserRole;
import com.sonbadrim_server.domain.user.entity.UserStatus;
import com.sonbadrim_server.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class EngineerServiceUnitTest {

    @InjectMocks
    private EngineerService engineerService;

    @Mock
    private EngineerRepository engineerRepository;

    @Mock
    private EngineerCategoryRepository engineerCategoryRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private UserService userService;

    @Mock
    private InterestedEngineerRepository interestedEngineerRepository;

    @Mock
    private ImagePolicyValidator imagePolicyValidator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("동시에 같은 기술자를 관심 등록해 unique 예외가 발생해도 이미 등록된 상태면 성공 처리한다")
    void addInterestedEngineer_duplicateKeyRaceTreatsAsSuccess() {
        // given
        User user = User.builder()
                .userId(1L)
                .kakaoId(1000L)
                .nickname("사용자")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        User engineerUser = User.builder()
                .userId(2L)
                .kakaoId(2000L)
                .nickname("기술자사용자")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        Engineer engineer = Engineer.create(
                engineerUser,
                null,
                null,
                null,
                null,
                null,
                "01012345678",
                "기술자",
                null
        );
        Long engineerId = 10L;

        given(userService.getCurrentUser()).willReturn(user);
        given(engineerRepository.findById(engineerId)).willReturn(Optional.of(engineer));
        given(interestedEngineerRepository.existsByUser_UserIdAndEngineer_EngineerId(user.getUserId(), engineerId))
                .willReturn(false, true);
        given(interestedEngineerRepository.saveAndFlush(any(InterestedEngineer.class)))
                .willThrow(new DataIntegrityViolationException("duplicate interested engineer"));

        // when & then
        assertThatCode(() -> engineerService.addInterestedEngineer(engineerId))
                .doesNotThrowAnyException();

        then(interestedEngineerRepository).should()
                .saveAndFlush(any(InterestedEngineer.class));
    }

    @Test
    @DisplayName("사용자와 연결되지 않은 수동 등록 기술자도 관심 등록할 수 있다")
    void addInterestedEngineer_adminCreatedEngineer() {
        // given
        User user = User.builder()
                .userId(1L)
                .kakaoId(1000L)
                .nickname("사용자")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        Engineer engineer = Engineer.createAdminCreated(
                null,
                null,
                null,
                null,
                null,
                "01012345678",
                "수동기술자",
                null
        );
        Long engineerId = 10L;

        given(userService.getCurrentUser()).willReturn(user);
        given(engineerRepository.findById(engineerId)).willReturn(Optional.of(engineer));
        given(interestedEngineerRepository.existsByUser_UserIdAndEngineer_EngineerId(user.getUserId(), engineerId))
                .willReturn(false);

        // when & then
        assertThatCode(() -> engineerService.addInterestedEngineer(engineerId))
                .doesNotThrowAnyException();

        then(interestedEngineerRepository).should()
                .saveAndFlush(any(InterestedEngineer.class));
    }
}

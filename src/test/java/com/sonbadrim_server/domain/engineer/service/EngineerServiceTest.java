package com.sonbadrim_server.domain.engineer.service;

import com.sonbadrim_server.domain.engineer.dto.request.EngineerRequestDto;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerCreateResponse;
import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.engineer.entity.EngineerCategory;
import com.sonbadrim_server.domain.engineer.repository.EngineerCategoryRepository;
import com.sonbadrim_server.domain.engineer.repository.EngineerRepository;
import com.sonbadrim_server.domain.portfolio.entity.Portfolio;
import com.sonbadrim_server.domain.portfolio.repository.PortfolioRepository;
import com.sonbadrim_server.domain.region.entity.Region;
import com.sonbadrim_server.domain.region.repository.RegionRepository;
import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.entity.UserRole;
import com.sonbadrim_server.domain.user.entity.UserStatus;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import com.sonbadrim_server.domain.user.service.UserService;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/sql/engineer-test-data.sql")
class EngineerServiceTest {

    @Autowired
    private EngineerService engineerService;

    @Autowired
    private EngineerRepository engineerRepository;

    @Autowired
    private EngineerCategoryRepository engineerCategoryRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private UserService userService;

    private User currentUser;
    private Region region;

    @BeforeEach
    void setUp() {
        region = regionRepository.findAllByOrderByRegionIdAsc().getFirst();
        currentUser = saveUser(900000000001L, "테스트유저");

        given(userService.getCurrentUser()).willReturn(currentUser);
    }

    @Nested
    @DisplayName("기술자 등록")
    class RegisterEngineer {

        @Test
        @DisplayName("유효한 요청이면 기술자와 카테고리, 포트폴리오를 저장한다")
        void registerEngineer_success() {
            // given
            EngineerRequestDto request = createRequest(
                    List.of("주방", "타일"),
                    List.of("portfolio1.jpg", "portfolio2.jpg"),
                    "010-1234-5678"
            );

            // when
            EngineerCreateResponse response = engineerService.registerEngineer(request);

            // then
            Engineer savedEngineer = engineerRepository.findById(response.getEngineerId())
                    .orElseThrow();
            List<EngineerCategory> savedCategories =
                    engineerCategoryRepository.findByEngineer_EngineerIdIn(List.of(savedEngineer.getEngineerId()));
            List<Portfolio> savedPortfolios =
                    portfolioRepository.findByEngineer_EngineerId(savedEngineer.getEngineerId());

            assertThat(savedEngineer.getUser().getUserId()).isEqualTo(currentUser.getUserId());
            assertThat(savedEngineer.getRegion().getRegionId()).isEqualTo(region.getRegionId());
            assertThat(savedEngineer.getProfileImage()).isEqualTo("profile.jpg");
            assertThat(savedEngineer.getIntroduction()).isEqualTo("테스트 소개");
            assertThat(savedEngineer.getStoreName()).isEqualTo("테스트 상호");
            assertThat(savedEngineer.getStoreAddress()).isEqualTo("서울시 테스트구");
            assertThat(savedEngineer.getPhone()).isEqualTo("01012345678");
            assertThat(savedEngineer.isPhonePublic()).isTrue();
            assertThat(savedEngineer.getEngineerName()).isEqualTo("김기술");
            assertThat(savedEngineer.getHeadline()).isEqualTo("주방 시공 전문");

            assertThat(savedCategories)
                    .extracting(engineerCategory -> engineerCategory.getCategory().getCategoryName())
                    .containsExactlyInAnyOrder("주방", "타일");
            assertThat(savedPortfolios)
                    .extracting(Portfolio::getPortfolioImage)
                    .containsExactly("portfolio1.jpg", "portfolio2.jpg");
        }

        @Test
        @DisplayName("이미 기술자로 등록된 사용자는 다시 등록할 수 없다")
        void registerEngineer_alreadyRegistered() {
            // given
            Engineer existingEngineer = Engineer.create(
                    currentUser,
                    region,
                    null,
                    null,
                    null,
                    null,
                    "01011112222",
                    false,
                    "기존기술자",
                    "기존 소개"
            );
            engineerRepository.save(existingEngineer);
            EngineerRequestDto request = createValidRequest();

            // when & then
            assertThatThrownBy(() -> engineerService.registerEngineer(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ENGINEER_ALREADY_REGISTERED);

            assertThat(engineerRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("존재하지 않는 지역으로는 등록할 수 없다")
        void registerEngineer_regionNotFound() {
            // given
            EngineerRequestDto request = createRequestBuilder()
                    .regionId(999)
                    .build();

            // when & then
            assertThatThrownBy(() -> engineerService.registerEngineer(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REGION_NOT_FOUND);

            assertThat(engineerRepository.count()).isZero();
        }

        @Test
        @DisplayName("시공 분야가 없으면 등록할 수 없다")
        void registerEngineer_categoryIsEmpty() {
            // given
            EngineerRequestDto request = createRequestBuilder()
                    .categories(List.of())
                    .build();

            // when & then
            assertThatThrownBy(() -> engineerService.registerEngineer(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_ENGINEER_CATEGORY);

            assertThat(engineerRepository.count()).isZero();
        }

        @Test
        @DisplayName("공백으로만 이루어진 시공 분야는 등록할 수 없다")
        void registerEngineer_categoryIsBlank() {
            // given
            EngineerRequestDto request = createRequestBuilder()
                    .categories(List.of(" ", "  "))
                    .build();

            // when & then
            assertThatThrownBy(() -> engineerService.registerEngineer(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_ENGINEER_CATEGORY);

            assertThat(engineerRepository.count()).isZero();
        }

        @Test
        @DisplayName("존재하지 않는 시공 분야가 포함되면 등록할 수 없다")
        void registerEngineer_invalidCategory() {
            // given
            EngineerRequestDto request = createRequest(
                    List.of("주방", "없는분야"),
                    List.of("portfolio1.jpg"),
                    "010-1234-5678"
            );

            // when & then
            assertThatThrownBy(() -> engineerService.registerEngineer(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_ENGINEER_CATEGORY);

            assertThat(engineerRepository.count()).isZero();
            assertThat(engineerCategoryRepository.count()).isZero();
            assertThat(portfolioRepository.count()).isZero();
        }

        @Test
        @DisplayName("중복 시공 분야는 제거하고 한 번씩만 저장한다")
        void registerEngineer_duplicateCategories() {
            // given
            EngineerRequestDto request = createRequest(
                    List.of(" 주방 ", "주방", "타일"),
                    List.of("portfolio1.jpg"),
                    "010-1234-5678"
            );

            // when
            EngineerCreateResponse response = engineerService.registerEngineer(request);

            // then
            List<EngineerCategory> savedCategories =
                    engineerCategoryRepository.findByEngineer_EngineerIdIn(List.of(response.getEngineerId()));
            assertThat(savedCategories)
                    .extracting(engineerCategory -> engineerCategory.getCategory().getCategoryName())
                    .containsExactlyInAnyOrder("주방", "타일");
        }

        @Test
        @DisplayName("유효하지 않은 전화번호는 등록할 수 없다")
        void registerEngineer_invalidPhone() {
            // given
            EngineerRequestDto request = createRequest(
                    List.of("주방", "타일"),
                    List.of("portfolio1.jpg"),
                    "1234"
            );

            // when & then
            assertThatThrownBy(() -> engineerService.registerEngineer(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_PHONE_NUMBER);

            assertThat(engineerRepository.count()).isZero();
            assertThat(engineerCategoryRepository.count()).isZero();
            assertThat(portfolioRepository.count()).isZero();
        }

        @Test
        @DisplayName("빈 포트폴리오 URL과 중복 URL은 제거하고 저장한다")
        void registerEngineer_normalizesPortfolioImages() {
            // given
            EngineerRequestDto request = createRequest(
                    List.of("주방", "타일"),
                    List.of(" portfolio1.jpg ", " ", "portfolio1.jpg", "portfolio2.jpg"),
                    "010-1234-5678"
            );

            // when
            EngineerCreateResponse response = engineerService.registerEngineer(request);

            // then
            List<Portfolio> savedPortfolios =
                    portfolioRepository.findByEngineer_EngineerId(response.getEngineerId());
            assertThat(savedPortfolios)
                    .extracting(Portfolio::getPortfolioImage)
                    .containsExactly("portfolio1.jpg", "portfolio2.jpg");
        }
    }

    private EngineerRequestDto createValidRequest() {
        return createRequest(
                List.of("주방", "타일"),
                List.of("portfolio1.jpg", "portfolio2.jpg"),
                "010-1234-5678"
        );
    }

    private EngineerRequestDto createRequest(
            List<String> categories,
            List<String> portfolioImages,
            String phone
    ) {
        return createRequestBuilder()
                .categories(categories)
                .portfolioImages(portfolioImages)
                .phone(phone)
                .build();
    }

    private EngineerRequestDto.EngineerRequestDtoBuilder createRequestBuilder() {
        return EngineerRequestDto.builder()
                .categories(List.of("주방", "타일"))
                .portfolioImages(List.of("portfolio1.jpg", "portfolio2.jpg"))
                .introduction(" 테스트 소개 ")
                .regionId(region.getRegionId())
                .profileImage(" profile.jpg ")
                .storeName(" 테스트 상호 ")
                .storeAddress(" 서울시 테스트구 ")
                .phone("010-1234-5678")
                .phonePublic(true)
                .engineerName(" 김기술 ")
                .headline(" 주방 시공 전문 ");
    }

    private User saveUser(Long kakaoId, String nickname) {
        User user = User.builder()
                .region(region)
                .kakaoId(kakaoId)
                .nickname(nickname)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        return userRepository.save(user);
    }
}

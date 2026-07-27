package com.sonbadrim_server.domain.engineer.service;

import com.sonbadrim_server.domain.engineer.dto.request.EngineerRequestDto;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerCreateResponse;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerDetailResponseDto;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerSummaryResponseDto;
import com.sonbadrim_server.domain.engineer.dto.response.InterestedEngineerItemResponseDto;
import com.sonbadrim_server.domain.engineer.dto.response.PhoneDuplicateCheckResponseDto;
import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.engineer.entity.EngineerClaimStatus;
import com.sonbadrim_server.domain.engineer.entity.EngineerCategory;
import com.sonbadrim_server.domain.engineer.entity.EngineerRegistrationType;
import com.sonbadrim_server.domain.engineer.entity.InterestedEngineer;
import com.sonbadrim_server.domain.engineer.repository.EngineerCategoryRepository;
import com.sonbadrim_server.domain.engineer.repository.EngineerRepository;
import com.sonbadrim_server.domain.engineer.repository.InterestedEngineerRepository;
import com.sonbadrim_server.domain.category.entity.Category;
import com.sonbadrim_server.domain.category.repository.CategoryRepository;
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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "cloudflare.r2.public-domain=https://image.test")
@Transactional
@Sql("/sql/engineer-test-data.sql")
class EngineerServiceTest {

    private static final String PROFILE_IMAGE = imageUrl("profile.jpg");
    private static final String OLD_PROFILE_IMAGE = imageUrl("old-profile.jpg");
    private static final String NEW_PROFILE_IMAGE = imageUrl("new-profile.jpg");
    private static final String PORTFOLIO_IMAGE_1 = imageUrl("portfolio1.jpg");
    private static final String PORTFOLIO_IMAGE_2 = imageUrl("portfolio2.jpg");
    private static final String OLD_PORTFOLIO_IMAGE = imageUrl("old-portfolio.jpg");
    private static final String NEW_PORTFOLIO_IMAGE = imageUrl("new-portfolio.jpg");
    private static final String SECOND_PORTFOLIO_IMAGE = imageUrl("second-portfolio.jpg");

    @Autowired
    private EngineerService engineerService;

    @Autowired
    private EngineerRepository engineerRepository;

    @Autowired
    private EngineerCategoryRepository engineerCategoryRepository;

    @Autowired
    private InterestedEngineerRepository interestedEngineerRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private S3Client s3Client;

    private User currentUser;
    private Region region;

    @BeforeEach
    void setUp() {
        region = regionRepository.findAllByOrderByRegionIdAsc().getFirst();
        currentUser = saveUser(900000000001L, "테스트유저");

        given(userService.getCurrentUser()).willReturn(currentUser);
        given(s3Client.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
                .willReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(),
                        new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
                ));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("기술자 등록")
    class RegisterEngineer {

        @Test
        @DisplayName("올바른 형식의 정보로 기술자를 등록할 수 있다")
        void registerEngineer_success() {
            // given
            EngineerRequestDto request = createRequest(
                    List.of("주방", "타일"),
                    List.of(PORTFOLIO_IMAGE_1, PORTFOLIO_IMAGE_2),
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
            assertThat(savedEngineer.getProfileImage()).isEqualTo(PROFILE_IMAGE);
            assertThat(savedEngineer.getIntroduction()).isEqualTo("테스트 소개");
            assertThat(savedEngineer.getStoreName()).isEqualTo("테스트 상호");
            assertThat(savedEngineer.getStoreAddress()).isEqualTo("서울시 테스트구");
            assertThat(savedEngineer.getPhone()).isEqualTo("01012345678");
            assertThat(savedEngineer.getEngineerName()).isEqualTo("김기술");
            assertThat(savedEngineer.getHeadline()).isEqualTo("주방 시공 전문");
            assertThat(savedEngineer.getRegistrationType()).isEqualTo(EngineerRegistrationType.SELF_REGISTERED);
            assertThat(savedEngineer.getClaimStatus()).isEqualTo(EngineerClaimStatus.APPROVED);

            assertThat(savedCategories)
                    .extracting(engineerCategory -> engineerCategory.getCategory().getCategoryName())
                    .containsExactlyInAnyOrder("주방", "타일");
            assertThat(savedPortfolios)
                    .extracting(Portfolio::getPortfolioImage)
                    .containsExactly(PORTFOLIO_IMAGE_1, PORTFOLIO_IMAGE_2);
        }

        @Test
        @DisplayName("짧은 소개 없이도 기술자를 등록할 수 있다")
        void registerEngineer_withoutHeadline() {
            // given
            EngineerRequestDto request = createRequestBuilder()
                    .headline(null)
                    .build();

            // when
            EngineerCreateResponse response = engineerService.registerEngineer(request);

            // then
            Engineer savedEngineer = engineerRepository.findById(response.getEngineerId())
                    .orElseThrow();
            assertThat(savedEngineer.getHeadline()).isNull();
        }

        @Test
        @DisplayName("이미 기술자로 등록한 사용자는 다시 등록할 수 없다")
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
        @DisplayName("존재하지 않는 지역으로는 기술자를 등록할 수 없다")
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

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("시공 분야가 없으면 기술자를 등록할 수 없다")
        void registerEngineer_categoriesAreMissing(List<String> categories) {
            // given
            EngineerRequestDto request = createRequestBuilder()
                    .categories(categories)
                    .build();

            // when & then
            assertThatThrownBy(() -> engineerService.registerEngineer(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_ENGINEER_CATEGORY);

            assertThat(engineerRepository.count()).isZero();
        }

        @Test
        @DisplayName("내용이 없는 시공 분야로는 기술자를 등록할 수 없다")
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
        @DisplayName("존재하지 않는 시공 분야가 포함되면 기술자를 등록할 수 없다")
        void registerEngineer_invalidCategory() {
            // given
            EngineerRequestDto request = createRequest(
                    List.of("주방", "없는분야"),
                    List.of(PORTFOLIO_IMAGE_1),
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
        @DisplayName("같은 시공 분야를 여러 번 선택해도 한 번만 등록할 수 있다")
        void registerEngineer_duplicateCategories() {
            // given
            EngineerRequestDto request = createRequest(
                    List.of(" 주방 ", "주방", "타일"),
                    List.of(PORTFOLIO_IMAGE_1),
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
        @DisplayName("올바르지 않은 전화번호로는 기술자를 등록할 수 없다")
        void registerEngineer_invalidPhone() {
            // given
            EngineerRequestDto request = createRequest(
                    List.of("주방", "타일"),
                    List.of(PORTFOLIO_IMAGE_1),
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

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("포트폴리오 이미지가 없으면 기술자를 등록할 수 없다")
        void registerEngineer_portfolioImagesAreMissing(List<String> portfolioImages) {
            // given
            EngineerRequestDto request = createRequestBuilder()
                    .portfolioImages(portfolioImages)
                    .build();

            // when & then
            assertThatThrownBy(() -> engineerService.registerEngineer(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_PORTFOLIO_IMAGE);

            assertThat(engineerRepository.count()).isZero();
            assertThat(engineerCategoryRepository.count()).isZero();
            assertThat(portfolioRepository.count()).isZero();
        }

        @Test
        @DisplayName("내용이 없는 포트폴리오 이미지로는 기술자를 등록할 수 없다")
        void registerEngineer_portfolioImagesAreBlank() {
            // given
            EngineerRequestDto request = createRequestBuilder()
                    .portfolioImages(List.of(" ", "  "))
                    .build();

            // when & then
            assertThatThrownBy(() -> engineerService.registerEngineer(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_PORTFOLIO_IMAGE);

            assertThat(engineerRepository.count()).isZero();
            assertThat(engineerCategoryRepository.count()).isZero();
            assertThat(portfolioRepository.count()).isZero();
        }

        @Test
        @DisplayName("외부 URL은 포트폴리오 이미지로 등록할 수 없다")
        void registerEngineer_rejectExternalPortfolioImageUrl() {
            // given
            EngineerRequestDto request = createRequestBuilder()
                    .portfolioImages(List.of("https://evil.example.com/portfolio.png"))
                    .build();

            // when & then
            assertThatThrownBy(() -> engineerService.registerEngineer(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_PORTFOLIO_IMAGE);

            assertThat(engineerRepository.count()).isZero();
            assertThat(engineerCategoryRepository.count()).isZero();
            assertThat(portfolioRepository.count()).isZero();
        }

        @Test
        @DisplayName("외부 URL은 프로필 이미지로 등록할 수 없다")
        void registerEngineer_rejectExternalProfileImageUrl() {
            // given
            EngineerRequestDto request = createRequestBuilder()
                    .profileImage("https://evil.example.com/profile.png")
                    .build();

            // when & then
            assertThatThrownBy(() -> engineerService.registerEngineer(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_PROFILE_IMAGE);

            assertThat(engineerRepository.count()).isZero();
            assertThat(engineerCategoryRepository.count()).isZero();
            assertThat(portfolioRepository.count()).isZero();
        }

        @Test
        @DisplayName("하이픈이 포함된 전화번호에서 숫자만 빼서 저장한다")
        void registerEngineer_normalizesPhone() {
            // given
            EngineerRequestDto request = createRequest(
                    List.of("주방", "타일"),
                    List.of(PORTFOLIO_IMAGE_1),
                    "010-1234-5678"
            );

            // when
            EngineerCreateResponse response = engineerService.registerEngineer(request);

            // then
            Engineer savedEngineer = engineerRepository.findById(response.getEngineerId())
                    .orElseThrow();
            assertThat(savedEngineer.getPhone()).isEqualTo("01012345678");
        }

        @Test
        @DisplayName("비어 있거나 중복된 포트폴리오 이미지는 제외하고 등록한다")
        void registerEngineer_normalizesPortfolioImages() {
            // given
            EngineerRequestDto request = createRequest(
                    List.of("주방", "타일"),
                    List.of(" " + PORTFOLIO_IMAGE_1 + " ", " ", PORTFOLIO_IMAGE_1, PORTFOLIO_IMAGE_2),
                    "010-1234-5678"
            );

            // when
            EngineerCreateResponse response = engineerService.registerEngineer(request);

            // then
            List<Portfolio> savedPortfolios =
                    portfolioRepository.findByEngineer_EngineerId(response.getEngineerId());
            assertThat(savedPortfolios)
                    .extracting(Portfolio::getPortfolioImage)
                    .containsExactly(PORTFOLIO_IMAGE_1, PORTFOLIO_IMAGE_2);
        }

        @Test
        @DisplayName("이미 등록된 전화번호로는 기술자를 등록할 수 없다")
        void registerEngineer_phoneAlreadyRegistered() {
            // given
            User anotherUser = saveUser(900000000002L, "다른사용자");
            Engineer existingEngineer = Engineer.create(
                    anotherUser,
                    region,
                    null,
                    null,
                    null,
                    null,
                    "01012345678",
                    "기존기술자",
                    "기존 소개"
            );
            engineerRepository.save(existingEngineer);

            EngineerRequestDto request = createRequestBuilder()
                    .phone("010-1234-5678")
                    .build();

            // when & then
            assertThatThrownBy(() -> engineerService.registerEngineer(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ENGINEER_PHONE_ALREADY_REGISTERED);

            assertThat(engineerRepository.count()).isEqualTo(1);
        }

    }

    @Nested
    @DisplayName("기술자 수정")
    class UpdateEngineer {

        @Test
        @DisplayName("본인의 기술자 정보를 수정할 수 있다")
        void updateEngineer_success() {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "기존기술자",
                    "기존 소개"
            );
            Category kitchen = categoryRepository.findByCategoryName("주방").orElseThrow();
            engineerCategoryRepository.save(EngineerCategory.create(engineer, kitchen));
            portfolioRepository.save(Portfolio.create(engineer, OLD_PORTFOLIO_IMAGE));

            EngineerRequestDto request = createRequestBuilder()
                    .categories(List.of("타일"))
                    .portfolioImages(List.of( NEW_PORTFOLIO_IMAGE ))
                    .profileImage( NEW_PROFILE_IMAGE )
                    .introduction(" 새로운 소개 ")
                    .storeName(" 새로운 상호 ")
                    .storeAddress(" 새로운 주소 ")
                    .phone("010-2222-3333")
                    .engineerName(" 새로운기술자 ")
                    .headline(" 새로운 짧은 소개 ")
                    .build();

            // when
            engineerService.updateEngineer(engineer.getEngineerId(), request);

            // then
            Engineer updatedEngineer = engineerRepository.findById(engineer.getEngineerId())
                    .orElseThrow();
            List<EngineerCategory> updatedCategories =
                    engineerCategoryRepository.findByEngineer_EngineerIdIn(List.of(engineer.getEngineerId()));
            List<Portfolio> updatedPortfolios =
                    portfolioRepository.findByEngineer_EngineerId(engineer.getEngineerId());

            assertThat(updatedEngineer.getEngineerName()).isEqualTo("새로운기술자");
            assertThat(updatedEngineer.getHeadline()).isEqualTo("새로운 짧은 소개");
            assertThat(updatedEngineer.getPhone()).isEqualTo("01022223333");
            assertThat(updatedEngineer.getProfileImage()).isEqualTo(NEW_PROFILE_IMAGE);
            assertThat(updatedEngineer.getIntroduction()).isEqualTo("새로운 소개");
            assertThat(updatedEngineer.getStoreName()).isEqualTo("새로운 상호");
            assertThat(updatedEngineer.getStoreAddress()).isEqualTo("새로운 주소");
            assertThat(updatedCategories)
                    .extracting(mapping -> mapping.getCategory().getCategoryName())
                    .containsExactly("타일");
            assertThat(updatedPortfolios)
                    .extracting(Portfolio::getPortfolioImage)
                    .containsExactly(NEW_PORTFOLIO_IMAGE);
        }

        @Test
        @DisplayName("짧은 소개를 비워서 수정할 수 있다")
        void updateEngineer_withoutHeadline() {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "기존기술자",
                    "기존 소개"
            );
            portfolioRepository.save(Portfolio.create(engineer, OLD_PORTFOLIO_IMAGE));

            EngineerRequestDto request = createRequestBuilder()
                    .headline(" ")
                    .phone("010-1111-2222")
                    .build();

            // when
            engineerService.updateEngineer(engineer.getEngineerId(), request);

            // then
            Engineer updatedEngineer = engineerRepository.findById(engineer.getEngineerId())
                    .orElseThrow();
            assertThat(updatedEngineer.getHeadline()).isNull();
        }

        @Test
        @DisplayName("다른 사용자의 기술자 정보를 수정할 수 없다")
        void updateEngineer_notOwner() {
            // given
            User anotherUser = saveUser(900000000002L, "다른사용자");
            Engineer engineer = saveEngineer(
                    anotherUser,
                    "01011112222",
                    "다른기술자",
                    "기존 소개"
            );

            // when & then
            assertThatThrownBy(() -> engineerService.updateEngineer(
                    engineer.getEngineerId(),
                    createValidRequest()
            ))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CANNOT_MODIFY_ENGINEER);
        }

        @Test
        @DisplayName("존재하지 않는 기술자 정보는 수정할 수 없다")
        void updateEngineer_engineerNotFound() {
            // when & then
            assertThatThrownBy(() -> engineerService.updateEngineer(999L, createValidRequest()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ENGINEER_NOT_FOUND);
        }

        @Test
        @DisplayName("존재하지 않는 지역으로 기술자 정보를 수정할 수 없다")
        void updateEngineer_regionNotFound() {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "기존기술자",
                    "기존 소개"
            );
            EngineerRequestDto request = createRequestBuilder()
                    .regionId(999)
                    .build();

            // when & then
            assertThatThrownBy(() -> engineerService.updateEngineer(engineer.getEngineerId(), request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.REGION_NOT_FOUND);
        }

        @Test
        @DisplayName("존재하지 않는 시공 분야가 포함되면 기술자 정보를 수정할 수 없다")
        void updateEngineer_invalidCategory() {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "기존기술자",
                    "기존 소개"
            );
            EngineerRequestDto request = createRequestBuilder()
                    .categories(List.of("주방", "없는분야"))
                    .phone("010-1111-2222")
                    .build();

            // when & then
            assertThatThrownBy(() -> engineerService.updateEngineer(engineer.getEngineerId(), request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_ENGINEER_CATEGORY);
        }

        @Test
        @DisplayName("중복되거나 내용이 없는 시공 분야와 포트폴리오는 정리하여 수정할 수 있다")
        void updateEngineer_normalizesCategoriesAndPortfolioImages() {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "기존기술자",
                    "기존 소개"
            );
            Category kitchen = categoryRepository.findByCategoryName("주방").orElseThrow();
            engineerCategoryRepository.save(EngineerCategory.create(engineer, kitchen));
            portfolioRepository.save(Portfolio.create(engineer, OLD_PORTFOLIO_IMAGE));

            EngineerRequestDto request = createRequestBuilder()
                    .categories(List.of(" 주방 ", " ", "주방", "타일"))
                    .portfolioImages(List.of(
                            " " + NEW_PORTFOLIO_IMAGE + " ",
                            " ",
                            NEW_PORTFOLIO_IMAGE,
                            SECOND_PORTFOLIO_IMAGE
                    ))
                    .phone("010-1111-2222")
                    .build();

            // when
            engineerService.updateEngineer(engineer.getEngineerId(), request);

            // then
            List<EngineerCategory> updatedCategories =
                    engineerCategoryRepository.findByEngineer_EngineerIdIn(List.of(engineer.getEngineerId()));
            List<Portfolio> updatedPortfolios =
                    portfolioRepository.findByEngineer_EngineerId(engineer.getEngineerId());

            assertThat(updatedCategories)
                    .extracting(mapping -> mapping.getCategory().getCategoryName())
                    .containsExactlyInAnyOrder("주방", "타일");
            assertThat(updatedPortfolios)
                    .extracting(Portfolio::getPortfolioImage)
                    .containsExactly(NEW_PORTFOLIO_IMAGE, SECOND_PORTFOLIO_IMAGE);
        }

        @Test
        @DisplayName("수정 시 유지되는 포트폴리오는 삭제하지 않고 새 포트폴리오만 추가한다")
        void updateEngineer_keepsUnchangedPortfolioAndAddsOnlyNewPortfolio() {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "기존기술자",
                    "기존 소개"
            );
            Portfolio unchangedPortfolio =
                    portfolioRepository.save(Portfolio.create(engineer, OLD_PORTFOLIO_IMAGE));
            entityManager.flush();
            portfolioRepository.updatePortfolioThumbnailImageIfPortfolioUnchanged(
                    unchangedPortfolio.getPortfolioId(),
                    OLD_PORTFOLIO_IMAGE,
                    imageUrl("thumbnail/old-portfolio.jpg")
            );
            entityManager.flush();
            entityManager.clear();

            EngineerRequestDto request = createRequestBuilder()
                    .portfolioImages(List.of(OLD_PORTFOLIO_IMAGE, NEW_PORTFOLIO_IMAGE))
                    .phone("010-1111-2222")
                    .build();

            // when
            engineerService.updateEngineer(engineer.getEngineerId(), request);
            entityManager.flush();
            entityManager.clear();

            // then
            List<Portfolio> updatedPortfolios =
                    portfolioRepository.findByEngineer_EngineerId(engineer.getEngineerId());
            assertThat(updatedPortfolios)
                    .extracting(Portfolio::getPortfolioImage)
                    .containsExactlyInAnyOrder(OLD_PORTFOLIO_IMAGE, NEW_PORTFOLIO_IMAGE);
            assertThat(updatedPortfolios)
                    .filteredOn(portfolio -> portfolio.getPortfolioImage().equals(OLD_PORTFOLIO_IMAGE))
                    .singleElement()
                    .satisfies(portfolio -> {
                        assertThat(portfolio.getPortfolioId()).isEqualTo(unchangedPortfolio.getPortfolioId());
                        assertThat(portfolio.getPortfolioThumbnailImage())
                                .isEqualTo(imageUrl("thumbnail/old-portfolio.jpg"));
                    });
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("시공 분야가 없으면 기술자 정보를 수정할 수 없다")
        void updateEngineer_categoriesAreMissing(List<String> categories) {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "기존기술자",
                    "기존 소개"
            );
            Category kitchen = categoryRepository.findByCategoryName("주방").orElseThrow();
            engineerCategoryRepository.save(EngineerCategory.create(engineer, kitchen));
            EngineerRequestDto request = createRequestBuilder()
                    .categories(categories)
                    .phone("010-1111-2222")
                    .build();

            // when & then
            assertThatThrownBy(() -> engineerService.updateEngineer(engineer.getEngineerId(), request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_ENGINEER_CATEGORY);

            assertThat(engineerCategoryRepository
                    .findByEngineer_EngineerIdIn(List.of(engineer.getEngineerId())))
                    .hasSize(1);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("포트폴리오 이미지가 없으면 기술자 정보를 수정할 수 없다")
        void updateEngineer_portfolioImagesAreMissing(List<String> portfolioImages) {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "기존기술자",
                    "기존 소개"
            );
            portfolioRepository.save(Portfolio.create(engineer, OLD_PORTFOLIO_IMAGE));
            EngineerRequestDto request = createRequestBuilder()
                    .portfolioImages(portfolioImages)
                    .phone("010-1111-2222")
                    .build();

            // when & then
            assertThatThrownBy(() -> engineerService.updateEngineer(engineer.getEngineerId(), request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_PORTFOLIO_IMAGE);

            assertThat(portfolioRepository.findByEngineer_EngineerId(engineer.getEngineerId()))
                    .extracting(Portfolio::getPortfolioImage)
                    .containsExactly(OLD_PORTFOLIO_IMAGE);
        }

        @Test
        @DisplayName("외부 URL은 포트폴리오 이미지로 수정할 수 없다")
        void updateEngineer_rejectExternalPortfolioImageUrl() {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "기존기술자",
                    "기존 소개"
            );
            portfolioRepository.save(Portfolio.create(engineer, OLD_PORTFOLIO_IMAGE));
            EngineerRequestDto request = createRequestBuilder()
                    .portfolioImages(List.of("https://evil.example.com/portfolio.png"))
                    .phone("010-1111-2222")
                    .build();

            // when & then
            assertThatThrownBy(() -> engineerService.updateEngineer(engineer.getEngineerId(), request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_PORTFOLIO_IMAGE);

            assertThat(portfolioRepository.findByEngineer_EngineerId(engineer.getEngineerId()))
                    .extracting(Portfolio::getPortfolioImage)
                    .containsExactly(OLD_PORTFOLIO_IMAGE);
        }

        @Test
        @DisplayName("기존 전화번호를 유지하면서 기술자 정보를 수정할 수 있다")
        void updateEngineer_samePhone() {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "기존기술자",
                    "기존 소개"
            );
            EngineerRequestDto request = createRequestBuilder()
                    .phone("010-1111-2222")
                    .engineerName("변경된기술자")
                    .build();

            // when
            engineerService.updateEngineer(engineer.getEngineerId(), request);

            // then
            Engineer updatedEngineer = engineerRepository.findById(engineer.getEngineerId())
                    .orElseThrow();
            assertThat(updatedEngineer.getPhone()).isEqualTo("01011112222");
            assertThat(updatedEngineer.getEngineerName()).isEqualTo("변경된기술자");
        }

        @Test
        @DisplayName("다른 기술자가 사용하는 전화번호로 수정할 수 없다")
        void updateEngineer_phoneAlreadyRegistered() {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "기존기술자",
                    "기존 소개"
            );
            User anotherUser = saveUser(900000000002L, "다른사용자");
            saveEngineer(
                    anotherUser,
                    "01033334444",
                    "다른기술자",
                    "다른 소개"
            );
            EngineerRequestDto request = createRequestBuilder()
                    .phone("010-3333-4444")
                    .build();

            // when & then
            assertThatThrownBy(() -> engineerService.updateEngineer(engineer.getEngineerId(), request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ENGINEER_PHONE_ALREADY_REGISTERED);

            assertThat(engineer.getPhone()).isEqualTo("01011112222");
        }
    }

    @Nested
    @DisplayName("기술자 목록 조회")
    class GetEngineerList {

        @Test
        @DisplayName("기술자 목록을 페이지 단위로 조회할 수 있다")
        void getEngineerList_success() {
            // given
            Engineer firstEngineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "첫번째기술자",
                    "첫 번째 소개"
            );
            User anotherUser = saveUser(900000000002L, "다른사용자");
            Engineer secondEngineer = saveEngineer(
                    anotherUser,
                    "01033334444",
                    "두번째기술자",
                    "두 번째 소개"
            );
            Category kitchen = categoryRepository.findByCategoryName("주방").orElseThrow();
            Category tile = categoryRepository.findByCategoryName("타일").orElseThrow();
            engineerCategoryRepository.saveAll(List.of(
                    EngineerCategory.create(firstEngineer, kitchen),
                    EngineerCategory.create(firstEngineer, tile),
                    EngineerCategory.create(secondEngineer, tile)
            ));
            PageRequest pageable = PageRequest.of(0, 10);

            // when
            Slice<EngineerSummaryResponseDto> result =
                    engineerService.getEngineerList(pageable);

            // then
            assertThat(result.getNumber()).isZero();
            assertThat(result.getSize()).isEqualTo(10);
            assertThat(result.getContent()).hasSize(2);

            assertThat(result.getContent())
                    .filteredOn(summary -> summary.getEngineerId().equals(firstEngineer.getEngineerId()))
                    .singleElement()
                    .satisfies(summary -> {
                        assertThat(summary.getEngineerName()).isEqualTo("첫번째기술자");
                        assertThat(summary.getProfileImage()).isEqualTo(OLD_PROFILE_IMAGE);
                        assertThat(summary.getIntroduction()).isEqualTo("기존 소개");
                        assertThat(summary.getCategories())
                                .containsExactlyInAnyOrder("주방", "타일");
                    });

            assertThat(result.getContent())
                    .filteredOn(summary -> summary.getEngineerId().equals(secondEngineer.getEngineerId()))
                    .singleElement()
                    .satisfies(summary -> {
                        assertThat(summary.getEngineerName()).isEqualTo("두번째기술자");
                        assertThat(summary.getProfileImage()).isEqualTo(OLD_PROFILE_IMAGE);
                        assertThat(summary.getIntroduction()).isEqualTo("기존 소개");
                        assertThat(summary.getCategories()).containsExactly("타일");
                    });
        }

        @Test
        @DisplayName("자기소개가 100자를 초과하면 줄여서 조회한다")
        void getEngineerList_truncatesIntroduction() {
            // given
            String longIntroduction = "가".repeat(101);
            Engineer engineer = Engineer.create(
                    currentUser,
                    region,
                    PROFILE_IMAGE,
                    longIntroduction,
                    "테스트 상호",
                    "테스트 주소",
                    "01011112222",
                    "목록기술자",
                    "목록 소개"
            );
            engineerRepository.save(engineer);

            // when
            Slice<EngineerSummaryResponseDto> result =
                    engineerService.getEngineerList(PageRequest.of(0, 10));

            // then
            assertThat(result.getContent()).singleElement()
                    .extracting(EngineerSummaryResponseDto::getIntroduction)
                    .isEqualTo("가".repeat(100) + "...");
        }

        @Test
        @DisplayName("등록된 기술자가 없으면 빈 목록을 조회한다")
        void getEngineerList_empty() {
            // when
            Slice<EngineerSummaryResponseDto> result =
                    engineerService.getEngineerList(PageRequest.of(0, 10));

            // then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.hasNext()).isFalse();
        }
    }

    @Nested
    @DisplayName("기술자 상세 조회")
    class GetEngineerDetail {

        @Test
        @DisplayName("비로그인 사용자도 기술자 상세 정보를 조회할 수 있다")
        void getEngineerDetail_anonymous() {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "상세기술자",
                    "상세 소개"
            );
            Category kitchen = categoryRepository.findByCategoryName("주방").orElseThrow();
            Category tile = categoryRepository.findByCategoryName("타일").orElseThrow();
            engineerCategoryRepository.saveAll(List.of(
                    EngineerCategory.create(engineer, kitchen),
                    EngineerCategory.create(engineer, tile)
            ));
            portfolioRepository.saveAll(List.of(
                    Portfolio.create(engineer, PORTFOLIO_IMAGE_1),
                    Portfolio.create(engineer, PORTFOLIO_IMAGE_2)
            ));
            entityManager.flush();
            entityManager.clear();
            SecurityContextHolder.clearContext();

            // when
            EngineerDetailResponseDto result =
                    engineerService.getEngineerDetail(engineer.getEngineerId());

            // then
            assertThat(result.getEngineerId()).isEqualTo(engineer.getEngineerId());
            assertThat(result.getUserId()).isEqualTo(currentUser.getUserId());
            assertThat(result.getEngineerName()).isEqualTo("상세기술자");
            assertThat(result.getHeadline()).isEqualTo("상세 소개");
            assertThat(result.getProfileImage()).isEqualTo(OLD_PROFILE_IMAGE);
            assertThat(result.getRegionName()).isEqualTo("서울특별시 강남구");
            assertThat(result.getCategories()).containsExactlyInAnyOrder("주방", "타일");
            assertThat(result.getIntroduction()).isEqualTo("기존 소개");
            assertThat(result.getStoreName()).isEqualTo("기존 상호");
            assertThat(result.getStoreAddress()).isEqualTo("기존 주소");
            assertThat(result.getPhone()).isEqualTo("01011112222");
            assertThat(result.isInterested()).isFalse();
            assertThat(result.getPortfolioImages())
                    .containsExactly(PORTFOLIO_IMAGE_1, PORTFOLIO_IMAGE_2);
        }

        @Test
        @DisplayName("수동 등록 기술자 상세 정보는 연결된 사용자 없이도 조회할 수 있다")
        void getEngineerDetail_adminCreatedWithoutUser() {
            // given
            Engineer engineer = engineerRepository.save(Engineer.createAdminCreated(
                    region,
                    OLD_PROFILE_IMAGE,
                    "기존 소개",
                    "기존 상호",
                    "기존 주소",
                    "01011112222",
                    "수동기술자",
                    null
            ));
            Category kitchen = categoryRepository.findByCategoryName("주방").orElseThrow();
            engineerCategoryRepository.save(EngineerCategory.create(engineer, kitchen));
            entityManager.flush();
            entityManager.clear();
            SecurityContextHolder.clearContext();

            // when
            EngineerDetailResponseDto result =
                    engineerService.getEngineerDetail(engineer.getEngineerId());

            // then
            assertThat(result.getEngineerId()).isEqualTo(engineer.getEngineerId());
            assertThat(result.getUserId()).isNull();
            assertThat(result.getEngineerName()).isEqualTo("수동기술자");
            assertThat(result.getCategories()).containsExactly("주방");
            assertThat(result.isInterested()).isFalse();
        }

        @Test
        @DisplayName("관심 기술자로 등록한 사용자를 조회하면 관심 기술자 여부가 참이다")
        void getEngineerDetail_interested() {
            // given
            User engineerUser = saveUser(900000000002L, "기술자사용자");
            Engineer engineer = saveEngineer(
                    engineerUser,
                    "01011112222",
                    "관심기술자",
                    "관심 소개"
            );
            interestedEngineerRepository.save(
                    InterestedEngineer.create(currentUser, engineer)
            );
            setAuthenticatedUser(currentUser);

            // when
            EngineerDetailResponseDto result =
                    engineerService.getEngineerDetail(engineer.getEngineerId());

            // then
            assertThat(result.isInterested()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 기술자 상세 정보는 조회할 수 없다")
        void getEngineerDetail_engineerNotFound() {
            // when & then
            assertThatThrownBy(() -> engineerService.getEngineerDetail(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ENGINEER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("기술자 삭제")
    class DeleteEngineer {

        @Test
        @DisplayName("본인의 기술자 정보와 연결된 데이터를 삭제하고 다시 기술자로 등록할 수 있다")
        void deleteEngineer_success() {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "삭제기술자",
                    "삭제 소개"
            );
            Category kitchen = categoryRepository.findByCategoryName("주방").orElseThrow();
            engineerCategoryRepository.save(
                    EngineerCategory.create(engineer, kitchen)
            );
            portfolioRepository.save(
                    Portfolio.create(engineer, imageUrl("portfolio.jpg"))
            );
            User interestedUser = saveUser(900000000002L, "관심사용자");
            interestedEngineerRepository.save(
                    InterestedEngineer.create(interestedUser, engineer)
            );

            // when
            engineerService.deleteEngineer(engineer.getEngineerId());
            entityManager.flush();

            // then
            assertThat(engineerRepository.findById(engineer.getEngineerId())).isEmpty();
            assertThat(engineerCategoryRepository.count()).isZero();
            assertThat(portfolioRepository.count()).isZero();
            assertThat(interestedEngineerRepository.count()).isZero();

            EngineerCreateResponse response =
                    engineerService.registerEngineer(createValidRequest());
            assertThat(engineerRepository.findById(response.getEngineerId())).isPresent();
        }

        @Test
        @DisplayName("다른 사용자의 기술자 정보는 삭제할 수 없다")
        void deleteEngineer_notOwner() {
            // given
            User anotherUser = saveUser(900000000002L, "다른사용자");
            Engineer engineer = saveEngineer(
                    anotherUser,
                    "01011112222",
                    "다른기술자",
                    "다른 소개"
            );

            // when & then
            assertThatThrownBy(() -> engineerService.deleteEngineer(engineer.getEngineerId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CANNOT_UNJOIN_ENGINEER);

            assertThat(engineerRepository.findById(engineer.getEngineerId())).isPresent();
        }

        @Test
        @DisplayName("존재하지 않는 기술자 정보는 삭제할 수 없다")
        void deleteEngineer_engineerNotFound() {
            // when & then
            assertThatThrownBy(() -> engineerService.deleteEngineer(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ENGINEER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("관심 기술자 추가")
    class AddInterestedEngineer {

        @Test
        @DisplayName("다른 기술자를 관심 기술자로 등록할 수 있다")
        void addInterestedEngineer_success() {
            // given
            User engineerUser = saveUser(900000000002L, "기술자사용자");
            Engineer engineer = saveEngineer(
                    engineerUser,
                    "01011112222",
                    "관심기술자",
                    "관심 소개"
            );

            // when
            engineerService.addInterestedEngineer(engineer.getEngineerId());

            // then
            assertThat(interestedEngineerRepository
                    .existsByUser_UserIdAndEngineer_EngineerId(
                            currentUser.getUserId(),
                            engineer.getEngineerId()
                    ))
                    .isTrue();
        }

        @Test
        @DisplayName("본인을 관심 기술자로 등록할 수 없다")
        void addInterestedEngineer_ownEngineer() {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "본인기술자",
                    "본인 소개"
            );

            // when & then
            assertThatThrownBy(() -> engineerService.addInterestedEngineer(engineer.getEngineerId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CANNOT_INTEREST_OWN_ENGINEER);

            assertThat(interestedEngineerRepository.count()).isZero();
        }

        @Test
        @DisplayName("존재하지 않는 기술자를 관심 기술자로 등록할 수 없다")
        void addInterestedEngineer_engineerNotFound() {
            // when & then
            assertThatThrownBy(() -> engineerService.addInterestedEngineer(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ENGINEER_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 등록한 관심 기술자를 다시 등록해도 중복 저장되지 않는다")
        void addInterestedEngineer_alreadyExists() {
            // given
            User engineerUser = saveUser(900000000002L, "기술자사용자");
            Engineer engineer = saveEngineer(
                    engineerUser,
                    "01011112222",
                    "관심기술자",
                    "관심 소개"
            );
            interestedEngineerRepository.save(
                    InterestedEngineer.create(currentUser, engineer)
            );

            // when
            engineerService.addInterestedEngineer(engineer.getEngineerId());

            // then
            assertThat(interestedEngineerRepository.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("관심 기술자 삭제")
    class RemoveInterestedEngineer {

        @Test
        @DisplayName("관심 기술자 목록에서 기술자를 삭제할 수 있다")
        void removeInterestedEngineer_success() {
            // given
            User engineerUser = saveUser(900000000002L, "기술자사용자");
            Engineer engineer = saveEngineer(
                    engineerUser,
                    "01011112222",
                    "관심기술자",
                    "관심 소개"
            );
            interestedEngineerRepository.save(
                    InterestedEngineer.create(currentUser, engineer)
            );

            // when
            engineerService.removeInterestedEngineer(engineer.getEngineerId());

            // then
            assertThat(interestedEngineerRepository
                    .existsByUser_UserIdAndEngineer_EngineerId(
                            currentUser.getUserId(),
                            engineer.getEngineerId()
                    ))
                    .isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 기술자는 관심 기술자 목록에서 삭제할 수 없다")
        void removeInterestedEngineer_engineerNotFound() {
            // when & then
            assertThatThrownBy(() -> engineerService.removeInterestedEngineer(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ENGINEER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("관심 기술자 목록 조회")
    class GetInterestedEngineers {

        @Test
        @DisplayName("현재 사용자가 등록한 관심 기술자 목록을 페이지 단위로 조회할 수 있다")
        void getInterestedEngineers_success() {
            // given
            User firstEngineerUser = saveUser(900000000002L, "첫번째기술자사용자");
            Engineer firstEngineer = saveEngineer(
                    firstEngineerUser,
                    "01011112222",
                    "첫번째관심기술자",
                    "첫 번째 소개"
            );
            User secondEngineerUser = saveUser(900000000003L, "두번째기술자사용자");
            Engineer secondEngineer = saveEngineer(
                    secondEngineerUser,
                    "01033334444",
                    "두번째관심기술자",
                    "두 번째 소개"
            );
            User anotherUser = saveUser(900000000004L, "다른사용자");

            interestedEngineerRepository.saveAll(List.of(
                    InterestedEngineer.create(currentUser, firstEngineer),
                    InterestedEngineer.create(currentUser, secondEngineer),
                    InterestedEngineer.create(anotherUser, firstEngineer)
            ));

            // when
            Page<InterestedEngineerItemResponseDto> result =
                    engineerService.getInterestedEngineers(PageRequest.of(0, 15));

            // then
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getNumber()).isZero();
            assertThat(result.getSize()).isEqualTo(15);
            assertThat(result.getContent())
                    .extracting(InterestedEngineerItemResponseDto::getEngineerId)
                    .containsExactlyInAnyOrder(
                            firstEngineer.getEngineerId(),
                            secondEngineer.getEngineerId()
                    );
            assertThat(result.getContent())
                    .extracting(InterestedEngineerItemResponseDto::getEngineerName)
                    .containsExactlyInAnyOrder(
                            "첫번째관심기술자",
                            "두번째관심기술자"
                    );
            assertThat(result.getContent())
                    .extracting(InterestedEngineerItemResponseDto::getProfileImage)
                    .containsOnly(OLD_PROFILE_IMAGE);
        }
    }

    @Nested
    @DisplayName("기술자 전화번호 중복 확인")
    class CheckPhoneDuplicate {

        @Test
        @DisplayName("이미 등록된 전화번호는 중복으로 확인된다")
        void checkPhoneDuplicate_duplicated() {
            // given
            saveEngineer(
                    currentUser,
                    "01012345678",
                    "기존기술자",
                    "기존 소개"
            );

            // when
            PhoneDuplicateCheckResponseDto result =
                    engineerService.checkPhoneDuplicate("010-1234-5678", null);

            // then
            assertThat(result.isDuplicated()).isTrue();
        }

        @Test
        @DisplayName("등록되지 않은 전화번호는 중복이 아닌 것으로 확인된다")
        void checkPhoneDuplicate_notDuplicated() {
            // when
            PhoneDuplicateCheckResponseDto result =
                    engineerService.checkPhoneDuplicate("010-1234-5678", null);

            // then
            assertThat(result.isDuplicated()).isFalse();
        }

        @Test
        @DisplayName("올바르지 않은 전화번호로 중복 여부를 확인할 수 없다")
        void checkPhoneDuplicate_invalidPhone() {
            // when & then
            assertThatThrownBy(() -> engineerService.checkPhoneDuplicate("1234", null))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_PHONE_NUMBER);
        }

        @Test
        @DisplayName("수정할 때 본인의 기존 전화번호는 중복이 아닌 것으로 확인된다")
        void checkPhoneDuplicate_excludesOwnEngineer() {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01012345678",
                    "기존기술자",
                    "기존 소개"
            );

            // when
            PhoneDuplicateCheckResponseDto result =
                    engineerService.checkPhoneDuplicate(
                            "010-1234-5678",
                            engineer.getEngineerId()
                    );

            // then
            assertThat(result.isDuplicated()).isFalse();
        }

        @Test
        @DisplayName("수정할 때 다른 기술자의 전화번호는 중복으로 확인된다")
        void checkPhoneDuplicate_detectsAnotherEngineerPhone() {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "본인기술자",
                    "본인 소개"
            );
            User anotherUser = saveUser(900000000002L, "다른사용자");
            saveEngineer(
                    anotherUser,
                    "01033334444",
                    "다른기술자",
                    "다른 소개"
            );

            // when
            PhoneDuplicateCheckResponseDto result =
                    engineerService.checkPhoneDuplicate(
                            "010-3333-4444",
                            engineer.getEngineerId()
                    );

            // then
            assertThat(result.isDuplicated()).isTrue();
        }

        @Test
        @DisplayName("다른 사용자의 기술자를 제외하여 전화번호 중복 여부를 확인할 수 없다")
        void checkPhoneDuplicate_notOwner() {
            // given
            User anotherUser = saveUser(900000000002L, "다른사용자");
            Engineer engineer = saveEngineer(
                    anotherUser,
                    "01011112222",
                    "다른기술자",
                    "다른 소개"
            );

            // when & then
            assertThatThrownBy(() -> engineerService.checkPhoneDuplicate(
                    "010-1234-5678",
                    engineer.getEngineerId()
            ))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CANNOT_MODIFY_ENGINEER);
        }
    }


    private EngineerRequestDto createValidRequest() {
        return createRequest(
                List.of("주방", "타일"),
                List.of(PORTFOLIO_IMAGE_1, PORTFOLIO_IMAGE_2),
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
                .portfolioImages(List.of(PORTFOLIO_IMAGE_1, PORTFOLIO_IMAGE_2))
                .introduction(" 테스트 소개 ")
                .regionId(region.getRegionId())
                .profileImage(" " + PROFILE_IMAGE + " ")
                .storeName(" 테스트 상호 ")
                .storeAddress(" 서울시 테스트구 ")
                .phone("010-1234-5678")
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

    private Engineer saveEngineer(
            User user,
            String phone,
            String engineerName,
            String headline
    ) {
        Engineer engineer = Engineer.create(
                user,
                region,
                OLD_PROFILE_IMAGE,
                "기존 소개",
                "기존 상호",
                "기존 주소",
                phone,
                engineerName,
                headline
        );
        return engineerRepository.save(engineer);
    }

    private void setAuthenticatedUser(User user) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        String.valueOf(user.getUserId()),
                        null,
                        List.of()
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static String imageUrl(String fileName) {
        return "https://image.test/engineer/" + fileName;
    }
}

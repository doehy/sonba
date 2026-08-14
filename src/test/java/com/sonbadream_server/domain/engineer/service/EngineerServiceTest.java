package com.sonbadream_server.domain.engineer.service;

import com.sonbadream_server.domain.engineer.dto.request.EngineerRequestDto;
import com.sonbadream_server.domain.engineer.dto.request.PortfolioItemRequestDto;
import com.sonbadream_server.domain.engineer.dto.response.EngineerClaimRequestResponseDto;
import com.sonbadream_server.domain.engineer.dto.response.EngineerCreateResponse;
import com.sonbadream_server.domain.engineer.dto.response.EngineerDetailResponseDto;
import com.sonbadream_server.domain.engineer.dto.response.EngineerSummaryResponseDto;
import com.sonbadream_server.domain.engineer.dto.response.InterestedEngineerItemResponseDto;
import com.sonbadream_server.domain.engineer.dto.response.PhoneDuplicateCheckResponseDto;
import com.sonbadream_server.domain.engineer.entity.Engineer;
import com.sonbadream_server.domain.engineer.entity.EngineerClaimStatus;
import com.sonbadream_server.domain.engineer.entity.EngineerCategory;
import com.sonbadream_server.domain.engineer.entity.EngineerRegistrationType;
import com.sonbadream_server.domain.engineer.entity.InterestedEngineer;
import com.sonbadream_server.domain.engineer.repository.EngineerCategoryRepository;
import com.sonbadream_server.domain.engineer.repository.EngineerRepository;
import com.sonbadream_server.domain.engineer.repository.InterestedEngineerRepository;
import com.sonbadream_server.domain.category.entity.Category;
import com.sonbadream_server.domain.category.repository.CategoryRepository;
import com.sonbadream_server.domain.portfolio.entity.Portfolio;
import com.sonbadream_server.domain.portfolio.entity.PortfolioImage;
import com.sonbadream_server.domain.portfolio.entity.PortfolioType;
import com.sonbadream_server.domain.portfolio.repository.PortfolioImageRepository;
import com.sonbadream_server.domain.portfolio.repository.PortfolioRepository;
import com.sonbadream_server.domain.region.entity.Region;
import com.sonbadream_server.domain.region.repository.RegionRepository;
import com.sonbadream_server.domain.user.entity.User;
import com.sonbadream_server.domain.user.entity.UserRole;
import com.sonbadream_server.domain.user.entity.UserStatus;
import com.sonbadream_server.domain.user.repository.UserRepository;
import com.sonbadream_server.domain.user.service.UserService;
import com.sonbadream_server.global.exception.CustomException;
import com.sonbadream_server.global.exception.ErrorCode;
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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
    private PortfolioImageRepository portfolioImageRepository;

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
                    .extracting(EngineerServiceTest.this::firstPortfolioImageUrl)
                    .containsExactly(PORTFOLIO_IMAGE_1, PORTFOLIO_IMAGE_2);
        }

        @Test
        @DisplayName("포트폴리오 시공 사례 타입별로 기술자를 등록할 수 있다")
        void registerEngineer_withPortfolioItems() {
            // given
            EngineerRequestDto request = createRequestBuilder()
                    .portfolioItems(List.of(
                            portfolioItem(PortfolioType.SINGLE, "현관 중문 시공", List.of(PORTFOLIO_IMAGE_1)),
                            portfolioItem(PortfolioType.BEFORE_AFTER, "욕실 리모델링", List.of(OLD_PORTFOLIO_IMAGE, NEW_PORTFOLIO_IMAGE)),
                            portfolioItem(PortfolioType.GALLERY, "신혼집 전체 인테리어", List.of(PORTFOLIO_IMAGE_2, SECOND_PORTFOLIO_IMAGE))
                    ))
                    .build();

            // when
            EngineerCreateResponse response = engineerService.registerEngineer(request);

            // then
            List<Portfolio> savedPortfolios =
                    portfolioRepository.findByEngineer_EngineerId(response.getEngineerId());
            assertThat(savedPortfolios)
                    .extracting(Portfolio::getType, Portfolio::getTitle)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(PortfolioType.SINGLE, "현관 중문 시공"),
                            org.assertj.core.groups.Tuple.tuple(PortfolioType.BEFORE_AFTER, "욕실 리모델링"),
                            org.assertj.core.groups.Tuple.tuple(PortfolioType.GALLERY, "신혼집 전체 인테리어")
                    );
            assertThat(savedPortfolios.get(0).getImages())
                    .extracting(PortfolioImage::getImageUrl)
                    .containsExactly(PORTFOLIO_IMAGE_1);
            assertThat(savedPortfolios.get(1).getImages())
                    .extracting(PortfolioImage::getImageUrl)
                    .containsExactly(OLD_PORTFOLIO_IMAGE, NEW_PORTFOLIO_IMAGE);
            assertThat(savedPortfolios.get(2).getImages())
                    .extracting(PortfolioImage::getImageUrl)
                    .containsExactly(PORTFOLIO_IMAGE_2, SECOND_PORTFOLIO_IMAGE);
        }

        @Test
        @DisplayName("갤러리 포트폴리오는 이미지를 최대 8장까지 등록할 수 있다")
        void registerEngineer_galleryImagesAtMaximum() {
            // given
            List<String> imageUrls = portfolioImageUrls(8);
            EngineerRequestDto request = createRequestBuilder()
                    .portfolioItems(List.of(portfolioItem(PortfolioType.GALLERY, "아파트 전체 시공", imageUrls)))
                    .build();

            // when
            EngineerCreateResponse response = engineerService.registerEngineer(request);

            // then
            List<Portfolio> savedPortfolios =
                    portfolioRepository.findByEngineer_EngineerId(response.getEngineerId());
            assertThat(savedPortfolios)
                    .singleElement()
                    .satisfies(portfolio -> assertThat(portfolioImageUrls(portfolio))
                            .containsExactlyElementsOf(imageUrls));
        }

        @Test
        @DisplayName("갤러리 포트폴리오는 이미지를 9장 이상 등록할 수 없다")
        void registerEngineer_galleryImagesExceedMaximum() {
            // given
            EngineerRequestDto request = createRequestBuilder()
                    .portfolioItems(List.of(portfolioItem(PortfolioType.GALLERY, "아파트 전체 시공", portfolioImageUrls(9))))
                    .build();

            // when & then
            assertThatThrownBy(() -> engineerService.registerEngineer(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_PORTFOLIO_IMAGE);
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
        @DisplayName("포트폴리오 시공 사례가 없으면 기술자를 등록할 수 없다")
        void registerEngineer_portfolioItemsAreMissing(List<String> imageUrls) {
            // given
            EngineerRequestDto request = createRequestBuilder()
                    .portfolioItems(portfolioItemsFromImages(imageUrls))
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
        void registerEngineer_portfolioItemsAreBlank() {
            // given
            EngineerRequestDto request = createRequestBuilder()
                    .portfolioItems(portfolioItemsFromImages(List.of(" ", "  ")))
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
                    .portfolioItems(portfolioItemsFromImages(List.of("https://evil.example.com/portfolio.png")))
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
            EngineerRequestDto request = createRequestBuilder()
                    .portfolioItems(List.of(portfolioItem(PortfolioType.GALLERY, null, List.of(
                            " " + PORTFOLIO_IMAGE_1 + " ",
                            " ",
                            PORTFOLIO_IMAGE_1,
                            PORTFOLIO_IMAGE_2
                    ))))
                    .build();

            // when
            EngineerCreateResponse response = engineerService.registerEngineer(request);

            // then
            List<Portfolio> savedPortfolios =
                    portfolioRepository.findByEngineer_EngineerId(response.getEngineerId());
            assertThat(savedPortfolios)
                    .singleElement()
                    .satisfies(portfolio -> assertThat(portfolioImageUrls(portfolio))
                            .containsExactly(PORTFOLIO_IMAGE_1, PORTFOLIO_IMAGE_2));
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
    @DisplayName("관리자 기술자 등록")
    class RegisterAdminCreatedEngineer {

        @Test
        @DisplayName("관리자는 사용자와 연결되지 않은 기술자 프로필을 등록할 수 있다")
        void registerAdminCreatedEngineer_success() {
            // given
            User adminUser = saveAdminUser(900000000010L, "관리자");
            given(userService.getCurrentUser()).willReturn(adminUser);

            EngineerRequestDto request = createRequestBuilder()
                    .introduction(" ")
                    .build();

            // when
            EngineerCreateResponse response = engineerService.registerAdminCreatedEngineer(request);

            // then
            Engineer savedEngineer = engineerRepository.findById(response.getEngineerId())
                    .orElseThrow();
            List<EngineerCategory> savedCategories =
                    engineerCategoryRepository.findByEngineer_EngineerIdIn(List.of(savedEngineer.getEngineerId()));
            List<Portfolio> savedPortfolios =
                    portfolioRepository.findByEngineer_EngineerId(savedEngineer.getEngineerId());

            assertThat(savedEngineer.getUser()).isNull();
            assertThat(savedEngineer.getRegion().getRegionId()).isEqualTo(region.getRegionId());
            assertThat(savedEngineer.getProfileImage()).isEqualTo(PROFILE_IMAGE);
            assertThat(savedEngineer.getIntroduction()).isEqualTo("상세 소개는 추후 보완될 예정입니다.");
            assertThat(savedEngineer.getPhone()).isEqualTo("01012345678");
            assertThat(savedEngineer.getEngineerName()).isEqualTo("김기술");
            assertThat(savedEngineer.getRegistrationType()).isEqualTo(EngineerRegistrationType.ADMIN_CREATED);
            assertThat(savedEngineer.getClaimStatus()).isEqualTo(EngineerClaimStatus.UNCLAIMED);

            assertThat(savedCategories)
                    .extracting(engineerCategory -> engineerCategory.getCategory().getCategoryName())
                    .containsExactlyInAnyOrder("주방", "타일");
            assertThat(savedPortfolios)
                    .extracting(EngineerServiceTest.this::firstPortfolioImageUrl)
                    .containsExactly(PORTFOLIO_IMAGE_1, PORTFOLIO_IMAGE_2);
        }

        @Test
        @DisplayName("관리자가 아니면 수동 기술자 프로필을 등록할 수 없다")
        void registerAdminCreatedEngineer_forbidden() {
            // given
            EngineerRequestDto request = createValidRequest();

            // when & then
            assertThatThrownBy(() -> engineerService.registerAdminCreatedEngineer(request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);

            assertThat(engineerRepository.count()).isZero();
        }

        @Test
        @DisplayName("관리자 등록 시 자기소개 입력값이 있어도 기본 문구로 저장한다")
        void registerAdminCreatedEngineer_usesDefaultIntroduction() {
            // given
            User adminUser = saveAdminUser(900000000010L, "관리자");
            given(userService.getCurrentUser()).willReturn(adminUser);

            EngineerRequestDto request = createRequestBuilder()
                    .introduction(" 꼼꼼하게 시공합니다. ")
                    .build();

            // when
            EngineerCreateResponse response = engineerService.registerAdminCreatedEngineer(request);

            // then
            Engineer savedEngineer = engineerRepository.findById(response.getEngineerId())
                    .orElseThrow();
            assertThat(savedEngineer.getIntroduction()).isEqualTo("상세 소개는 추후 보완될 예정입니다.");
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
            portfolioRepository.save(createSinglePortfolio(engineer, OLD_PORTFOLIO_IMAGE));

            EngineerRequestDto request = createRequestBuilder()
                    .categories(List.of("타일"))
                    .portfolioItems(portfolioItemsFromImages(List.of(NEW_PORTFOLIO_IMAGE)))
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
                    .extracting(EngineerServiceTest.this::firstPortfolioImageUrl)
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
            portfolioRepository.save(createSinglePortfolio(engineer, OLD_PORTFOLIO_IMAGE));

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
            portfolioRepository.save(createSinglePortfolio(engineer, OLD_PORTFOLIO_IMAGE));

            EngineerRequestDto request = createRequestBuilder()
                    .categories(List.of(" 주방 ", " ", "주방", "타일"))
                    .portfolioItems(List.of(portfolioItem(PortfolioType.GALLERY, null, List.of(
                                    " " + NEW_PORTFOLIO_IMAGE + " ",
                                    " ",
                                    NEW_PORTFOLIO_IMAGE,
                                    SECOND_PORTFOLIO_IMAGE
                            ))))
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
                    .singleElement()
                    .satisfies(portfolio -> assertThat(portfolioImageUrls(portfolio))
                            .containsExactly(NEW_PORTFOLIO_IMAGE, SECOND_PORTFOLIO_IMAGE));
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
                    portfolioRepository.save(createSinglePortfolio(engineer, OLD_PORTFOLIO_IMAGE));
            entityManager.flush();
            portfolioImageRepository.updateThumbnailImageIfImageUnchanged(
                    unchangedPortfolio.getImages().getFirst().getPortfolioImageId(),
                    OLD_PORTFOLIO_IMAGE,
                    imageUrl("thumbnail/old-portfolio.jpg")
            );
            entityManager.flush();
            entityManager.clear();

            EngineerRequestDto request = createRequestBuilder()
                    .portfolioItems(List.of(
                            portfolioItem(
                                    unchangedPortfolio.getPortfolioId(),
                                    PortfolioType.SINGLE,
                                    null,
                                    List.of(OLD_PORTFOLIO_IMAGE)
                            ),
                            portfolioItem(PortfolioType.SINGLE, null, List.of(NEW_PORTFOLIO_IMAGE))
                    ))
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
                    .extracting(EngineerServiceTest.this::firstPortfolioImageUrl)
                    .containsExactlyInAnyOrder(OLD_PORTFOLIO_IMAGE, NEW_PORTFOLIO_IMAGE);
            assertThat(updatedPortfolios)
                    .filteredOn(portfolio -> firstPortfolioImageUrl(portfolio).equals(OLD_PORTFOLIO_IMAGE))
                    .singleElement()
                    .satisfies(portfolio -> {
                        assertThat(portfolio.getPortfolioId()).isEqualTo(unchangedPortfolio.getPortfolioId());
                        assertThat(EngineerServiceTest.this.firstPortfolioThumbnailUrl(portfolio))
                                .isEqualTo(imageUrl("thumbnail/old-portfolio.jpg"));
                    });
        }

        @Test
        @DisplayName("포트폴리오 제목만 수정하면 이미지와 썸네일을 유지하고 원격 이미지 검증을 다시 하지 않는다")
        void updateEngineer_changesOnlyPortfolioTitle() {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "기존기술자",
                    "기존 소개"
            );
            Portfolio portfolio = portfolioRepository.save(
                    Portfolio.create(
                            engineer,
                            PortfolioType.SINGLE,
                            "기존 제목",
                            0,
                            List.of(OLD_PORTFOLIO_IMAGE)
                    )
            );
            entityManager.flush();
            portfolioImageRepository.updateThumbnailImageIfImageUnchanged(
                    portfolio.getImages().getFirst().getPortfolioImageId(),
                    OLD_PORTFOLIO_IMAGE,
                    imageUrl("thumbnail/old-portfolio.jpg")
            );
            entityManager.flush();
            entityManager.clear();

            EngineerRequestDto request = createRequestBuilder()
                    .profileImage(OLD_PROFILE_IMAGE)
                    .phone("010-1111-2222")
                    .portfolioItems(List.of(portfolioItem(
                            portfolio.getPortfolioId(),
                            PortfolioType.SINGLE,
                            "새 제목",
                            List.of(OLD_PORTFOLIO_IMAGE)
                    )))
                    .build();

            // when
            engineerService.updateEngineer(engineer.getEngineerId(), request);
            entityManager.flush();
            entityManager.clear();

            // then
            Portfolio updatedPortfolio = portfolioRepository.findByEngineer_EngineerId(engineer.getEngineerId())
                    .getFirst();
            assertThat(updatedPortfolio.getPortfolioId()).isEqualTo(portfolio.getPortfolioId());
            assertThat(updatedPortfolio.getTitle()).isEqualTo("새 제목");
            assertThat(updatedPortfolio.getImages().getFirst().getImageUrl()).isEqualTo(OLD_PORTFOLIO_IMAGE);
            assertThat(updatedPortfolio.getImages().getFirst().getThumbnailImageUrl())
                    .isEqualTo(imageUrl("thumbnail/old-portfolio.jpg"));
            verify(s3Client, never()).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
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
        @DisplayName("포트폴리오 시공 사례가 없으면 기술자 정보를 수정할 수 없다")
        void updateEngineer_portfolioItemsAreMissing(List<String> imageUrls) {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "기존기술자",
                    "기존 소개"
            );
            portfolioRepository.save(createSinglePortfolio(engineer, OLD_PORTFOLIO_IMAGE));
            EngineerRequestDto request = createRequestBuilder()
                    .portfolioItems(portfolioItemsFromImages(imageUrls))
                    .phone("010-1111-2222")
                    .build();

            // when & then
            assertThatThrownBy(() -> engineerService.updateEngineer(engineer.getEngineerId(), request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_PORTFOLIO_IMAGE);

            assertThat(portfolioRepository.findByEngineer_EngineerId(engineer.getEngineerId()))
                    .extracting(EngineerServiceTest.this::firstPortfolioImageUrl)
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
            portfolioRepository.save(createSinglePortfolio(engineer, OLD_PORTFOLIO_IMAGE));
            EngineerRequestDto request = createRequestBuilder()
                    .portfolioItems(portfolioItemsFromImages(List.of("https://evil.example.com/portfolio.png")))
                    .phone("010-1111-2222")
                    .build();

            // when & then
            assertThatThrownBy(() -> engineerService.updateEngineer(engineer.getEngineerId(), request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_PORTFOLIO_IMAGE);

            assertThat(portfolioRepository.findByEngineer_EngineerId(engineer.getEngineerId()))
                    .extracting(EngineerServiceTest.this::firstPortfolioImageUrl)
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
    @DisplayName("관리자 기술자 수정")
    class UpdateEngineerByAdmin {

        @Test
        @DisplayName("관리자는 다른 사용자의 기술자 정보를 수정할 수 있다")
        void updateEngineerByAdmin_success() {
            // given
            User owner = saveUser(900000000002L, "기술자소유자");
            Engineer engineer = saveEngineer(
                    owner,
                    "01011112222",
                    "기존기술자",
                    "기존 소개"
            );
            Category kitchen = categoryRepository.findByCategoryName("주방").orElseThrow();
            engineerCategoryRepository.save(EngineerCategory.create(engineer, kitchen));
            portfolioRepository.save(createSinglePortfolio(engineer, OLD_PORTFOLIO_IMAGE));

            User adminUser = saveAdminUser(900000000010L, "관리자");
            given(userService.getCurrentUser()).willReturn(adminUser);

            EngineerRequestDto request = createRequestBuilder()
                    .categories(List.of("타일"))
                    .portfolioItems(portfolioItemsFromImages(List.of(NEW_PORTFOLIO_IMAGE)))
                    .profileImage(NEW_PROFILE_IMAGE)
                    .introduction(" 관리자 수정 소개 ")
                    .phone("010-2222-3333")
                    .engineerName(" 관리자수정기술자 ")
                    .headline(" 관리자 수정 짧은 소개 ")
                    .build();

            // when
            engineerService.updateEngineerByAdmin(engineer.getEngineerId(), request);

            // then
            Engineer updatedEngineer = engineerRepository.findById(engineer.getEngineerId())
                    .orElseThrow();
            List<EngineerCategory> updatedCategories =
                    engineerCategoryRepository.findByEngineer_EngineerIdIn(List.of(engineer.getEngineerId()));
            List<Portfolio> updatedPortfolios =
                    portfolioRepository.findByEngineer_EngineerId(engineer.getEngineerId());

            assertThat(updatedEngineer.getUser().getUserId()).isEqualTo(owner.getUserId());
            assertThat(updatedEngineer.getEngineerName()).isEqualTo("관리자수정기술자");
            assertThat(updatedEngineer.getHeadline()).isEqualTo("관리자 수정 짧은 소개");
            assertThat(updatedEngineer.getPhone()).isEqualTo("01022223333");
            assertThat(updatedEngineer.getProfileImage()).isEqualTo(NEW_PROFILE_IMAGE);
            assertThat(updatedEngineer.getIntroduction()).isEqualTo("관리자 수정 소개");
            assertThat(updatedCategories)
                    .extracting(mapping -> mapping.getCategory().getCategoryName())
                    .containsExactly("타일");
            assertThat(updatedPortfolios)
                    .extracting(EngineerServiceTest.this::firstPortfolioImageUrl)
                    .containsExactly(NEW_PORTFOLIO_IMAGE);
        }

        @Test
        @DisplayName("관리자가 아니면 관리자 수정 API로 기술자 정보를 수정할 수 없다")
        void updateEngineerByAdmin_forbidden() {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "기존기술자",
                    "기존 소개"
            );

            // when & then
            assertThatThrownBy(() -> engineerService.updateEngineerByAdmin(
                    engineer.getEngineerId(),
                    createValidRequest()
            ))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);
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
    @DisplayName("관리자 기술자 삭제")
    class DeleteEngineerByAdmin {

        @Test
        @DisplayName("관리자는 다른 사용자의 기술자 정보와 연결된 데이터를 삭제할 수 있다")
        void deleteEngineerByAdmin_success() {
            // given
            User owner = saveUser(900000000002L, "기술자소유자");
            Engineer engineer = saveEngineer(
                    owner,
                    "01011112222",
                    "삭제기술자",
                    "삭제 소개"
            );
            Category kitchen = categoryRepository.findByCategoryName("주방").orElseThrow();
            engineerCategoryRepository.save(EngineerCategory.create(engineer, kitchen));
            portfolioRepository.save(createSinglePortfolio(engineer, imageUrl("portfolio.jpg")));
            User interestedUser = saveUser(900000000003L, "관심사용자");
            interestedEngineerRepository.save(InterestedEngineer.create(interestedUser, engineer));

            User adminUser = saveAdminUser(900000000010L, "관리자");
            given(userService.getCurrentUser()).willReturn(adminUser);

            // when
            engineerService.deleteEngineerByAdmin(engineer.getEngineerId());
            entityManager.flush();

            // then
            assertThat(engineerRepository.findById(engineer.getEngineerId())).isEmpty();
            assertThat(engineerCategoryRepository.count()).isZero();
            assertThat(portfolioRepository.count()).isZero();
            assertThat(interestedEngineerRepository.count()).isZero();
        }

        @Test
        @DisplayName("관리자가 아니면 관리자 삭제 API로 기술자 정보를 삭제할 수 없다")
        void deleteEngineerByAdmin_forbidden() {
            // given
            Engineer engineer = saveEngineer(
                    currentUser,
                    "01011112222",
                    "삭제기술자",
                    "삭제 소개"
            );

            // when & then
            assertThatThrownBy(() -> engineerService.deleteEngineerByAdmin(engineer.getEngineerId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);

            assertThat(engineerRepository.findById(engineer.getEngineerId())).isPresent();
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
                    createSinglePortfolio(engineer, PORTFOLIO_IMAGE_1),
                    createSinglePortfolio(engineer, PORTFOLIO_IMAGE_2)
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
            assertThat(result.getPortfolioItems())
                    .extracting("type", "images")
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(PortfolioType.SINGLE, List.of(PORTFOLIO_IMAGE_1)),
                            org.assertj.core.groups.Tuple.tuple(PortfolioType.SINGLE, List.of(PORTFOLIO_IMAGE_2))
                    );
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
                    createSinglePortfolio(engineer, imageUrl("portfolio.jpg"))
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
    @DisplayName("기존 기술자 프로필 연결 요청")
    class RequestEngineerClaim {

        @Test
        @DisplayName("사용자와 연결되지 않은 수동 등록 기술자 프로필에 연결을 요청할 수 있다")
        void requestEngineerClaim_success() {
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

            // when
            engineerService.requestEngineerClaim(engineer.getEngineerId());
            entityManager.flush();
            entityManager.clear();

            // then
            Engineer requestedEngineer = engineerRepository.findById(engineer.getEngineerId())
                    .orElseThrow();
            assertThat(requestedEngineer.getClaimStatus()).isEqualTo(EngineerClaimStatus.REQUESTED);
            assertThat(requestedEngineer.getClaimRequester().getUserId()).isEqualTo(currentUser.getUserId());
            assertThat(requestedEngineer.getUser()).isNull();
        }

        @Test
        @DisplayName("이미 본인이 연결 요청한 기술자 프로필에 다시 요청하면 성공 처리한다")
        void requestEngineerClaim_alreadyRequestedBySameUser() {
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
            engineer.requestClaim(currentUser);
            entityManager.flush();
            entityManager.clear();

            // when & then
            assertThatCode(() -> engineerService.requestEngineerClaim(engineer.getEngineerId()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("다른 사용자가 이미 연결 요청 중인 기술자 프로필에는 요청할 수 없다")
        void requestEngineerClaim_alreadyRequestedByOtherUser() {
            // given
            User anotherUser = saveUser(900000000002L, "다른사용자");
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
            engineer.requestClaim(anotherUser);
            entityManager.flush();
            entityManager.clear();

            // when & then
            assertThatThrownBy(() -> engineerService.requestEngineerClaim(engineer.getEngineerId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ENGINEER_CLAIM_ALREADY_REQUESTED);
        }

        @Test
        @DisplayName("이미 사용자와 연결된 기술자 프로필에는 연결 요청할 수 없다")
        void requestEngineerClaim_alreadyClaimed() {
            // given
            User engineerUser = saveUser(900000000002L, "기술자사용자");
            Engineer engineer = saveEngineer(
                    engineerUser,
                    "01011112222",
                    "기술자",
                    "기술자 소개"
            );

            // when & then
            assertThatThrownBy(() -> engineerService.requestEngineerClaim(engineer.getEngineerId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ENGINEER_ALREADY_CLAIMED);
        }

        @Test
        @DisplayName("이미 기술자 등록이 완료된 사용자는 기존 프로필 연결을 요청할 수 없다")
        void requestEngineerClaim_currentUserAlreadyRegistered() {
            // given
            saveEngineer(
                    currentUser,
                    "01011112222",
                    "내기술자",
                    "내 소개"
            );
            Engineer manualEngineer = engineerRepository.save(Engineer.createAdminCreated(
                    region,
                    OLD_PROFILE_IMAGE,
                    "기존 소개",
                    "기존 상호",
                    "기존 주소",
                    "01033334444",
                    "수동기술자",
                    null
            ));

            // when & then
            assertThatThrownBy(() -> engineerService.requestEngineerClaim(manualEngineer.getEngineerId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ENGINEER_ALREADY_REGISTERED);
        }

        @Test
        @DisplayName("존재하지 않는 기술자 프로필에는 연결 요청할 수 없다")
        void requestEngineerClaim_engineerNotFound() {
            // when & then
            assertThatThrownBy(() -> engineerService.requestEngineerClaim(999L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ENGINEER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("관리자 기술자 프로필 연결 요청 처리")
    class AdminEngineerClaim {

        @Test
        @DisplayName("관리자는 연결 요청 목록을 조회할 수 있다")
        void getEngineerClaimRequests_success() {
            // given
            User adminUser = saveAdminUser(900000000002L, "관리자");
            User requester = saveUser(900000000003L, "요청자");
            Engineer requestedEngineer = engineerRepository.save(Engineer.createAdminCreated(
                    region,
                    OLD_PROFILE_IMAGE,
                    "기존 소개",
                    "기존 상호",
                    "기존 주소",
                    "01011112222",
                    "요청기술자",
                    null
            ));
            requestedEngineer.requestClaim(requester);
            engineerRepository.save(Engineer.createAdminCreated(
                    region,
                    OLD_PROFILE_IMAGE,
                    "기존 소개",
                    "기존 상호",
                    "기존 주소",
                    "01033334444",
                    "미요청기술자",
                    null
            ));
            given(userService.getCurrentUser()).willReturn(adminUser);

            // when
            Page<EngineerClaimRequestResponseDto> result =
                    engineerService.getEngineerClaimRequests(PageRequest.of(0, 20));

            // then
            assertThat(result.getContent()).singleElement()
                    .satisfies(response -> {
                        assertThat(response.getEngineerId()).isEqualTo(requestedEngineer.getEngineerId());
                        assertThat(response.getEngineerName()).isEqualTo("요청기술자");
                        assertThat(response.getPhone()).isEqualTo("01011112222");
                        assertThat(response.getRequesterUserId()).isEqualTo(requester.getUserId());
                        assertThat(response.getRequesterNickname()).isEqualTo("요청자");
                        assertThat(response.getClaimStatus()).isEqualTo(EngineerClaimStatus.REQUESTED);
                    });
        }

        @Test
        @DisplayName("관리자가 연결 요청을 승인하면 기술자 프로필이 요청자와 연결된다")
        void approveEngineerClaim_success() {
            // given
            User adminUser = saveAdminUser(900000000002L, "관리자");
            User requester = saveUser(900000000003L, "요청자");
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
            engineer.requestClaim(requester);
            given(userService.getCurrentUser()).willReturn(adminUser);

            // when
            engineerService.approveEngineerClaim(engineer.getEngineerId());
            entityManager.flush();
            entityManager.clear();

            // then
            Engineer approvedEngineer = engineerRepository.findById(engineer.getEngineerId())
                    .orElseThrow();
            assertThat(approvedEngineer.getUser().getUserId()).isEqualTo(requester.getUserId());
            assertThat(approvedEngineer.getClaimRequester()).isNull();
            assertThat(approvedEngineer.getClaimStatus()).isEqualTo(EngineerClaimStatus.APPROVED);
        }

        @Test
        @DisplayName("관리자가 연결 요청을 거절하면 요청자가 제거되고 거절 상태가 된다")
        void rejectEngineerClaim_success() {
            // given
            User adminUser = saveAdminUser(900000000002L, "관리자");
            User requester = saveUser(900000000003L, "요청자");
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
            engineer.requestClaim(requester);
            given(userService.getCurrentUser()).willReturn(adminUser);

            // when
            engineerService.rejectEngineerClaim(engineer.getEngineerId());
            entityManager.flush();
            entityManager.clear();

            // then
            Engineer rejectedEngineer = engineerRepository.findById(engineer.getEngineerId())
                    .orElseThrow();
            assertThat(rejectedEngineer.getUser()).isNull();
            assertThat(rejectedEngineer.getClaimRequester()).isNull();
            assertThat(rejectedEngineer.getClaimStatus()).isEqualTo(EngineerClaimStatus.REJECTED);
        }

        @Test
        @DisplayName("관리자가 아니면 연결 요청 목록을 조회할 수 없다")
        void getEngineerClaimRequests_notAdmin() {
            // when & then
            assertThatThrownBy(() -> engineerService.getEngineerClaimRequests(PageRequest.of(0, 20)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("요청 대기 상태가 아닌 기술자 프로필은 승인할 수 없다")
        void approveEngineerClaim_notRequested() {
            // given
            User adminUser = saveAdminUser(900000000002L, "관리자");
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
            given(userService.getCurrentUser()).willReturn(adminUser);

            // when & then
            assertThatThrownBy(() -> engineerService.approveEngineerClaim(engineer.getEngineerId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ENGINEER_CLAIM_NOT_REQUESTED);
        }

        @Test
        @DisplayName("요청자가 이미 다른 기술자 프로필을 가지고 있으면 승인할 수 없다")
        void approveEngineerClaim_requesterAlreadyRegistered() {
            // given
            User adminUser = saveAdminUser(900000000002L, "관리자");
            User requester = saveUser(900000000003L, "요청자");
            Engineer requestedEngineer = engineerRepository.save(Engineer.createAdminCreated(
                    region,
                    OLD_PROFILE_IMAGE,
                    "기존 소개",
                    "기존 상호",
                    "기존 주소",
                    "01011112222",
                    "수동기술자",
                    null
            ));
            requestedEngineer.requestClaim(requester);
            saveEngineer(
                    requester,
                    "01033334444",
                    "이미등록된기술자",
                    "이미 등록"
            );
            given(userService.getCurrentUser()).willReturn(adminUser);

            // when & then
            assertThatThrownBy(() -> engineerService.approveEngineerClaim(requestedEngineer.getEngineerId()))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ENGINEER_ALREADY_REGISTERED);
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

        @Test
        @DisplayName("관리자는 다른 사용자의 기술자를 제외하여 전화번호 중복 여부를 확인할 수 있다")
        void checkPhoneDuplicate_adminCanExcludeAnyEngineer() {
            // given
            User owner = saveUser(900000000002L, "기술자소유자");
            Engineer engineer = saveEngineer(
                    owner,
                    "01011112222",
                    "기존기술자",
                    "기존 소개"
            );
            User adminUser = saveAdminUser(900000000010L, "관리자");
            given(userService.getCurrentUser()).willReturn(adminUser);

            // when
            PhoneDuplicateCheckResponseDto result =
                    engineerService.checkPhoneDuplicate(
                            "010-1111-2222",
                            engineer.getEngineerId()
                    );

            // then
            assertThat(result.isDuplicated()).isFalse();
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
            List<String> imageUrls,
            String phone
    ) {
        return createRequestBuilder()
                .categories(categories)
                .portfolioItems(portfolioItemsFromImages(imageUrls))
                .phone(phone)
                .build();
    }

    private EngineerRequestDto.EngineerRequestDtoBuilder createRequestBuilder() {
        return EngineerRequestDto.builder()
                .categories(List.of("주방", "타일"))
                .portfolioItems(portfolioItemsFromImages(List.of(PORTFOLIO_IMAGE_1, PORTFOLIO_IMAGE_2)))
                .introduction(" 테스트 소개 ")
                .regionId(region.getRegionId())
                .profileImage(" " + PROFILE_IMAGE + " ")
                .storeName(" 테스트 상호 ")
                .storeAddress(" 서울시 테스트구 ")
                .phone("010-1234-5678")
                .engineerName(" 김기술 ")
                .headline(" 주방 시공 전문 ");
    }

    private PortfolioItemRequestDto portfolioItem(PortfolioType type, String title, List<String> images) {
        return portfolioItem(null, type, title, images);
    }

    private PortfolioItemRequestDto portfolioItem(
            Long portfolioId,
            PortfolioType type,
            String title,
            List<String> images
    ) {
        return PortfolioItemRequestDto.builder()
                .portfolioId(portfolioId)
                .type(type)
                .title(title)
                .images(images)
                .build();
    }

    private List<PortfolioItemRequestDto> portfolioItemsFromImages(List<String> images) {
        if (images == null) {
            return null;
        }
        return images.stream()
                .map(image -> portfolioItem(PortfolioType.SINGLE, null, List.of(image)))
                .toList();
    }

    private List<String> portfolioImageUrls(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> imageUrl("gallery-" + index + ".jpg"))
                .toList();
    }

    private Portfolio createSinglePortfolio(Engineer engineer, String imageUrl) {
        return Portfolio.create(engineer, PortfolioType.SINGLE, null, 0, List.of(imageUrl));
    }

    private String firstPortfolioImageUrl(Portfolio portfolio) {
        return portfolio.getImages().getFirst().getImageUrl();
    }

    private List<String> portfolioImageUrls(Portfolio portfolio) {
        return portfolio.getImages().stream()
                .map(portfolioImage -> portfolioImage.getImageUrl())
                .toList();
    }

    private String firstPortfolioThumbnailUrl(Portfolio portfolio) {
        return portfolio.getImages().getFirst().getThumbnailImageUrl();
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

    private User saveAdminUser(Long kakaoId, String nickname) {
        User user = User.builder()
                .region(region)
                .kakaoId(kakaoId)
                .nickname(nickname)
                .role(UserRole.ADMIN)
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

package com.sonbadrim_server.domain.engineer.controller;

import com.sonbadrim_server.domain.engineer.dto.request.EngineerRequestDto;
import com.sonbadrim_server.domain.engineer.dto.request.PortfolioItemRequestDto;
import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.engineer.repository.EngineerRepository;
import com.sonbadrim_server.domain.portfolio.entity.Portfolio;
import com.sonbadrim_server.domain.portfolio.entity.PortfolioType;
import com.sonbadrim_server.domain.portfolio.repository.PortfolioRepository;
import com.sonbadrim_server.domain.region.entity.Region;
import com.sonbadrim_server.domain.region.repository.RegionRepository;
import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.entity.UserRole;
import com.sonbadrim_server.domain.user.entity.UserStatus;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@TestPropertySource(properties = "cloudflare.r2.public-domain=https://image.test")
@Transactional
@Sql("/sql/engineer-test-data.sql")
class EngineerRegistrationUpdateE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private EngineerRepository engineerRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private S3Client s3Client;

    private User currentUser;
    private Region region;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        region = regionRepository.findAllByOrderByRegionIdAsc().getFirst();
        currentUser = saveUser(910000000001L, "E2E유저");
        setAuthenticatedUser(currentUser);

        given(s3Client.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
                .willReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(),
                        new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
                ));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("기술자를 등록한 뒤 포트폴리오 구조를 포함해 수정할 수 있다")
    void registerAndUpdateEngineer() throws Exception {
        EngineerRequestDto registerRequest = createRegisterRequest();

        mockMvc.perform(post("/engineers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.engineerId").exists());

        Engineer savedEngineer = engineerRepository.findAll().getFirst();
        Long engineerId = savedEngineer.getEngineerId();
        flushAndClear();

        mockMvc.perform(get("/engineers/{engineerId}", engineerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.engineerId").value(engineerId))
                .andExpect(jsonPath("$.data.engineerName").value("등록기술자"))
                .andExpect(jsonPath("$.data.headline").value("등록 헤드라인"))
                .andExpect(jsonPath("$.data.phone").value("01012345678"))
                .andExpect(jsonPath("$.data.categories[*]", containsInAnyOrder("주방", "타일")))
                .andExpect(jsonPath("$.data.portfolioItems.length()").value(3))
                .andExpect(jsonPath("$.data.portfolioItems[0].type").value("SINGLE"))
                .andExpect(jsonPath("$.data.portfolioItems[0].title").value("현관 중문 시공"))
                .andExpect(jsonPath("$.data.portfolioItems[0].images[0]").value(imageUrl("single.jpg")))
                .andExpect(jsonPath("$.data.portfolioItems[1].type").value("BEFORE_AFTER"))
                .andExpect(jsonPath("$.data.portfolioItems[1].images[0]").value(imageUrl("before.jpg")))
                .andExpect(jsonPath("$.data.portfolioItems[1].images[1]").value(imageUrl("after.jpg")))
                .andExpect(jsonPath("$.data.portfolioItems[2].type").value("GALLERY"))
                .andExpect(jsonPath("$.data.portfolioItems[2].images.length()").value(3));

        EngineerRequestDto updateRequest = createUpdateRequest();

        mockMvc.perform(patch("/engineers/{engineerId}", engineerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        flushAndClear();

        mockMvc.perform(get("/engineers/{engineerId}", engineerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.engineerName").value("수정기술자"))
                .andExpect(jsonPath("$.data.headline").value("수정 헤드라인"))
                .andExpect(jsonPath("$.data.phone").value("01099998888"))
                .andExpect(jsonPath("$.data.categories.length()").value(1))
                .andExpect(jsonPath("$.data.categories[0]").value("타일"))
                .andExpect(jsonPath("$.data.portfolioItems.length()").value(2))
                .andExpect(jsonPath("$.data.portfolioItems[0].type").value("BEFORE_AFTER"))
                .andExpect(jsonPath("$.data.portfolioItems[0].title").value("수정 욕실 리모델링"))
                .andExpect(jsonPath("$.data.portfolioItems[0].images[0]").value(imageUrl("updated-before.jpg")))
                .andExpect(jsonPath("$.data.portfolioItems[0].images[1]").value(imageUrl("updated-after.jpg")))
                .andExpect(jsonPath("$.data.portfolioItems[1].type").value("GALLERY"))
                .andExpect(jsonPath("$.data.portfolioItems[1].title").value("수정 신혼집 시공"))
                .andExpect(jsonPath("$.data.portfolioItems[1].images.length()").value(2));

        List<Portfolio> portfolios = portfolioRepository.findByEngineer_EngineerId(engineerId);
        assertThat(portfolios).hasSize(2);
        assertThat(portfolios)
                .extracting(Portfolio::getType)
                .containsExactly(PortfolioType.BEFORE_AFTER, PortfolioType.GALLERY);
        assertThat(portfolios.stream().flatMap(portfolio -> portfolio.getImages().stream()))
                .hasSize(4);
    }

    private EngineerRequestDto createRegisterRequest() {
        return EngineerRequestDto.builder()
                .categories(List.of("주방", "타일"))
                .portfolioItems(List.of(
                        portfolioItem(PortfolioType.SINGLE, "현관 중문 시공", List.of(imageUrl("single.jpg"))),
                        portfolioItem(PortfolioType.BEFORE_AFTER, "욕실 리모델링", List.of(
                                imageUrl("before.jpg"),
                                imageUrl("after.jpg")
                        )),
                        portfolioItem(PortfolioType.GALLERY, "신혼집 전체 인테리어", List.of(
                                imageUrl("gallery-1.jpg"),
                                imageUrl("gallery-2.jpg"),
                                imageUrl("gallery-3.jpg")
                        ))
                ))
                .introduction("등록 소개입니다.")
                .regionId(region.getRegionId())
                .profileImage(imageUrl("profile.jpg"))
                .storeName("등록상호")
                .storeAddress("서울 테스트로 1")
                .phone("010-1234-5678")
                .engineerName("등록기술자")
                .headline("등록 헤드라인")
                .build();
    }

    private EngineerRequestDto createUpdateRequest() {
        return EngineerRequestDto.builder()
                .categories(List.of("타일"))
                .portfolioItems(List.of(
                        portfolioItem(PortfolioType.BEFORE_AFTER, "수정 욕실 리모델링", List.of(
                                imageUrl("updated-before.jpg"),
                                imageUrl("updated-after.jpg")
                        )),
                        portfolioItem(PortfolioType.GALLERY, "수정 신혼집 시공", List.of(
                                imageUrl("updated-gallery-1.jpg"),
                                imageUrl("updated-gallery-2.jpg")
                        ))
                ))
                .introduction("수정 소개입니다.")
                .regionId(region.getRegionId())
                .profileImage(imageUrl("updated-profile.jpg"))
                .storeName("수정상호")
                .storeAddress("서울 수정로 1")
                .phone("010-9999-8888")
                .engineerName("수정기술자")
                .headline("수정 헤드라인")
                .build();
    }

    private PortfolioItemRequestDto portfolioItem(PortfolioType type, String title, List<String> images) {
        return PortfolioItemRequestDto.builder()
                .type(type)
                .title(title)
                .images(images)
                .build();
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

    private void setAuthenticatedUser(User user) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(String.valueOf(user.getUserId()), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private static String imageUrl(String fileName) {
        return "https://image.test/engineer/" + fileName;
    }
}

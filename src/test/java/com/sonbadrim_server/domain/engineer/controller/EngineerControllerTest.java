package com.sonbadrim_server.domain.engineer.controller;

import com.sonbadrim_server.domain.engineer.dto.request.EngineerRequestDto;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerCreateResponse;
import com.sonbadrim_server.domain.engineer.service.EngineerService;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(EngineerController.class)
class EngineerControllerTest {

    @MockitoBean
    private EngineerService engineerService;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("기술자 등록")
    class RegisterEngineer {

        @DisplayName("올바른 형식의 정보로 기술자를 등록할 수 있다")
        @Test
        void registerEngineer_success() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder().build();

            given(engineerService.registerEngineer(any(EngineerRequestDto.class)))
                    .willReturn(new EngineerCreateResponse(1L));

            // when & then
            mockMvc.perform(post("/engineers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.engineerId").value(1L));

        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("시공 분야가 없으면 기술자를 등록할 수 없다")
        void registerEngineer_categoriesAreMissing(List<String> categories) throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder()
                    .categories(categories)
                    .build();

            // when & then
            expectValidationFailure(request, "categories");
        }

        @Test
        @DisplayName("시공 분야를 6개 이상 선택하여 기술자를 등록할 수 없다")
        void registerEngineer_categoriesExceedMaximum() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder()
                    .categories(List.of("주방", "도배", "마루", "장판", "욕실", "타일"))
                    .build();

            // when & then
            expectValidationFailure(request, "categories");
        }

        @Test
        @DisplayName("시공 분야를 최대 5개까지 선택하여 기술자를 등록할 수 있다")
        void registerEngineer_categoriesAtMaximum() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder()
                    .categories(List.of("주방", "도배", "마루", "장판", "욕실"))
                    .build();

            given(engineerService.registerEngineer(any(EngineerRequestDto.class)))
                    .willReturn(new EngineerCreateResponse(1L));

            // when & then
            expectRegistrationSuccess(request);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("포트폴리오 이미지가 없으면 기술자를 등록할 수 없다")
        void registerEngineer_portfolioImagesAreMissing(List<String> portfolioImages) throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder()
                    .portfolioImages(portfolioImages)
                    .build();

            // when & then
            expectValidationFailure(request, "portfolioImages");
        }

        @Test
        @DisplayName("포트폴리오 이미지를 7개 이상 등록할 수 없다")
        void registerEngineer_portfolioImagesExceedMaximum() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder()
                    .portfolioImages(List.of(
                            "https://example.com/portfolio1.jpg",
                            "https://example.com/portfolio2.jpg",
                            "https://example.com/portfolio3.jpg",
                            "https://example.com/portfolio4.jpg",
                            "https://example.com/portfolio5.jpg",
                            "https://example.com/portfolio6.jpg",
                            "https://example.com/portfolio7.jpg"
                    ))
                    .build();

            // when & then
            expectValidationFailure(request, "portfolioImages");
        }

        @Test
        @DisplayName("포트폴리오 이미지를 최대 6개까지 등록할 수 있다")
        void registerEngineer_portfolioImagesAtMaximum() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder()
                    .portfolioImages(List.of(
                            "https://example.com/portfolio1.jpg",
                            "https://example.com/portfolio2.jpg",
                            "https://example.com/portfolio3.jpg",
                            "https://example.com/portfolio4.jpg",
                            "https://example.com/portfolio5.jpg",
                            "https://example.com/portfolio6.jpg"
                    ))
                    .build();

            given(engineerService.registerEngineer(any(EngineerRequestDto.class)))
                    .willReturn(new EngineerCreateResponse(1L));

            // when & then
            expectRegistrationSuccess(request);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   "})
        @DisplayName("기술자 이름이 없으면 기술자를 등록할 수 없다.")
        void registerEngineer_portfolioImagesIsMissing(String engineerName) throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder()
                    .engineerName(engineerName)
                    .build();

            // when & then
            expectValidationFailure(request, "engineerName");
        }

        @DisplayName("기술자 이름을 최대 20자까지 작성할 수 있다.")
        @Test
        void registerEngineer_engineerNameAtMaximum() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder()
                    .engineerName("가".repeat(20))
                    .build();

            given(engineerService.registerEngineer(any(EngineerRequestDto.class)))
                    .willReturn(new EngineerCreateResponse(1L));

            // when & then
            expectRegistrationSuccess(request);
        }

        @ParameterizedTest
        @ValueSource(strings = {"1234", "010-123-456", "전화번호"})
        @DisplayName("올바르지 않은 전화번호로 기술자를 등록할 수 없다")
        void registerEngineer_phoneIsInvalid(String phone) throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder()
                    .phone(phone)
                    .build();

            // when & then
            expectValidationFailure(request, "phone");
        }

        @Test
        @DisplayName("전화번호 공개 여부를 선택하지 않으면 기술자를 등록할 수 없다")
        void registerEngineer_phonePublicIsMissing() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder()
                    .phonePublic(null)
                    .build();

            // when & then
            expectValidationFailure(request, "phonePublic");
        }

        @Test
        @DisplayName("자기소개를 300자를 초과하여 등록할 수 없다")
        void registerEngineer_introductionExceedsMaximum() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder()
                    .introduction("가".repeat(301))
                    .build();

            // when & then
            expectValidationFailure(request, "introduction");
        }

        @Test
        @DisplayName("상호명을 20자를 초과하여 등록할 수 없다")
        void registerEngineer_storeNameExceedsMaximum() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder()
                    .storeName("가".repeat(21))
                    .build();

            // when & then
            expectValidationFailure(request, "storeName");
        }

        @Test
        @DisplayName("가게 주소를 30자를 초과하여 등록할 수 없다")
        void registerEngineer_storeAddressExceedsMaximum() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder()
                    .storeAddress("가".repeat(31))
                    .build();

            // when & then
            expectValidationFailure(request, "storeAddress");
        }

        @Test
        @DisplayName("짧은 소개를 100자를 초과하여 등록할 수 없다")
        void registerEngineer_headlineExceedsMaximum() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder()
                    .headline("가".repeat(101))
                    .build();

            // when & then
            expectValidationFailure(request, "headline");
        }
    }

    private void expectRegistrationSuccess(EngineerRequestDto request) throws Exception {
        mockMvc.perform(post("/engineers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.engineerId").value(1L));
    }

    private void expectValidationFailure(EngineerRequestDto request, String fieldName) throws Exception {
        mockMvc.perform(post("/engineers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data.code").value("GLOBAL_400_1"))
                .andExpect(jsonPath("$.data.validation." + fieldName).exists());

        verify(engineerService, never())
                .registerEngineer(any(EngineerRequestDto.class));
    }

    private EngineerRequestDto.EngineerRequestDtoBuilder createEngineerRequestBuilder() {
        return EngineerRequestDto.builder()
                .categories(List.of("주방", "타일"))
                .portfolioImages(List.of("https://example.com/portfolio1.jpg",
                        "https://example.com/portfolio2.jpg"
                ))
                .introduction("주방과 타일 시공을 전문으로 합니다.")
                .regionId(1)
                .profileImage("https://example.com/portfolio.jpg")
                .storeName("인테리어 종합 공사")
                .storeAddress("뉴욕")
                .phone("010-1234-5678")
                .phonePublic(true)
                .engineerName("김도현")
                .headline("주방 시공 전문");
    }
}

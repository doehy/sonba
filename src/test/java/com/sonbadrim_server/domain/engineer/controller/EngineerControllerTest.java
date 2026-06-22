package com.sonbadrim_server.domain.engineer.controller;

import com.sonbadrim_server.domain.engineer.dto.request.EngineerRequestDto;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerCreateResponse;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerDetailResponseDto;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerSummaryResponseDto;
import com.sonbadrim_server.domain.engineer.service.EngineerService;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
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

    @Nested
    @DisplayName("기술자 수정")
    class UpdateEngineer {

        @Test
        @DisplayName("올바른 형식의 정보로 본인의 기술자 정보를 수정할 수 있다")
        void updateEngineer_success() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder()
                    .engineerName("수정된기술자")
                    .build();

            // when & then
            mockMvc.perform(patch("/engineers/{engineerId}", 7L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(engineerService).updateEngineer(
                    eq(7L),
                    any(EngineerRequestDto.class)
            );
        }

        @Test
        @DisplayName("올바르지 않은 형식의 정보로 기술자 정보를 수정할 수 없다")
        void updateEngineer_validationFailure() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder()
                    .engineerName(" ")
                    .build();

            // when & then
            mockMvc.perform(patch("/engineers/{engineerId}", 7L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("GLOBAL_400_1"))
                    .andExpect(jsonPath("$.data.validation.engineerName").exists());

            verify(engineerService, never())
                    .updateEngineer(anyLong(), any(EngineerRequestDto.class));
        }

        @Test
        @DisplayName("존재하지 않는 기술자 정보는 수정할 수 없다")
        void updateEngineer_engineerNotFound() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder().build();
            willThrow(new CustomException(ErrorCode.ENGINEER_NOT_FOUND))
                    .given(engineerService)
                    .updateEngineer(eq(999L), any(EngineerRequestDto.class));

            // when & then
            mockMvc.perform(patch("/engineers/{engineerId}", 999L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("ENGINEER_NOT_FOUND"));
        }

        @Test
        @DisplayName("다른 사용자의 기술자 정보를 수정할 수 없다")
        void updateEngineer_notOwner() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder().build();
            willThrow(new CustomException(ErrorCode.CANNOT_MODIFY_ENGINEER))
                    .given(engineerService)
                    .updateEngineer(eq(7L), any(EngineerRequestDto.class));

            // when & then
            mockMvc.perform(patch("/engineers/{engineerId}", 7L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("CANNOT_MODIFY_ENGINEER"));
        }
    }

    @Nested
    @DisplayName("기술자 목록 조회")
    class GetEngineerList {

        @Test
        @DisplayName("기술자 목록을 기본 페이지 설정으로 조회할 수 있다")
        void getEngineerList_defaultPageable() throws Exception {
            // given
            EngineerSummaryResponseDto engineer = EngineerSummaryResponseDto.builder()
                    .engineerId(1L)
                    .engineerName("김기술")
                    .profileImage("profile.jpg")
                    .categories(List.of("주방", "타일"))
                    .introduction("주방 시공 전문가입니다.")
                    .build();
            given(engineerService.getEngineerList(any(Pageable.class)))
                    .willReturn(new PageImpl<>(
                            List.of(engineer),
                            PageRequest.of(0, 20),
                            1
                    ));

            // when & then
            mockMvc.perform(get("/engineers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].engineerId").value(1L))
                    .andExpect(jsonPath("$.data.content[0].engineerName").value("김기술"))
                    .andExpect(jsonPath("$.data.content[0].profileImage").value("profile.jpg"))
                    .andExpect(jsonPath("$.data.content[0].categories[0]").value("주방"))
                    .andExpect(jsonPath("$.data.content[0].introduction").value("주방 시공 전문가입니다."))
                    .andExpect(jsonPath("$.data.totalElements").value(1));

            Pageable pageable = captureRequestedPageable();
            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getPageSize()).isEqualTo(20);
            assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
                    .isEqualTo(Sort.Direction.DESC);
        }

        @Test
        @DisplayName("페이지 번호와 크기를 지정하여 기술자 목록을 조회할 수 있다")
        void getEngineerList_customPageable() throws Exception {
            // given
            given(engineerService.getEngineerList(any(Pageable.class)))
                    .willReturn(new PageImpl<>(
                            List.of(),
                            PageRequest.of(2, 10),
                            0
                    ));

            // when & then
            mockMvc.perform(get("/engineers")
                            .param("page", "2")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isEmpty());

            Pageable pageable = captureRequestedPageable();
            assertThat(pageable.getPageNumber()).isEqualTo(2);
            assertThat(pageable.getPageSize()).isEqualTo(10);
            assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
                    .isEqualTo(Sort.Direction.DESC);
        }

        private Pageable captureRequestedPageable() {
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(engineerService).getEngineerList(captor.capture());
            return captor.getValue();
        }
    }

    @Nested
    @DisplayName("기술자 상세 조회")
    class GetEngineerDetail {

        @Test
        @DisplayName("기술자 상세 정보를 조회할 수 있다")
        void getEngineerDetail_success() throws Exception {
            // given
            EngineerDetailResponseDto response = EngineerDetailResponseDto.builder()
                    .engineerId(7L)
                    .userId(5L)
                    .engineerName("김기술")
                    .headline("주방 시공 전문")
                    .profileImage("profile.jpg")
                    .regionName("서울특별시 강남구")
                    .categories(List.of("주방", "타일"))
                    .introduction("상세 자기소개")
                    .storeName("기술 상사")
                    .storeAddress("서울특별시 강남구")
                    .phone("01012345678")
                    .phonePublic(true)
                    .interested(true)
                    .portfolioImages(List.of("portfolio1.jpg", "portfolio2.jpg"))
                    .build();
            given(engineerService.getEngineerDetail(7L))
                    .willReturn(response);

            // when & then
            mockMvc.perform(get("/engineers/{engineerId}", 7L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.engineerId").value(7L))
                    .andExpect(jsonPath("$.data.userId").value(5L))
                    .andExpect(jsonPath("$.data.engineerName").value("김기술"))
                    .andExpect(jsonPath("$.data.categories[0]").value("주방"))
                    .andExpect(jsonPath("$.data.interested").value(true))
                    .andExpect(jsonPath("$.data.portfolioImages[0]").value("portfolio1.jpg"));

            verify(engineerService).getEngineerDetail(7L);
        }

        @Test
        @DisplayName("존재하지 않는 기술자 상세 정보는 조회할 수 없다")
        void getEngineerDetail_engineerNotFound() throws Exception {
            // given
            given(engineerService.getEngineerDetail(999L))
                    .willThrow(new CustomException(ErrorCode.ENGINEER_NOT_FOUND));

            // when & then
            mockMvc.perform(get("/engineers/{engineerId}", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("ENGINEER_NOT_FOUND"));
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

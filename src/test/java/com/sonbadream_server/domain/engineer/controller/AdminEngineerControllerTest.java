package com.sonbadream_server.domain.engineer.controller;

import com.sonbadream_server.domain.engineer.dto.request.EngineerRequestDto;
import com.sonbadream_server.domain.engineer.dto.request.PortfolioItemRequestDto;
import com.sonbadream_server.domain.engineer.dto.response.EngineerCreateResponse;
import com.sonbadream_server.domain.engineer.service.EngineerService;
import com.sonbadream_server.domain.portfolio.entity.PortfolioType;
import com.sonbadream_server.domain.user.repository.UserRepository;
import com.sonbadream_server.global.exception.CustomException;
import com.sonbadream_server.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AdminEngineerController.class)
class AdminEngineerControllerTest {

    @MockitoBean
    private EngineerService engineerService;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("관리자 기술자 등록")
    class RegisterAdminCreatedEngineer {

        @Test
        @DisplayName("관리자는 수동 기술자 프로필을 등록할 수 있다")
        void registerAdminCreatedEngineer_success() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder().build();
            given(engineerService.registerAdminCreatedEngineer(any(EngineerRequestDto.class)))
                    .willReturn(new EngineerCreateResponse(7L));

            // when & then
            mockMvc.perform(post("/admin/engineers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.engineerId").value(7L));

            verify(engineerService).registerAdminCreatedEngineer(any(EngineerRequestDto.class));
        }

        @Test
        @DisplayName("관리자가 아니면 수동 기술자 프로필을 등록할 수 없다")
        void registerAdminCreatedEngineer_forbidden() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder().build();
            given(engineerService.registerAdminCreatedEngineer(any(EngineerRequestDto.class)))
                    .willThrow(new CustomException(ErrorCode.FORBIDDEN));

            // when & then
            mockMvc.perform(post("/admin/engineers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("기술자 이름이 없으면 수동 기술자 프로필을 등록할 수 없다")
        void registerAdminCreatedEngineer_engineerNameMissing() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder()
                    .engineerName(" ")
                    .build();

            // when & then
            mockMvc.perform(post("/admin/engineers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.validation.engineerName").exists());
        }
    }

    @Nested
    @DisplayName("관리자 기술자 수정")
    class UpdateEngineerByAdmin {

        @Test
        @DisplayName("관리자는 기술자 프로필을 수정할 수 있다")
        void updateEngineerByAdmin_success() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder()
                    .engineerName("수정기술자")
                    .build();

            // when & then
            mockMvc.perform(patch("/admin/engineers/{engineerId}", 7L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(engineerService).updateEngineerByAdmin(eq(7L), any(EngineerRequestDto.class));
        }

        @Test
        @DisplayName("존재하지 않는 기술자 프로필은 수정할 수 없다")
        void updateEngineerByAdmin_engineerNotFound() throws Exception {
            // given
            EngineerRequestDto request = createEngineerRequestBuilder().build();
            willThrow(new CustomException(ErrorCode.ENGINEER_NOT_FOUND))
                    .given(engineerService)
                    .updateEngineerByAdmin(eq(999L), any(EngineerRequestDto.class));

            // when & then
            mockMvc.perform(patch("/admin/engineers/{engineerId}", 999L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("ENGINEER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("관리자 기술자 삭제")
    class DeleteEngineerByAdmin {

        @Test
        @DisplayName("관리자는 기술자 프로필을 삭제할 수 있다")
        void deleteEngineerByAdmin_success() throws Exception {
            // when & then
            mockMvc.perform(delete("/admin/engineers/{engineerId}", 7L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(engineerService).deleteEngineerByAdmin(7L);
        }

        @Test
        @DisplayName("관리자가 아니면 기술자 프로필을 삭제할 수 없다")
        void deleteEngineerByAdmin_forbidden() throws Exception {
            // given
            willThrow(new CustomException(ErrorCode.FORBIDDEN))
                    .given(engineerService)
                    .deleteEngineerByAdmin(7L);

            // when & then
            mockMvc.perform(delete("/admin/engineers/{engineerId}", 7L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("FORBIDDEN"));
        }
    }

    private EngineerRequestDto.EngineerRequestDtoBuilder createEngineerRequestBuilder() {
        return EngineerRequestDto.builder()
                .categories(List.of("주방", "타일"))
                .portfolioItems(List.of(
                        singlePortfolioItem("https://example.com/portfolio1.jpg"),
                        singlePortfolioItem("https://example.com/portfolio2.jpg")
                ))
                .introduction(null)
                .regionId(1)
                .profileImage("https://example.com/profile.jpg")
                .storeName("인테리어 종합 공사")
                .storeAddress("서울시 강남구")
                .phone("010-1234-5678")
                .engineerName("김도현")
                .headline(null);
    }

    private PortfolioItemRequestDto singlePortfolioItem(String imageUrl) {
        return PortfolioItemRequestDto.builder()
                .type(PortfolioType.SINGLE)
                .images(List.of(imageUrl))
                .build();
    }
}

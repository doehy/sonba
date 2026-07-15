package com.sonbadrim_server.domain.engineer.repository;

import com.sonbadrim_server.domain.category.entity.Category;
import com.sonbadrim_server.domain.category.repository.CategoryRepository;
import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.engineer.entity.EngineerCategory;
import com.sonbadrim_server.domain.region.entity.Region;
import com.sonbadrim_server.domain.region.repository.RegionRepository;
import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.entity.UserRole;
import com.sonbadrim_server.domain.user.entity.UserStatus;
import com.sonbadrim_server.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/sql/engineer-test-data.sql")
class EngineerRepositoryTest {

    @Autowired
    private EngineerRepository engineerRepository;

    @Autowired
    private EngineerCategoryRepository engineerCategoryRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private Region region;
    private Category kitchen;
    private Category tile;

    @BeforeEach
    void setUp() {
        region = regionRepository.findAllByOrderByRegionIdAsc().getFirst();
        kitchen = categoryRepository.findByCategoryName("주방").orElseThrow();
        tile = categoryRepository.findByCategoryName("타일").orElseThrow();
    }

    @Nested
    @DisplayName("기술자 상세 조회")
    class FindDetailByEngineerId {

        @Test
        @DisplayName("기술자 상세 정보와 연관된 사용자, 지역, 시공 분야를 함께 조회할 수 있다")
        void findDetailByEngineerId_success() {
            // given
            User user = userRepository.save(User.builder()
                    .region(region)
                    .kakaoId(900000000001L)
                    .nickname("테스트유저")
                    .role(UserRole.USER)
                    .status(UserStatus.ACTIVE)
                    .build());
            Engineer engineer = engineerRepository.save(Engineer.create(
                    user,
                    region,
                    "profile.jpg",
                    "상세 소개",
                    "테스트 상호",
                    "테스트 주소",
                    "01011112222",
                    true,
                    "상세기술자",
                    "주방 시공 전문"
            ));
            engineerCategoryRepository.saveAll(List.of(
                    EngineerCategory.create(engineer, kitchen),
                    EngineerCategory.create(engineer, tile)
            ));

            entityManager.flush();
            entityManager.clear();

            // when
            Engineer foundEngineer = engineerRepository
                    .findDetailByEngineerId(engineer.getEngineerId())
                    .orElseThrow();

            // then
            PersistenceUnitUtil persistenceUnitUtil =
                    entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

            assertThat(foundEngineer.getEngineerId()).isEqualTo(engineer.getEngineerId());
            assertThat(persistenceUnitUtil.isLoaded(foundEngineer, "user")).isTrue();
            assertThat(persistenceUnitUtil.isLoaded(foundEngineer, "region")).isTrue();
            assertThat(persistenceUnitUtil.isLoaded(foundEngineer, "engineerCategories")).isTrue();
            assertThat(foundEngineer.getUser().getUserId()).isEqualTo(user.getUserId());
            assertThat(foundEngineer.getRegion().getRegionId()).isEqualTo(region.getRegionId());
            assertThat(foundEngineer.getEngineerCategories())
                    .allSatisfy(mapping ->
                            assertThat(persistenceUnitUtil.isLoaded(mapping, "category")).isTrue()
                    )
                    .extracting(mapping -> mapping.getCategory().getCategoryName())
                    .containsExactlyInAnyOrder("주방", "타일");
        }

        @Test
        @DisplayName("존재하지 않는 기술자 상세 정보는 조회되지 않는다")
        void findDetailByEngineerId_notFound() {
            // when
            var result = engineerRepository.findDetailByEngineerId(999L);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("기술자 검색")
    class SearchEngineers {

        @Test
        @DisplayName("카테고리 필터에 맞는 기술자를 중복 없이 조회한다")
        void searchEngineers_categoryFilter() {
            // given
            Engineer firstEngineer = saveEngineer(
                    900000000002L,
                    "첫번째유저",
                    "01022223333",
                    "타일기술자",
                    "타일과 주방 시공"
            );
            Engineer secondEngineer = saveEngineer(
                    900000000003L,
                    "두번째유저",
                    "01033334444",
                    "주방기술자",
                    "주방 시공"
            );
            engineerCategoryRepository.saveAll(List.of(
                    EngineerCategory.create(firstEngineer, kitchen),
                    EngineerCategory.create(firstEngineer, tile),
                    EngineerCategory.create(secondEngineer, kitchen)
            ));

            entityManager.flush();
            entityManager.clear();

            // when
            Slice<Engineer> result = engineerRepository.searchEngineers(
                    null,
                    List.of(tile.getCategoryId()),
                    null,
                    null,
                    PageRequest.of(0, 10)
            );

            // then
            assertThat(result.hasNext()).isFalse();
            assertThat(result.getContent())
                    .extracting(Engineer::getEngineerId)
                    .containsExactly(firstEngineer.getEngineerId());
        }

    }

    @Nested
    @DisplayName("기술자 추천")
    class RecommendEngineers {

        @Test
        @DisplayName("기준 기술자와 겹치는 카테고리가 많은 순서로 추천한다")
        void findRecommendedEngineers_orderByMatchedCategoryCount() {
            // given
            Engineer baseEngineer = saveEngineer(
                    900000000004L,
                    "세번째유저",
                    "01044445555",
                    "기준기술자",
                    "기준 시공"
            );
            Engineer exactMatchedEngineer = saveEngineer(
                    900000000005L,
                    "네번째유저",
                    "01055556666",
                    "주방타일기술자",
                    "주방 타일 시공"
            );
            Engineer partlyMatchedEngineer = saveEngineer(
                    900000000006L,
                    "다섯번째유저",
                    "01066667777",
                    "주방기술자",
                    "주방 시공"
            );
            Engineer notMatchedEngineer = saveEngineer(
                    900000000007L,
                    "여섯번째유저",
                    "01077778888",
                    "기타기술자",
                    "기타 시공"
            );
            engineerCategoryRepository.saveAll(List.of(
                    EngineerCategory.create(baseEngineer, kitchen),
                    EngineerCategory.create(baseEngineer, tile),
                    EngineerCategory.create(exactMatchedEngineer, kitchen),
                    EngineerCategory.create(exactMatchedEngineer, tile),
                    EngineerCategory.create(partlyMatchedEngineer, kitchen)
            ));

            entityManager.flush();
            entityManager.clear();

            EngineerRecommendationBaseInfo baseInfo = engineerRepository.findRecommendationBaseInfoByEngineerId(baseEngineer.getEngineerId())
                    .orElseThrow();
            List<Integer> baseCategoryIds = engineerCategoryRepository.findCategoryIdsByEngineerId(baseEngineer.getEngineerId());

            // when
            Slice<Engineer> result = engineerRepository.findRecommendedEngineers(
                    baseEngineer.getEngineerId(),
                    baseInfo.getRegionId(),
                    baseInfo.getCity(),
                    baseCategoryIds,
                    PageRequest.of(0, 10)
            );

            // then
            assertThat(result.hasNext()).isFalse();
            assertThat(result.getContent())
                    .extracting(Engineer::getEngineerId)
                    .containsExactly(
                            exactMatchedEngineer.getEngineerId(),
                            partlyMatchedEngineer.getEngineerId()
                    );
            assertThat(result.getContent())
                    .extracting(Engineer::getEngineerId)
                    .doesNotContain(notMatchedEngineer.getEngineerId());
        }
    }

    private Engineer saveEngineer(
            Long kakaoId,
            String nickname,
            String phone,
            String engineerName,
            String headline
    ) {
        User user = userRepository.save(User.builder()
                .region(region)
                .kakaoId(kakaoId)
                .nickname(nickname)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());
        return engineerRepository.save(Engineer.create(
                user,
                region,
                "profile.jpg",
                "상세 소개",
                "테스트 상호",
                "테스트 주소",
                phone,
                true,
                engineerName,
                headline
        ));
    }
}

package com.sonbadrim_server.domain.engineer.repository;

import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.engineer.entity.QEngineerCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static com.sonbadrim_server.domain.engineer.entity.QEngineer.engineer;

@RequiredArgsConstructor
public class EngineerRecommendationRepositoryImpl implements EngineerRecommendationRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Slice<Engineer> findRecommendedEngineers(
            Long baseEngineerId,
            Integer baseRegionId,
            String baseCity,
            List<Integer> baseCategoryIds,
            Pageable pageable
    ) {
        QEngineerCategory matchedCategory = new QEngineerCategory("matchedCategory");
        NumberExpression<Integer> sameRegionRank = new CaseBuilder()
                .when(engineer.region.regionId.eq(baseRegionId))
                .then(1)
                .otherwise(0);

        if (baseCategoryIds != null && !baseCategoryIds.isEmpty()) {
            List<Engineer> results = queryFactory
                    .select(matchedCategory.engineer)
                    .from(matchedCategory)
                    .join(matchedCategory.engineer, engineer)
                    .where(
                            matchedCategory.engineer.engineerId.ne(baseEngineerId),
                            engineer.region.city.eq(baseCity),
                            matchedCategory.category.categoryId.in(baseCategoryIds)
                    )
                    .groupBy(engineer)
                    .orderBy(
                            matchedCategory.engineerCategoryId.count().desc(),
                            sameRegionRank.desc(),
                            engineer.createdAt.desc()
                    )
                    .offset(pageable.getOffset())
                    .limit(pageable.getPageSize() + 1L)
                    .fetch();

            return toSlice(results, pageable);
        }

        List<Engineer> results = queryFactory
                .selectFrom(engineer)
                .where(
                        engineer.engineerId.ne(baseEngineerId),
                        engineer.region.city.eq(baseCity)
                )
                .orderBy(
                        sameRegionRank.desc(),
                        engineer.createdAt.desc()
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize() + 1L)
                .fetch();

        return toSlice(results, pageable);
    }

    private Slice<Engineer> toSlice(List<Engineer> results, Pageable pageable) {
        boolean hasNext = results.size() > pageable.getPageSize();
        if (hasNext) {
            results.remove(results.size() - 1);
        }
        return new SliceImpl<>(results, pageable, hasNext);
    }
}

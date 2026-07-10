package com.sonbadrim_server.domain.engineer.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sonbadrim_server.domain.engineer.entity.Engineer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static com.sonbadrim_server.domain.engineer.entity.QEngineer.engineer;
import static com.sonbadrim_server.domain.engineer.entity.QEngineerCategory.engineerCategory;
import static com.sonbadrim_server.domain.region.entity.QRegion.region;

@RequiredArgsConstructor
public class EngineerSearchRepositoryImpl implements EngineerSearchRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Slice<Engineer> searchEngineers(
            String keyword,
            List<Integer> categoryIds,
            String city,
            Integer regionId,
            Pageable pageable
    ) {
        BooleanBuilder where = new BooleanBuilder()
                .and(keywordContains(keyword))
                .and(cityEquals(city))
                .and(regionIdEquals(regionId))
                .and(categoryExists(categoryIds));

        List<Engineer> results = queryFactory
                .selectFrom(engineer)
                .join(engineer.region, region)
                .where(where)
                .orderBy(engineer.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize() + 1L)
                .fetch();

        return toSlice(results, pageable);
    }

    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null) {
            return null;
        }

        String likeKeyword = "%" + keyword + "%";
        return normalized(engineer.engineerName).like(likeKeyword)
                .or(normalized(engineer.headline).like(likeKeyword));
    }

    private BooleanExpression cityEquals(String city) {
        return city == null ? null : region.city.eq(city);
    }

    private BooleanExpression regionIdEquals(Integer regionId) {
        return regionId == null ? null : engineer.region.regionId.eq(regionId);
    }

    private BooleanExpression categoryExists(List<Integer> categoryIds) {
        if (categoryIds == null) {
            return null;
        }

        return JPAExpressions
                .selectOne()
                .from(engineerCategory)
                .where(
                        engineerCategory.engineer.eq(engineer),
                        engineerCategory.category.categoryId.in(categoryIds)
                )
                .exists();
    }

    private com.querydsl.core.types.dsl.StringTemplate normalized(
            com.querydsl.core.types.Expression<String> expression
    ) {
        return Expressions.stringTemplate(
                "lower(replace({0}, ' ', ''))",
                expression
        );
    }

    private Slice<Engineer> toSlice(List<Engineer> results, Pageable pageable) {
        boolean hasNext = results.size() > pageable.getPageSize();
        if (hasNext) {
            results.remove(results.size() - 1);
        }
        return new SliceImpl<>(results, pageable, hasNext);
    }
}

package com.sonbadrim_server.domain.engineer.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sonbadrim_server.domain.engineer.entity.Engineer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static com.sonbadrim_server.domain.engineer.entity.QEngineer.engineer;
import static com.sonbadrim_server.domain.engineer.entity.QEngineerCategory.engineerCategory;
import static com.sonbadrim_server.domain.region.entity.QRegion.region;

@RequiredArgsConstructor
public class EngineerSearchRepositoryImpl implements EngineerSearchRepository {

    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;

    @Override
    public Slice<Engineer> searchEngineers(
            String keyword,
            List<Integer> categoryIds,
            String city,
            Integer regionId,
            Pageable pageable
    ) {
        if (keyword != null) {
            Slice<Engineer> fullTextResult = searchEngineersByFullText(
                    keyword,
                    categoryIds,
                    city,
                    regionId,
                    pageable
            );
            if (!fullTextResult.isEmpty()) {
                return fullTextResult;
            }
        }

        return searchEngineersByLike(keyword, categoryIds, city, regionId, pageable);
    }

    private Slice<Engineer> searchEngineersByLike(
            String keyword,
            List<Integer> categoryIds,
            String city,
            Integer regionId,
            Pageable pageable
    ) {
        BooleanBuilder where = new BooleanBuilder()
                .and(keywordContains(normalizeLikeKeyword(keyword)))
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

    private Slice<Engineer> searchEngineersByFullText(
            String keyword,
            List<Integer> categoryIds,
            String city,
            Integer regionId,
            Pageable pageable
    ) {
        String fullTextKeyword = toFullTextBooleanKeyword(keyword);
        if (fullTextKeyword == null) {
            return new SliceImpl<>(List.of(), pageable, false);
        }

        try {
            Query query = entityManager.createNativeQuery(
                    buildFullTextSql(categoryIds, city, regionId),
                    Engineer.class
            );
            query.setParameter("fullTextKeyword", fullTextKeyword);
            if (city != null) {
                query.setParameter("city", city);
            }
            if (regionId != null) {
                query.setParameter("regionId", regionId);
            }
            if (categoryIds != null) {
                query.setParameter("categoryIds", categoryIds);
            }
            query.setFirstResult((int) pageable.getOffset());
            query.setMaxResults(pageable.getPageSize() + 1);

            @SuppressWarnings("unchecked")
            List<Engineer> results = query.getResultList();
            return toSlice(results, pageable);
        } catch (RuntimeException ignored) {
            return new SliceImpl<>(List.of(), pageable, false);
        }
    }

    private String buildFullTextSql(List<Integer> categoryIds, String city, Integer regionId) {
        StringBuilder sql = new StringBuilder("""
                select e.*
                from engineer e
                join region r on r.region_id = e.region_id
                where match(e.engineer_name, e.headline) against (:fullTextKeyword in boolean mode)
                """);

        if (city != null) {
            sql.append("""
                    
                    and r.city = :city
                    """);
        }
        if (regionId != null) {
            sql.append("""
                    
                    and r.region_id = :regionId
                    """);
        }
        if (categoryIds != null) {
            sql.append("""
                    
                    and exists (
                        select 1
                        from engineer_category ec
                        where ec.engineer_id = e.engineer_id
                          and ec.category_id in (:categoryIds)
                    )
                    """);
        }
        sql.append("""
                
                order by e.created_at desc
                """);
        return sql.toString();
    }

    private BooleanExpression keywordContains(String keyword) {
        if (keyword == null) {
            return null;
        }

        String likeKeyword = "%" + keyword + "%";
        return normalized(engineer.engineerName).like(likeKeyword)
                .or(normalized(engineer.headline).like(likeKeyword));
    }

    private String normalizeLikeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String withoutSpaces = keyword.replaceAll("\\s+", "");
        return withoutSpaces.isEmpty() ? null : withoutSpaces.toLowerCase(Locale.ROOT);
    }

    private String toFullTextBooleanKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        Set<String> tokens = new LinkedHashSet<>();
        for (String token : keyword.trim().split("\\s+")) {
            String sanitizedToken = sanitizeFullTextToken(token);
            if (!sanitizedToken.isEmpty()) {
                tokens.add(sanitizedToken);
            }
        }

        String withoutSpaces = sanitizeFullTextToken(keyword.replaceAll("\\s+", ""));
        if (!withoutSpaces.isEmpty()) {
            tokens.add(withoutSpaces);
        }

        if (tokens.isEmpty()) {
            return null;
        }

        return tokens.stream()
                .map(token -> token + "*")
                .collect(Collectors.joining(" "));
    }

    private String sanitizeFullTextToken(String token) {
        return token.replaceAll("[+\\-<>()~*\"@]", "").trim();
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

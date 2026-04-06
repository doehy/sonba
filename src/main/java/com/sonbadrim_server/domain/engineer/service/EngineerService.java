package com.sonbadrim_server.domain.engineer.service;

import com.sonbadrim_server.domain.category.entity.Category;
import com.sonbadrim_server.domain.category.repository.CategoryRepository;
import com.sonbadrim_server.domain.engineer.dto.request.EngineerCreateRequest;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerCreateResponse;
import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.engineer.entity.EngineerCategory;
import com.sonbadrim_server.domain.engineer.repository.EngineerCategoryRepository;
import com.sonbadrim_server.domain.engineer.repository.EngineerRepository;
import com.sonbadrim_server.domain.portfolio.entity.Portfolio;
import com.sonbadrim_server.domain.portfolio.repository.PortfolioRepository;
import com.sonbadrim_server.domain.region.entity.Region;
import com.sonbadrim_server.domain.region.repository.RegionRepository;
import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.service.UserService;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class EngineerService {

    private static final String PROFILE_FOLDER = "engineer-profile";
    private static final String PORTFOLIO_FOLDER = "portfolio";

    @Value("${cloudflare.r2.public-domain}")
    private String publicDomain;

    @Value("${app.image.default-engineer-profile}")
    private String defaultEngineerProfileImage;

    private final EngineerRepository engineerRepository;
    private final EngineerCategoryRepository engineerCategoryRepository;
    private final PortfolioRepository portfolioRepository;
    private final CategoryRepository categoryRepository;
    private final RegionRepository regionRepository;
    private final UserService userService;

    public EngineerCreateResponse registerEngineer(EngineerCreateRequest request) {
        User currentUser = userService.getCurrentUser();

        if (engineerRepository.existsByUser_UserId(currentUser.getUserId())) {
            throw new CustomException(ErrorCode.ENGINEER_ALREADY_REGISTERED);
        }

        Region region = regionRepository.findById(request.getRegionId())
                .orElseThrow(() -> new CustomException(ErrorCode.REGION_NOT_FOUND));

        List<String> requestedCategoryNames = request.getCategories();
        if (requestedCategoryNames == null || requestedCategoryNames.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_ENGINEER_CATEGORY);
        }

        List<String> normalizedCategoryNames = requestedCategoryNames.stream()
                .map(String::trim)
                .filter(category -> !category.isBlank())
                .distinct()
                .toList();

        if (normalizedCategoryNames.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_ENGINEER_CATEGORY);
        }

        List<Category> categories = categoryRepository.findByCategoryNameIn(normalizedCategoryNames);
        if (categories.size() != normalizedCategoryNames.size()) {
            throw new CustomException(ErrorCode.INVALID_ENGINEER_CATEGORY);
        }

        validateProfileImage(request.getProfileImage());
        validatePortfolioImages(request.getPortfolioImages());

        String profileImage = (request.getProfileImage() == null || request.getProfileImage().isBlank())
                ? defaultEngineerProfileImage
                : request.getProfileImage().trim();

        Engineer engineer = Engineer.create(
                currentUser,
                region,
                profileImage,
                trimToNull(request.getIntroduction()),
                trimToNull(request.getStoreName()),
                trimToNull(request.getStoreAddress()),
                request.getPhone().trim(),
                request.getPhonePublic(),
                request.getEngineerName().trim()
        );
        engineerRepository.save(engineer);

        List<EngineerCategory> mappings = categories.stream()
                .map(category -> EngineerCategory.create(engineer, category))
                .toList();
        engineerCategoryRepository.saveAll(mappings);

        List<Portfolio> portfolios = request.getPortfolioImages().stream()
                .map(String::trim)
                .filter(image -> !image.isBlank())
                .distinct()
                .map(image -> Portfolio.create(engineer, image))
                .toList();

        if (!portfolios.isEmpty()) {
            portfolioRepository.saveAll(portfolios);
        }

        return new EngineerCreateResponse(engineer.getEngineerId());
    }

    private void validateProfileImage(String profileImage) {
        if (profileImage == null || profileImage.isBlank()) {
            return;
        }

        String trimmed = profileImage.trim();
        String profilePrefix = buildFolderPrefix(PROFILE_FOLDER);

        if (!trimmed.startsWith(profilePrefix)) {
            throw new CustomException(ErrorCode.INVALID_PROFILE_IMAGE);
        }
    }

    private void validatePortfolioImages(List<String> portfolioImages) {
        if (portfolioImages == null || portfolioImages.isEmpty()) {
            return;
        }

        String portfolioPrefix = buildFolderPrefix(PORTFOLIO_FOLDER);

        boolean hasInvalidImage = portfolioImages.stream()
                .map(String::trim)
                .anyMatch(image -> !image.startsWith(portfolioPrefix));

        if (hasInvalidImage) {
            throw new CustomException(ErrorCode.INVALID_PORTFOLIO_IMAGE);
        }
    }

    private String buildFolderPrefix(String folder) {
        return publicDomain.endsWith("/")
                ? publicDomain + folder + "/"
                : publicDomain + "/" + folder + "/";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}

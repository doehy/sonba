package com.sonbadrim_server.domain.engineer.service;

import com.sonbadrim_server.domain.category.entity.Category;
import com.sonbadrim_server.domain.category.repository.CategoryRepository;
import com.sonbadrim_server.domain.engineer.dto.request.EngineerRequestDto;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerCreateResponse;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerDetailResponseDto;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerSummaryResponseDto;
import com.sonbadrim_server.domain.engineer.dto.response.InterestedEngineerItemResponseDto;
import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.engineer.entity.EngineerCategory;
import com.sonbadrim_server.domain.engineer.entity.InterestedEngineer;
import com.sonbadrim_server.domain.engineer.repository.EngineerCategoryRepository;
import com.sonbadrim_server.domain.engineer.repository.EngineerRepository;
import com.sonbadrim_server.domain.engineer.repository.InterestedEngineerRepository;
import com.sonbadrim_server.domain.portfolio.entity.Portfolio;
import com.sonbadrim_server.domain.portfolio.repository.PortfolioRepository;
import com.sonbadrim_server.domain.region.entity.Region;
import com.sonbadrim_server.domain.region.repository.RegionRepository;
import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.service.UserService;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EngineerService {

    private final EngineerRepository engineerRepository;
    private final EngineerCategoryRepository engineerCategoryRepository;
    private final PortfolioRepository portfolioRepository;
    private final CategoryRepository categoryRepository;
    private final RegionRepository regionRepository;
    private final UserService userService;
    private final InterestedEngineerRepository interestedEngineerRepository;

    /**
     * 기술자 등록하기
     * @return EngineerCreateResponse
     */
    public EngineerCreateResponse registerEngineer(EngineerRequestDto request) {
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

        Engineer engineer = Engineer.create(
                currentUser,
                region,
                trimToNull(request.getProfileImage()),
                trimToNull(request.getIntroduction()),
                trimToNull(request.getStoreName()),
                trimToNull(request.getStoreAddress()),
                request.getPhone().trim(),
                request.getPhonePublic(),
                request.getEngineerName().trim(),
                request.getHeadline().trim()
        );
        engineerRepository.save(engineer);

        List<EngineerCategory> mappings = categories.stream()
                .map(category -> EngineerCategory.create(engineer, category))
                .toList();
        engineerCategoryRepository.saveAll(mappings);

        List<String> portfolioImages = request.getPortfolioImages() == null
                ? List.of()
                : request.getPortfolioImages();

        List<Portfolio> portfolios = portfolioImages.stream()
                .map(this::trimToNull)
                .filter(image -> image != null)
                .distinct()
                .map(image -> Portfolio.create(engineer, image))
                .toList();

        if (!portfolios.isEmpty()) {
            portfolioRepository.saveAll(portfolios);
        }

        return new EngineerCreateResponse(engineer.getEngineerId());
    }

    /**
     * 기술자 정보 수정
     * @return void
     */
    public void updateEngineer(Long engineerId, EngineerRequestDto request) {
        User currentUser = userService.getCurrentUser();

        Engineer engineer = engineerRepository.findById(engineerId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENGINEER_NOT_FOUND));

        validateOwner(engineer, currentUser);

        Region region = regionRepository.findById(request.getRegionId())
                .orElseThrow(() -> new CustomException(ErrorCode.REGION_NOT_FOUND));

        engineer.updateInfo(
                request.getEngineerName().trim(),
                request.getHeadline().trim(),
                normalizePhone(request.getPhone()),
                request.getPhonePublic(),
                trimToNull(request.getProfileImage()),
                trimToNull(request.getIntroduction()),
                trimToNull(request.getStoreName()),
                trimToNull(request.getStoreAddress()),
                region
        );

        replaceCategories(engineer, request.getCategories());
        replacePortfolioImages(engineer, request.getPortfolioImages());
    }

    /**
     * 기술자들의 목록을 조회한다.
     * @return 기술자 20명 summaryDto
     */
    public Page<EngineerSummaryResponseDto> getEngineerList(Pageable pageable) {
        Page<Engineer> engineerPage = engineerRepository.findAll(pageable);

        List<Long> engineerIds = engineerPage.getContent().stream()
                .map(Engineer::getEngineerId)
                .toList();

        Map<Long, List<String>> categoryMap = getCategoryMap(engineerIds);

        return engineerPage.map(engineer -> EngineerSummaryResponseDto.builder()
                .engineerId(engineer.getEngineerId())
                .engineerName(engineer.getEngineerName())
                .profileImage(engineer.getProfileImage())
                .categories(categoryMap.getOrDefault(engineer.getEngineerId(), Collections.emptyList()))
                .introduction(truncateIntroduction(engineer.getIntroduction()))
                .build()
        );
    }

    /**
     * 현재 사용자의 관심 기술자 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public Page<InterestedEngineerItemResponseDto> getInterestedEngineers(Pageable pageable) {
        User currentUser = userService.getCurrentUser();
        Page<InterestedEngineer> interestedEngineerPage =
                interestedEngineerRepository.findByUser_UserIdOrderByCreatedAtDesc(currentUser.getUserId(), pageable);

        return interestedEngineerPage.map(interestedEngineer ->
                InterestedEngineerItemResponseDto.from(interestedEngineer.getEngineer())
        );
    }

    /**
     * 기술자 상세 조회
     * @return EngineerDetailResponseDto
     */
    public EngineerDetailResponseDto getEngineerDetail(Long engineerId) {
        Engineer engineer = engineerRepository.findDetailByEngineerId(engineerId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENGINEER_NOT_FOUND));
        List<String> portfolioImages = portfolioRepository.findByEngineer_EngineerId(engineerId).stream()
                .map(Portfolio::getPortfolioImage)
                .toList();

        Long currentUserId = getCurrentUserIdOrNull();
        boolean interested = currentUserId != null
                && interestedEngineerRepository.existsByUser_UserIdAndEngineer_EngineerId(currentUserId, engineerId);

        return EngineerDetailResponseDto.from(engineer, portfolioImages, interested);
    }

    /**
     * 기술자 삭제
     * @return void
     */
    public void deleteEngineer(Long engineerId) {
        User currentUser = userService.getCurrentUser();

        Engineer engineer = engineerRepository.findByEngineerIdAndUser_UserId(engineerId, currentUser.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.ENGINEER_NOT_FOUND));

        currentUser.clearEngineer();

        engineerCategoryRepository.deleteByEngineer(engineer);
        portfolioRepository.deleteByEngineer(engineer);
        engineerRepository.delete(engineer);
    }

    public void addInterestedEngineer(Long engineerId) {
        User user = userService.getCurrentUser();
        Engineer engineer = engineerRepository.findById(engineerId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENGINEER_NOT_FOUND));

        if (engineer.getUser().getUserId().equals(user.getUserId())) {
            throw new CustomException(ErrorCode.CANNOT_INTEREST_OWN_ENGINEER);
        }

        boolean exists = interestedEngineerRepository.existsByUser_UserIdAndEngineer_EngineerId(
                user.getUserId(),
                engineerId
        );
        if (exists) {
            return;
        }

        interestedEngineerRepository.save(InterestedEngineer.create(user, engineer));
    }

    public void removeInterestedEngineer(Long engineerId) {
        User user = userService.getCurrentUser();
        if (!engineerRepository.existsById(engineerId)) {
            throw new CustomException(ErrorCode.ENGINEER_NOT_FOUND);
        }

        interestedEngineerRepository.deleteByUser_UserIdAndEngineer_EngineerId(
                user.getUserId(),
                engineerId
        );
    }

    private void validateOwner(Engineer engineer, User currentUser) {
        if (!engineer.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new CustomException(ErrorCode.CANNOT_MODIFY_ENGINEER);
        }
    }

    private void replaceCategories(Engineer engineer, List<String> categoryNames) {
        engineerCategoryRepository.deleteAllByEngineer(engineer);
        engineerCategoryRepository.flush();

        List<EngineerCategory> categories = categoryNames.stream()
                .map(String::trim)
                .map(this::findCategoryByName)
                .map(category -> EngineerCategory.create(engineer, category))
                .toList();

        engineerCategoryRepository.saveAll(categories);
    }

    private void replacePortfolioImages(Engineer engineer, List<String> portfolioImages) {
        portfolioRepository.deleteAllByEngineer(engineer);

        List<Portfolio> images = portfolioImages.stream()
                .map(String::trim)
                .map(imageUrl -> Portfolio.create(engineer, imageUrl))
                .toList();

        portfolioRepository.saveAll(images);
    }

    private Category findCategoryByName(String categoryName) {
        return categoryRepository.findByCategoryName(categoryName)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ENGINEER_CATEGORY));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_PHONE_NUMBER);
        }

        String digits = phone.replaceAll("[^0-9]", "");

        if (!digits.matches("^01[016789]\\d{7,8}$")) {
            throw new CustomException(ErrorCode.INVALID_PHONE_NUMBER);
        }

        return digits;
    }

    private Map<Long, List<String>> getCategoryMap(List<Long> engineerIds) {
        if (engineerIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<EngineerCategory> engineerCategories =
                engineerCategoryRepository.findByEngineer_EngineerIdIn(engineerIds);

        return engineerCategories.stream()
                .collect(Collectors.groupingBy(
                        engineerCategory -> engineerCategory.getEngineer().getEngineerId(),
                        Collectors.mapping(
                                engineerCategory -> engineerCategory.getCategory().getCategoryName(),
                                Collectors.toList()
                        )
                ));
    }

    private String truncateIntroduction(String introduction) {
        if (introduction == null || introduction.isBlank()) {
            return "";
        }

        String trimmed = introduction.trim();
        if (trimmed.length() <= 100) {
            return trimmed;
        }

        return trimmed.substring(0, 100) + "...";
    }

    private Long getCurrentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

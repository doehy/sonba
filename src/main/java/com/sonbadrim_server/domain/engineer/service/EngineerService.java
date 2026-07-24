package com.sonbadrim_server.domain.engineer.service;

import com.sonbadrim_server.domain.category.entity.Category;
import com.sonbadrim_server.domain.category.repository.CategoryRepository;
import com.sonbadrim_server.domain.engineer.dto.request.EngineerRequestDto;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerCreateResponse;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerDetailResponseDto;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerSummaryResponseDto;
import com.sonbadrim_server.domain.engineer.dto.response.InterestedEngineerItemResponseDto;
import com.sonbadrim_server.domain.engineer.dto.response.PhoneDuplicateCheckResponseDto;
import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.engineer.entity.EngineerCategory;
import com.sonbadrim_server.domain.engineer.entity.InterestedEngineer;
import com.sonbadrim_server.domain.engineer.repository.EngineerCategoryRepository;
import com.sonbadrim_server.domain.engineer.repository.EngineerRepository;
import com.sonbadrim_server.domain.engineer.repository.InterestedEngineerRepository;
import com.sonbadrim_server.domain.image.event.ImageUploadedEvent;
import com.sonbadrim_server.domain.image.entity.ImageUsageType;
import com.sonbadrim_server.domain.image.service.ImagePolicyValidator;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final ImagePolicyValidator imagePolicyValidator;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 기술자 등록하기
     * @return EngineerCreateResponse
     */
    public EngineerCreateResponse registerEngineer(EngineerRequestDto request) {
        User currentUser = userService.getCurrentUser();

        if (engineerRepository.existsByUser_UserId(currentUser.getUserId())) {
            throw new CustomException(ErrorCode.ENGINEER_ALREADY_REGISTERED);
        }

        String normalizedPhone = normalizePhone(request.getPhone());
        if (engineerRepository.existsByPhone(normalizedPhone)) {
            throw new CustomException(ErrorCode.ENGINEER_PHONE_ALREADY_REGISTERED);
        }

        Region region = regionRepository.findById(request.getRegionId())
                .orElseThrow(() -> new CustomException(ErrorCode.REGION_NOT_FOUND));

        List<Category> categories = validateAndGetCategories(request.getCategories());
        List<String> normalizedPortfolioImages =
                validateAndNormalizePortfolioImages(request.getPortfolioImages());
        String normalizedProfileImage = imagePolicyValidator.validateProfileImageUrl(request.getProfileImage());

        Engineer engineer = Engineer.create(
                currentUser,
                region,
                normalizedProfileImage,
                trimToNull(request.getIntroduction()),
                trimToNull(request.getStoreName()),
                trimToNull(request.getStoreAddress()),
                normalizedPhone,
                request.getEngineerName().trim(),
                trimToNull(request.getHeadline())
        );
        engineerRepository.save(engineer);

        List<EngineerCategory> mappings = categories.stream()
                .map(category -> EngineerCategory.create(engineer, category))
                .toList();
        engineerCategoryRepository.saveAll(mappings);

        List<Portfolio> portfolios = normalizedPortfolioImages.stream()
                .map(image -> Portfolio.create(engineer, image))
                .toList();

        List<Portfolio> savedPortfolios = portfolioRepository.saveAll(portfolios);
        publishProfileImageUploadedEvent(engineer.getEngineerId(), normalizedProfileImage);
        publishPortfolioImageUploadedEvents(savedPortfolios);

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

        String normalizedPhone = normalizePhone(request.getPhone());

        if (engineerRepository.existsByPhoneAndEngineerIdNot(
                normalizedPhone,
                engineer.getEngineerId()
        )) {
            throw new CustomException(ErrorCode.ENGINEER_PHONE_ALREADY_REGISTERED);
        }

        List<Category> categories = validateAndGetCategories(request.getCategories());
        List<String> normalizedPortfolioImages =
                validateAndNormalizePortfolioImages(request.getPortfolioImages());
        String normalizedProfileImage = imagePolicyValidator.validateProfileImageUrl(request.getProfileImage());
        boolean profileImageChanged = !Objects.equals(engineer.getProfileImage(), normalizedProfileImage);

        engineer.updateInfo(
                request.getEngineerName().trim(),
                trimToNull(request.getHeadline()),
                normalizedPhone,
                normalizedProfileImage,
                trimToNull(request.getIntroduction()),
                trimToNull(request.getStoreName()),
                trimToNull(request.getStoreAddress()),
                region
        );

        replaceCategories(engineer, categories);
        replacePortfolioImages(engineer, normalizedPortfolioImages);
        if (profileImageChanged) {
            publishProfileImageUploadedEvent(engineer.getEngineerId(), normalizedProfileImage);
        }
    }

    /**
     * 기술자들의 목록을 조회한다.
     * @return 기술자 20명 summaryDto
     */
    public Slice<EngineerSummaryResponseDto> getEngineerList(Pageable pageable) {
        Slice<Engineer> engineerSlice = engineerRepository.findEngineerList(pageable);

        List<Long> engineerIds = engineerSlice.getContent().stream()
                .map(Engineer::getEngineerId)
                .toList();

        Map<Long, List<String>> categoryMap = getCategoryMap(engineerIds);

        return engineerSlice.map(engineer -> EngineerSummaryResponseDto.builder()
                .engineerId(engineer.getEngineerId())
                .engineerName(engineer.getEngineerName())
                .profileImage(engineer.getDisplayProfileImage())
                .categories(categoryMap.getOrDefault(engineer.getEngineerId(), Collections.emptyList()))
                .introduction(truncateIntroduction(engineer.getIntroduction()))
                .build()
        );
    }

    /**
     * 기술자 상세 조회
     * @return EngineerDetailResponseDto
     */
    public EngineerDetailResponseDto getEngineerDetail(Long engineerId) {
        Engineer engineer = engineerRepository.findDetailByEngineerId(engineerId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENGINEER_NOT_FOUND));
        List<Portfolio> portfolios = portfolioRepository.findByEngineer_EngineerId(engineerId);
        List<String> portfolioImages = portfolios.stream()
                .map(Portfolio::getPortfolioImage)
                .toList();
        List<String> portfolioThumbnailImages = portfolios.stream()
                .map(Portfolio::getDisplayPortfolioImage)
                .toList();

        Long currentUserId = getCurrentUserIdOrNull();
        boolean interested = currentUserId != null
                && interestedEngineerRepository.existsByUser_UserIdAndEngineer_EngineerId(currentUserId, engineerId);

        return EngineerDetailResponseDto.from(engineer, portfolioImages, portfolioThumbnailImages, interested);
    }

    /**
     * 기술자 삭제
     * @return void
     */
    public void deleteEngineer(Long engineerId) {
        User currentUser = userService.getCurrentUser();

        Engineer engineer = engineerRepository.findById(engineerId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENGINEER_NOT_FOUND));

        if (!engineer.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new CustomException(ErrorCode.CANNOT_UNJOIN_ENGINEER);
        }

        interestedEngineerRepository.deleteAllByEngineer(engineer);
        engineerCategoryRepository.deleteAllByEngineer(engineer);
        portfolioRepository.deleteAllByEngineer(engineer);

        currentUser.clearEngineer();
        engineerRepository.delete(engineer);
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

        try {
            interestedEngineerRepository.saveAndFlush(InterestedEngineer.create(user, engineer));
        } catch (DataIntegrityViolationException e) {
            boolean alreadyInterested = interestedEngineerRepository.existsByUser_UserIdAndEngineer_EngineerId(
                    user.getUserId(),
                    engineerId
            );
            if (alreadyInterested) {
                return;
            }
            throw e;
        }
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

    /**
     * 기술자 전화번호 중복 여부를 확인한다.
     */
    @Transactional(readOnly = true)
    public PhoneDuplicateCheckResponseDto checkPhoneDuplicate(String phone, Long engineerId) {
        String normalizedPhone = normalizePhone(phone);

        if (engineerId == null) {
            return new PhoneDuplicateCheckResponseDto(
                    engineerRepository.existsByPhone(normalizedPhone)
            );
        }

        Engineer engineer = engineerRepository.findById(engineerId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENGINEER_NOT_FOUND));
        User currentUser = userService.getCurrentUser();
        validateOwner(engineer, currentUser);

        boolean duplicated = engineerRepository.existsByPhoneAndEngineerIdNot(
                normalizedPhone,
                engineerId
        );

        return new PhoneDuplicateCheckResponseDto(duplicated);
    }

    private void validateOwner(Engineer engineer, User currentUser) {
        if (!engineer.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new CustomException(ErrorCode.CANNOT_MODIFY_ENGINEER);
        }
    }

    private List<Category> validateAndGetCategories(List<String> categoryNames) {
        if (categoryNames == null || categoryNames.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_ENGINEER_CATEGORY);
        }

        List<String> normalizedCategoryNames = categoryNames.stream()
                .map(this::trimToNull)
                .filter(category -> category != null)
                .distinct()
                .toList();

        if (normalizedCategoryNames.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_ENGINEER_CATEGORY);
        }

        List<Category> categories =
                categoryRepository.findByCategoryNameIn(normalizedCategoryNames);
        if (categories.size() != normalizedCategoryNames.size()) {
            throw new CustomException(ErrorCode.INVALID_ENGINEER_CATEGORY);
        }

        return categories;
    }

    private List<String> validateAndNormalizePortfolioImages(List<String> portfolioImages) {
        if (portfolioImages == null || portfolioImages.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_PORTFOLIO_IMAGE);
        }

        List<String> normalizedPortfolioImages = portfolioImages.stream()
                .map(this::trimToNull)
                .filter(image -> image != null)
                .map(imagePolicyValidator::validatePortfolioImageUrl)
                .distinct()
                .toList();

        if (normalizedPortfolioImages.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_PORTFOLIO_IMAGE);
        }

        return normalizedPortfolioImages;
    }

    private void replaceCategories(Engineer engineer, List<Category> categories) {
        engineerCategoryRepository.deleteAllByEngineer(engineer);
        engineerCategoryRepository.flush();

        List<EngineerCategory> mappings = categories.stream()
                .map(category -> EngineerCategory.create(engineer, category))
                .toList();

        engineerCategoryRepository.saveAll(mappings);
    }

    private void replacePortfolioImages(Engineer engineer, List<String> portfolioImages) {
        portfolioRepository.deleteAllByEngineer(engineer);

        List<Portfolio> images = portfolioImages.stream()
                .map(imageUrl -> Portfolio.create(engineer, imageUrl))
                .toList();

        List<Portfolio> savedPortfolios = portfolioRepository.saveAll(images);
        publishPortfolioImageUploadedEvents(savedPortfolios);
    }

    private void publishProfileImageUploadedEvent(Long engineerId, String profileImageUrl) {
        if (profileImageUrl != null && !profileImageUrl.isBlank()) {
            eventPublisher.publishEvent(new ImageUploadedEvent(
                    ImageUsageType.ENGINEER_PROFILE,
                    engineerId,
                    profileImageUrl
            ));
        }
    }

    private void publishPortfolioImageUploadedEvents(List<Portfolio> portfolios) {
        portfolios.stream()
                .filter(portfolio -> portfolio.getPortfolioId() != null)
                .filter(portfolio -> portfolio.getPortfolioImage() != null && !portfolio.getPortfolioImage().isBlank())
                .forEach(portfolio -> eventPublisher.publishEvent(
                        new ImageUploadedEvent(
                                ImageUsageType.ENGINEER_PORTFOLIO,
                                portfolio.getPortfolioId(),
                                portfolio.getPortfolioImage()
                        )
                ));
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

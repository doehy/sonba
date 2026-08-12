package com.sonbadrim_server.domain.engineer.service;

import com.sonbadrim_server.domain.category.entity.Category;
import com.sonbadrim_server.domain.category.repository.CategoryRepository;
import com.sonbadrim_server.domain.engineer.dto.request.EngineerRequestDto;
import com.sonbadrim_server.domain.engineer.dto.request.PortfolioItemRequestDto;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerCreateResponse;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerClaimRequestResponseDto;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerDetailResponseDto;
import com.sonbadrim_server.domain.engineer.dto.response.EngineerSummaryResponseDto;
import com.sonbadrim_server.domain.engineer.dto.response.InterestedEngineerItemResponseDto;
import com.sonbadrim_server.domain.engineer.dto.response.PhoneDuplicateCheckResponseDto;
import com.sonbadrim_server.domain.engineer.dto.response.PortfolioItemResponseDto;
import com.sonbadrim_server.domain.engineer.entity.Engineer;
import com.sonbadrim_server.domain.engineer.entity.EngineerCategory;
import com.sonbadrim_server.domain.engineer.entity.EngineerClaimStatus;
import com.sonbadrim_server.domain.engineer.entity.InterestedEngineer;
import com.sonbadrim_server.domain.engineer.repository.EngineerCategoryRepository;
import com.sonbadrim_server.domain.engineer.repository.EngineerRepository;
import com.sonbadrim_server.domain.engineer.repository.InterestedEngineerRepository;
import com.sonbadrim_server.domain.image.event.ImageUploadedEvent;
import com.sonbadrim_server.domain.image.entity.ImageUsageType;
import com.sonbadrim_server.domain.image.service.ImagePolicyValidator;
import com.sonbadrim_server.domain.portfolio.entity.Portfolio;
import com.sonbadrim_server.domain.portfolio.entity.PortfolioImage;
import com.sonbadrim_server.domain.portfolio.entity.PortfolioType;
import com.sonbadrim_server.domain.portfolio.repository.PortfolioRepository;
import com.sonbadrim_server.domain.region.entity.Region;
import com.sonbadrim_server.domain.region.repository.RegionRepository;
import com.sonbadrim_server.domain.user.entity.User;
import com.sonbadrim_server.domain.user.entity.UserRole;
import com.sonbadrim_server.domain.user.service.UserService;
import com.sonbadrim_server.global.config.CacheConfig;
import com.sonbadrim_server.global.exception.CustomException;
import com.sonbadrim_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EngineerService {

    private static final String ADMIN_CREATED_DEFAULT_INTRODUCTION = "상세 소개는 추후 보완될 예정입니다.";
    private static final int MAX_GALLERY_IMAGE_COUNT = 8;

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
    @CacheEvict(cacheNames = CacheConfig.SEARCH_ENGINEERS_CACHE, allEntries = true)
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
        List<NormalizedPortfolioItem> normalizedPortfolioItems =
                validateNewPortfolioItems(request.getPortfolioItems());
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

        return saveEngineerWithRelations(engineer, categories, normalizedPortfolioItems, normalizedProfileImage);
    }

    /**
     * 관리자가 수동으로 기술자 프로필을 등록한다.
     * @return EngineerCreateResponse
     */
    @CacheEvict(cacheNames = CacheConfig.SEARCH_ENGINEERS_CACHE, allEntries = true)
    public EngineerCreateResponse registerAdminCreatedEngineer(EngineerRequestDto request) {
        validateAdmin(userService.getCurrentUser());

        String normalizedPhone = normalizePhone(request.getPhone());
        if (engineerRepository.existsByPhone(normalizedPhone)) {
            throw new CustomException(ErrorCode.ENGINEER_PHONE_ALREADY_REGISTERED);
        }

        Region region = regionRepository.findById(request.getRegionId())
                .orElseThrow(() -> new CustomException(ErrorCode.REGION_NOT_FOUND));

        List<Category> categories = validateAndGetCategories(request.getCategories());
        List<NormalizedPortfolioItem> normalizedPortfolioItems =
                validateNewPortfolioItems(request.getPortfolioItems());
        String normalizedProfileImage = imagePolicyValidator.validateProfileImageUrl(request.getProfileImage());

        Engineer engineer = Engineer.createAdminCreated(
                region,
                normalizedProfileImage,
                ADMIN_CREATED_DEFAULT_INTRODUCTION,
                trimToNull(request.getStoreName()),
                trimToNull(request.getStoreAddress()),
                normalizedPhone,
                request.getEngineerName().trim(),
                trimToNull(request.getHeadline())
        );

        return saveEngineerWithRelations(engineer, categories, normalizedPortfolioItems, normalizedProfileImage);
    }

    /**
     * 기술자와 시공 분야, 포트폴리오를 함께 저장하고 이미지 썸네일 생성 이벤트를 발행한다.
     */
    private EngineerCreateResponse saveEngineerWithRelations(
            Engineer engineer,
            List<Category> categories,
            List<NormalizedPortfolioItem> normalizedPortfolioItems,
            String normalizedProfileImage
    ) {
        engineerRepository.save(engineer);

        List<EngineerCategory> mappings = categories.stream()
                .map(category -> EngineerCategory.create(engineer, category))
                .toList();
        engineerCategoryRepository.saveAll(mappings);

        List<Portfolio> portfolios = createPortfolios(engineer, normalizedPortfolioItems);

        List<Portfolio> savedPortfolios = portfolioRepository.saveAll(portfolios);
        portfolioRepository.flush();
        publishProfileImageUploadedEvent(engineer.getEngineerId(), normalizedProfileImage);
        publishPortfolioImageUploadedEvents(savedPortfolios.stream()
                .flatMap(portfolio -> portfolio.getImages().stream())
                .toList());

        return new EngineerCreateResponse(engineer.getEngineerId());
    }

    /**
     * 기술자 정보 수정
     * @return void
     */
    @CacheEvict(cacheNames = CacheConfig.SEARCH_ENGINEERS_CACHE, allEntries = true)
    public void updateEngineer(Long engineerId, EngineerRequestDto request) {
        User currentUser = userService.getCurrentUser();

        Engineer engineer = engineerRepository.findById(engineerId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENGINEER_NOT_FOUND));

        validateOwner(engineer, currentUser);

        updateEngineerInfo(engineer, request);
    }

    /**
     * 관리자가 기술자 정보를 수정한다.
     * @return void
     */
    @CacheEvict(cacheNames = CacheConfig.SEARCH_ENGINEERS_CACHE, allEntries = true)
    public void updateEngineerByAdmin(Long engineerId, EngineerRequestDto request) {
        validateAdmin(userService.getCurrentUser());

        Engineer engineer = engineerRepository.findById(engineerId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENGINEER_NOT_FOUND));

        updateEngineerInfo(engineer, request);
    }

    /**
     * 기술자 기본 정보와 시공 분야, 포트폴리오 변경 사항을 하나의 트랜잭션에서 반영한다.
     */
    private void updateEngineerInfo(Engineer engineer, EngineerRequestDto request) {
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
        List<NormalizedPortfolioItem> normalizedPortfolioItems =
                validateAndNormalizePortfolioItems(request.getPortfolioItems());
        String requestedProfileImage = trimToNull(request.getProfileImage());
        boolean profileImageChanged = !Objects.equals(engineer.getProfileImage(), requestedProfileImage);
        String normalizedProfileImage = profileImageChanged
                ? imagePolicyValidator.validateProfileImageUrl(requestedProfileImage)
                : engineer.getProfileImage();

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
        synchronizePortfolioItems(engineer, normalizedPortfolioItems);
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
        List<PortfolioItemResponseDto> portfolioItems = portfolios.stream()
                .map(PortfolioItemResponseDto::from)
                .toList();

        Long currentUserId = getCurrentUserIdOrNull();
        boolean interested = currentUserId != null
                && interestedEngineerRepository.existsByUser_UserIdAndEngineer_EngineerId(currentUserId, engineerId);

        return EngineerDetailResponseDto.from(engineer, portfolioItems, interested);
    }

    /**
     * 기술자 삭제
     * @return void
     */
    @CacheEvict(cacheNames = CacheConfig.SEARCH_ENGINEERS_CACHE, allEntries = true)
    public void deleteEngineer(Long engineerId) {
        User currentUser = userService.getCurrentUser();

        Engineer engineer = engineerRepository.findById(engineerId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENGINEER_NOT_FOUND));

        if (!isOwnedBy(engineer, currentUser)) {
            throw new CustomException(ErrorCode.CANNOT_UNJOIN_ENGINEER);
        }

        deleteEngineerWithRelations(engineer);
    }

    /**
     * 관리자가 기술자 정보를 삭제한다.
     * @return void
     */
    @CacheEvict(cacheNames = CacheConfig.SEARCH_ENGINEERS_CACHE, allEntries = true)
    public void deleteEngineerByAdmin(Long engineerId) {
        validateAdmin(userService.getCurrentUser());

        Engineer engineer = engineerRepository.findById(engineerId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENGINEER_NOT_FOUND));

        deleteEngineerWithRelations(engineer);
    }

    /**
     * 기술자와 연결된 관심 목록, 시공 분야, 포트폴리오를 정리한 뒤 기술자를 삭제한다.
     */
    private void deleteEngineerWithRelations(Engineer engineer) {
        interestedEngineerRepository.deleteAllByEngineer(engineer);
        engineerCategoryRepository.deleteAllByEngineer(engineer);
        portfolioRepository.deleteAllByEngineer(engineer);

        if (engineer.getUser() != null) {
            engineer.getUser().clearEngineer();
        }
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

    /**
     * 관심 기술자를 등록한다.
     */
    public void addInterestedEngineer(Long engineerId) {
        User user = userService.getCurrentUser();
        Engineer engineer = engineerRepository.findById(engineerId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENGINEER_NOT_FOUND));

        if (isOwnedBy(engineer, user)) {
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

    /**
     * 관심 기술자를 삭제한다.
     */
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
     * 기존 기술자에 계정 연결 요청을 한다.
     */
    public void requestEngineerClaim(Long engineerId) {
        User currentUser = userService.getCurrentUser();

        if (engineerRepository.existsByUser_UserId(currentUser.getUserId())) {
            throw new CustomException(ErrorCode.ENGINEER_ALREADY_REGISTERED);
        }

        Engineer engineer = engineerRepository.findById(engineerId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENGINEER_NOT_FOUND));

        if (engineer.getUser() != null || engineer.getClaimStatus() == EngineerClaimStatus.APPROVED) {
            throw new CustomException(ErrorCode.ENGINEER_ALREADY_CLAIMED);
        }

        if (engineer.getClaimStatus() == EngineerClaimStatus.REQUESTED) {
            if (isClaimRequestedBy(engineer, currentUser)) {
                return;
            }
            throw new CustomException(ErrorCode.ENGINEER_CLAIM_ALREADY_REQUESTED);
        }

        engineer.requestClaim(currentUser);
    }

    /**
     * 기술자 연결 요청 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public Page<EngineerClaimRequestResponseDto> getEngineerClaimRequests(Pageable pageable) {
        validateAdmin(userService.getCurrentUser());

        return engineerRepository
                .findByClaimStatusOrderByModifiedAtDesc(EngineerClaimStatus.REQUESTED, pageable)
                .map(EngineerClaimRequestResponseDto::from);
    }

    /**
     * 기술자 연결 요청을 승인해준다.
     */
    public void approveEngineerClaim(Long engineerId) {
        validateAdmin(userService.getCurrentUser());

        Engineer engineer = engineerRepository.findById(engineerId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENGINEER_NOT_FOUND));

        validateClaimRequestPending(engineer);

        User requester = engineer.getClaimRequester();
        if (engineerRepository.existsByUser_UserId(requester.getUserId())) {
            throw new CustomException(ErrorCode.ENGINEER_ALREADY_REGISTERED);
        }

        engineer.approveClaim(requester);
    }

    /**
     * 기술자 연결 요청을 거절한다.
     */
    public void rejectEngineerClaim(Long engineerId) {
        validateAdmin(userService.getCurrentUser());

        Engineer engineer = engineerRepository.findById(engineerId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENGINEER_NOT_FOUND));

        validateClaimRequestPending(engineer);

        engineer.rejectClaim();
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
        if (currentUser.getRole() != UserRole.ADMIN) {
            validateOwner(engineer, currentUser);
        }

        boolean duplicated = engineerRepository.existsByPhoneAndEngineerIdNot(
                normalizedPhone,
                engineerId
        );

        return new PhoneDuplicateCheckResponseDto(duplicated);
    }

    /**
     * 현재 사용자가 해당 기술자 프로필의 소유자인지 검증한다.
     */
    private void validateOwner(Engineer engineer, User currentUser) {
        if (!isOwnedBy(engineer, currentUser)) {
            throw new CustomException(ErrorCode.CANNOT_MODIFY_ENGINEER);
        }
    }

    /**
     * 기술자 프로필과 사용자가 소유 관계로 연결되어 있는지 확인한다.
     */
    private boolean isOwnedBy(Engineer engineer, User user) {
        return engineer.getUser() != null
                && user != null
                && engineer.getUser().getUserId().equals(user.getUserId());
    }

    /**
     * 해당 사용자가 기술자 프로필 연결을 요청한 당사자인지 확인한다.
     */
    private boolean isClaimRequestedBy(Engineer engineer, User user) {
        return engineer.getClaimRequester() != null
                && user != null
                && engineer.getClaimRequester().getUserId().equals(user.getUserId());
    }

    /**
     * 기술자 프로필이 관리자 승인 또는 거절을 기다리는 연결 요청 상태인지 검증한다.
     */
    private void validateClaimRequestPending(Engineer engineer) {
        if (engineer.getUser() != null || engineer.getClaimStatus() == EngineerClaimStatus.APPROVED) {
            throw new CustomException(ErrorCode.ENGINEER_ALREADY_CLAIMED);
        }

        if (engineer.getClaimStatus() != EngineerClaimStatus.REQUESTED || engineer.getClaimRequester() == null) {
            throw new CustomException(ErrorCode.ENGINEER_CLAIM_NOT_REQUESTED);
        }
    }

    /**
     * 현재 사용자가 관리자 권한을 가지고 있는지 검증한다.
     */
    private void validateAdmin(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 요청받은 시공 분야명을 정리하고 실제로 존재하는 카테고리 엔티티로 변환한다.
     */
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

    /**
     * 포트폴리오 요청 목록의 필수값과 타입별 이미지 개수를 검증하고 값을 정규화한다.
     */
    private List<NormalizedPortfolioItem> validateAndNormalizePortfolioItems(List<PortfolioItemRequestDto> portfolioItems) {
        if (portfolioItems == null || portfolioItems.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_PORTFOLIO_IMAGE);
        }

        List<NormalizedPortfolioItem> normalizedPortfolioItems = portfolioItems.stream()
                .map(this::validateAndNormalizePortfolioItem)
                .toList();

        if (normalizedPortfolioItems.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_PORTFOLIO_IMAGE);
        }

        return normalizedPortfolioItems;
    }

    /**
     * 신규 등록용 포트폴리오인지 확인하고 모든 신규 이미지의 실제 파일 내용을 검증한다.
     */
    private List<NormalizedPortfolioItem> validateNewPortfolioItems(List<PortfolioItemRequestDto> portfolioItems) {
        List<NormalizedPortfolioItem> normalizedPortfolioItems =
                validateAndNormalizePortfolioItems(portfolioItems);

        return normalizedPortfolioItems.stream()
                .map(portfolioItem -> {
                    if (portfolioItem.portfolioId() != null) {
                        throw new CustomException(ErrorCode.INVALID_PORTFOLIO_IMAGE);
                    }
                    return new NormalizedPortfolioItem(
                            null,
                            portfolioItem.type(),
                            portfolioItem.title(),
                            validateNewPortfolioImageUrls(portfolioItem.images())
                    );
                })
                .toList();
    }

    /**
     * 포트폴리오 한 건의 제목과 이미지 URL을 정리하고 타입에 맞는 이미지 개수를 검증한다.
     */
    private NormalizedPortfolioItem validateAndNormalizePortfolioItem(PortfolioItemRequestDto portfolioItem) {
        if (portfolioItem == null || portfolioItem.getType() == null) {
            throw new CustomException(ErrorCode.INVALID_PORTFOLIO_IMAGE);
        }

        List<String> normalizedImages = normalizePortfolioImageUrls(portfolioItem.getImages());
        validatePortfolioImageCount(portfolioItem.getType(), normalizedImages.size());

        return new NormalizedPortfolioItem(
                portfolioItem.getPortfolioId(),
                portfolioItem.getType(),
                trimToNull(portfolioItem.getTitle()),
                normalizedImages
        );
    }

    /**
     * 이미지 URL의 공백과 중복을 제거하고 유효한 URL 목록으로 정리한다.
     */
    private List<String> normalizePortfolioImageUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_PORTFOLIO_IMAGE);
        }

        List<String> normalizedImageUrls = imageUrls.stream()
                .map(this::trimToNull)
                .filter(image -> image != null)
                .distinct()
                .toList();

        if (normalizedImageUrls.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_PORTFOLIO_IMAGE);
        }

        return normalizedImageUrls;
    }

    /**
     * 새로 추가되는 포트폴리오 이미지가 R2에 존재하는 실제 이미지 파일인지 검증한다.
     */
    private List<String> validateNewPortfolioImageUrls(List<String> imageUrls) {
        return imageUrls.stream()
                .map(imagePolicyValidator::validatePortfolioImageUrl)
                .toList();
    }

    /**
     * 포트폴리오 타입별로 허용되는 이미지 개수인지 검증한다.
     */
    private void validatePortfolioImageCount(PortfolioType type, int imageCount) {
        switch (type) {
            case SINGLE -> {
                if (imageCount != 1) {
                    throw new CustomException(ErrorCode.INVALID_PORTFOLIO_IMAGE);
                }
            }
            case BEFORE_AFTER -> {
                if (imageCount != 2) {
                    throw new CustomException(ErrorCode.INVALID_PORTFOLIO_IMAGE);
                }
            }
            case GALLERY -> {
                if (imageCount < 2 || imageCount > MAX_GALLERY_IMAGE_COUNT) {
                    throw new CustomException(ErrorCode.INVALID_PORTFOLIO_IMAGE);
                }
            }
        }
    }

    /**
     * 기술자의 기존 시공 분야 연결을 요청받은 시공 분야 목록으로 교체한다.
     */
    private void replaceCategories(Engineer engineer, List<Category> categories) {
        engineerCategoryRepository.deleteAllByEngineer(engineer);
        engineerCategoryRepository.flush();

        List<EngineerCategory> mappings = categories.stream()
                .map(category -> EngineerCategory.create(engineer, category))
                .toList();

        engineerCategoryRepository.saveAll(mappings);
    }

    /**
     * 포트폴리오 ID를 기준으로 기존 항목은 수정하고 새 항목은 추가하며 빠진 항목은 삭제한다.
     */
    private void synchronizePortfolioItems(Engineer engineer, List<NormalizedPortfolioItem> portfolioItems) {
        List<Portfolio> existingPortfolios =
                portfolioRepository.findByEngineer_EngineerId(engineer.getEngineerId());
        Map<Long, Portfolio> existingPortfolioMap = existingPortfolios.stream()
                .collect(Collectors.toMap(Portfolio::getPortfolioId, portfolio -> portfolio));
        Set<Long> requestedPortfolioIds = new HashSet<>();
        List<Portfolio> portfoliosToCreate = new ArrayList<>();
        List<PortfolioImage> newPortfolioImages = new ArrayList<>();

        for (int portfolioIndex = 0; portfolioIndex < portfolioItems.size(); portfolioIndex++) {
            NormalizedPortfolioItem portfolioItem = portfolioItems.get(portfolioIndex);
            if (portfolioItem.portfolioId() == null) {
                List<String> validatedImageUrls = validateNewPortfolioImageUrls(portfolioItem.images());
                Portfolio portfolio = Portfolio.create(
                        engineer,
                        portfolioItem.type(),
                        portfolioItem.title(),
                        portfolioIndex,
                        validatedImageUrls
                );
                portfoliosToCreate.add(portfolio);
                newPortfolioImages.addAll(portfolio.getImages());
                continue;
            }

            if (!requestedPortfolioIds.add(portfolioItem.portfolioId())) {
                throw new CustomException(ErrorCode.INVALID_PORTFOLIO_IMAGE);
            }

            Portfolio portfolio = existingPortfolioMap.get(portfolioItem.portfolioId());
            if (portfolio == null) {
                throw new CustomException(ErrorCode.INVALID_PORTFOLIO_IMAGE);
            }

            portfolio.update(portfolioItem.type(), portfolioItem.title(), portfolioIndex);
            synchronizePortfolioImages(portfolio, portfolioItem.images(), newPortfolioImages);
        }

        List<Portfolio> portfoliosToDelete = existingPortfolios.stream()
                .filter(portfolio -> !requestedPortfolioIds.contains(portfolio.getPortfolioId()))
                .toList();
        if (!portfoliosToDelete.isEmpty()) {
            portfolioRepository.deleteAll(portfoliosToDelete);
        }
        if (!portfoliosToCreate.isEmpty()) {
            portfolioRepository.saveAll(portfoliosToCreate);
        }

        portfolioRepository.flush();
        publishPortfolioImageUploadedEvents(newPortfolioImages);
    }

    /**
     * 기존 포트폴리오의 이미지 URL을 기준으로 유지, 순서 변경, 추가, 삭제를 차등 반영한다.
     */
    private void synchronizePortfolioImages(
            Portfolio portfolio,
            List<String> requestedImageUrls,
            List<PortfolioImage> newPortfolioImages
    ) {
        Set<String> requestedImageUrlSet = new HashSet<>(requestedImageUrls);
        portfolio.removeImagesNotIn(requestedImageUrlSet);

        Map<String, PortfolioImage> existingImageMap = portfolio.getImages().stream()
                .collect(Collectors.toMap(PortfolioImage::getImageUrl, portfolioImage -> portfolioImage));

        for (int imageIndex = 0; imageIndex < requestedImageUrls.size(); imageIndex++) {
            String requestedImageUrl = requestedImageUrls.get(imageIndex);
            PortfolioImage existingImage = existingImageMap.get(requestedImageUrl);
            if (existingImage != null) {
                existingImage.updateSortOrder(imageIndex);
                continue;
            }

            String validatedImageUrl = imagePolicyValidator.validatePortfolioImageUrl(requestedImageUrl);
            newPortfolioImages.add(portfolio.addImage(validatedImageUrl, imageIndex));
        }
    }

    /**
     * 정규화된 포트폴리오 요청 목록을 저장 가능한 포트폴리오 엔티티 목록으로 변환한다.
     */
    private List<Portfolio> createPortfolios(Engineer engineer, List<NormalizedPortfolioItem> portfolioItems) {
        return IntStream.range(0, portfolioItems.size())
                .mapToObj(index -> {
                    NormalizedPortfolioItem portfolioItem = portfolioItems.get(index);
                    return Portfolio.create(
                            engineer,
                            portfolioItem.type(),
                            portfolioItem.title(),
                            index,
                            portfolioItem.images()
                    );
                })
                .toList();
    }

    /**
     * 프로필 이미지가 존재하면 비동기 썸네일 생성을 위한 업로드 이벤트를 발행한다.
     */
    private void publishProfileImageUploadedEvent(Long engineerId, String profileImageUrl) {
        if (profileImageUrl != null && !profileImageUrl.isBlank()) {
            eventPublisher.publishEvent(new ImageUploadedEvent(
                    ImageUsageType.ENGINEER_PROFILE,
                    engineerId,
                    profileImageUrl
            ));
        }
    }

    /**
     * 새로 저장된 포트폴리오 이미지마다 비동기 썸네일 생성 이벤트를 발행한다.
     */
    private void publishPortfolioImageUploadedEvents(List<PortfolioImage> portfolioImages) {
        portfolioImages.stream()
                .filter(portfolioImage -> portfolioImage.getPortfolioImageId() != null)
                .filter(portfolioImage -> portfolioImage.getImageUrl() != null && !portfolioImage.getImageUrl().isBlank())
                .forEach(portfolioImage -> eventPublisher.publishEvent(
                        new ImageUploadedEvent(
                                ImageUsageType.ENGINEER_PORTFOLIO,
                                portfolioImage.getPortfolioImageId(),
                                portfolioImage.getImageUrl()
                        )
                ));
    }

    /**
     * 문자열 앞뒤 공백을 제거하고 내용이 없으면 null로 변환한다.
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 전화번호에서 숫자만 추출하고 국내 휴대전화 번호 형식인지 검증한다.
     */
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

    /**
     * 기술자 ID별 시공 분야명 목록을 한 번에 조회해 Map으로 구성한다.
     */
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

    /**
     * 목록 화면용 자기소개를 최대 100자로 잘라 반환한다.
     */
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

    /**
     * 현재 인증 정보에서 사용자 ID를 꺼내며 비로그인 또는 잘못된 인증 정보이면 null을 반환한다.
     */
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

    private record NormalizedPortfolioItem(
            Long portfolioId,
            PortfolioType type,
            String title,
            List<String> images
    ) {
    }

}

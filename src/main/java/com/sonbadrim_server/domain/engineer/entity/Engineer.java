package com.sonbadrim_server.domain.engineer.entity;

import com.sonbadrim_server.domain.portfolio.entity.Portfolio;
import com.sonbadrim_server.domain.region.entity.Region;
import com.sonbadrim_server.domain.user.entity.User;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.sonbadrim_server.global.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "engineer",
        indexes = {
                @Index(
                        name = "idx_engineer_created_at_engineer_id",
                        columnList = "created_at DESC, engineer_id DESC"
                )
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Engineer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "engineer_id")
    private Long engineerId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Column(name = "profileImage", length = 512)
    private String profileImage;

    @Column(name = "profile_thumbnail_image", length = 512)
    private String profileThumbnailImage;

    @Column(name = "introduction", columnDefinition = "TEXT", length = 300)
    private String introduction;

    @Column(name = "store_name", length = 20)
    private String storeName;

    @Column(name = "store_address", length = 255)
    private String storeAddress;

    @Column(name = "phone", nullable = false, length = 20, unique = true)
    private String phone;

    @Column(name = "engineer_name", nullable = false, length = 20)
    private String engineerName;

    @Column(name = "headline", length = 100)
    private String headline;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_type", nullable = false, length = 30)
    private EngineerRegistrationType registrationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_status", nullable = false, length = 30)
    private EngineerClaimStatus claimStatus;

    @OneToMany(mappedBy = "engineer")
    private List<Portfolio> portfolios = new ArrayList<>();

    @OneToMany(mappedBy = "engineer")
    private List<EngineerCategory> engineerCategories = new ArrayList<>();

    public static Engineer create(
            User user,
            Region region,
            String profileImage,
            String introduction,
            String storeName,
            String storeAddress,
            String phone,
            String engineerName,
            String headline
    ) {
        Engineer engineer = new Engineer();
        engineer.user = user;
        engineer.region = region;
        engineer.profileImage = profileImage;
        engineer.profileThumbnailImage = null;
        engineer.introduction = introduction;
        engineer.storeName = storeName;
        engineer.storeAddress = storeAddress;
        engineer.phone = phone;
        engineer.engineerName = engineerName;
        engineer.headline = headline;
        engineer.registrationType = EngineerRegistrationType.SELF_REGISTERED;
        engineer.claimStatus = EngineerClaimStatus.APPROVED;
        return engineer;
    }

    public static Engineer createAdminCreated(
            Region region,
            String profileImage,
            String introduction,
            String storeName,
            String storeAddress,
            String phone,
            String engineerName,
            String headline
    ) {
        Engineer engineer = new Engineer();
        engineer.user = null;
        engineer.region = region;
        engineer.profileImage = profileImage;
        engineer.profileThumbnailImage = null;
        engineer.introduction = introduction;
        engineer.storeName = storeName;
        engineer.storeAddress = storeAddress;
        engineer.phone = phone;
        engineer.engineerName = engineerName;
        engineer.headline = headline;
        engineer.registrationType = EngineerRegistrationType.ADMIN_CREATED;
        engineer.claimStatus = EngineerClaimStatus.UNCLAIMED;
        return engineer;
    }

    public void approveClaim(User user) {
        this.user = user;
        this.claimStatus = EngineerClaimStatus.APPROVED;
    }

    public void updateInfo(
            String engineerName,
            String headline,
            String phone,
            String profileImage,
            String introduction,
            String storeName,
            String storeAddress,
            Region region
    ) {
        this.engineerName = engineerName;
        this.headline = headline;
        this.phone = phone;
        if (!Objects.equals(this.profileImage, profileImage)) {
            this.profileThumbnailImage = null;
        }
        this.profileImage = profileImage;
        this.introduction = introduction;
        this.storeName = storeName;
        this.storeAddress = storeAddress;
        this.region = region;
    }

    public String getDisplayProfileImage() {
        return profileThumbnailImage != null && !profileThumbnailImage.isBlank()
                ? profileThumbnailImage
                : profileImage;
    }
}

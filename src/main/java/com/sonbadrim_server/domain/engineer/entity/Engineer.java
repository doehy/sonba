package com.sonbadrim_server.domain.engineer.entity;

import com.sonbadrim_server.domain.portfolio.entity.Portfolio;
import com.sonbadrim_server.domain.region.entity.Region;
import com.sonbadrim_server.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import com.sonbadrim_server.global.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "engineer")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Engineer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "engineer_id")
    private Long engineerId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Column(name = "profileImage", length = 512)
    private String profileImage;

    @Column(name = "introduction", columnDefinition = "TEXT", length = 300)
    private String introduction;

    @Column(name = "store_name", length = 20)
    private String storeName;

    @Column(name = "store_address", length = 255)
    private String storeAddress;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "phone_public", nullable = false)
    private boolean phonePublic;

    @Column(name = "engineer_name", nullable = false, length = 20)
    private String engineerName;

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
            boolean phonePublic,
            String engineerName
    ) {
        Engineer engineer = new Engineer();
        engineer.user = user;
        engineer.region = region;
        engineer.profileImage = profileImage;
        engineer.introduction = introduction;
        engineer.storeName = storeName;
        engineer.storeAddress = storeAddress;
        engineer.phone = phone;
        engineer.phonePublic = phonePublic;
        engineer.engineerName = engineerName;
        return engineer;
    }

    public void updateInfo(
            String engineerName,
            String phone,
            Boolean phonePublic,
            String profileImage,
            String introduction,
            String storeName,
            String storeAddress,
            Region region
    ) {
        this.engineerName = engineerName;
        this.phone = phone;
        this.phonePublic = phonePublic;
        this.profileImage = profileImage;
        this.introduction = introduction;
        this.storeName = storeName;
        this.storeAddress = storeAddress;
        this.region = region;
    }
}

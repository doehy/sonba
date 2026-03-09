package com.sonbadrim_server.domain.engineer;

import com.sonbadrim_server.domain.portfolio.Portfolio;
import com.sonbadrim_server.domain.region.Region;
import com.sonbadrim_server.domain.user.User;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "engineer")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Engineer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "engineer_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Column(name = "profileImage", length = 512)
    private String profileImage;

    @Column(name = "introduction", columnDefinition = "TEXT")
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
}

package com.sonbadrim_server.domain.region;

import com.sonbadrim_server.domain.engineer.Engineer;
import com.sonbadrim_server.domain.user.User;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import com.sonbadrim_server.global.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "region",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_region_city_district", columnNames = {"city", "district"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "region_id")
    private Integer regionId;

    @Column(name = "city", nullable = false, length = 50)
    private String city;

    @Column(name = "district", nullable = false, length = 50)
    private String district;

    @OneToMany(mappedBy = "region")
    private List<User> users = new ArrayList<>();

    @OneToMany(mappedBy = "region")
    private List<Engineer> engineers = new ArrayList<>();
}

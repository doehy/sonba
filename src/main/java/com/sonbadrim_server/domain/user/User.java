package com.sonbadrim_server.domain.user;

import com.sonbadrim_server.domain.block.Block;
import com.sonbadrim_server.domain.engineer.Engineer;
import com.sonbadrim_server.domain.inquiry.Inquiry;
import com.sonbadrim_server.domain.inquiry.InquiryMessage;
import com.sonbadrim_server.domain.notification.Notification;
import com.sonbadrim_server.domain.region.Region;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "`user`")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Column(name = "kakao_id", nullable = false, unique = true)
    private Long kakaoId;

    @Column(name = "nickname", nullable = false, length = 20)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "birth")
    private LocalDate birth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @Column(name = "kakao_access_token", length = 255)
    private String kakaoAccessToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @OneToOne(mappedBy = "user")
    private Engineer engineer;

    @OneToMany(mappedBy = "user")
    private List<Notification> notifications = new ArrayList<>();

    @OneToMany(mappedBy = "blocker")
    private List<Block> blocksByMe = new ArrayList<>();

    @OneToMany(mappedBy = "blocked")
    private List<Block> blocksAgainstMe = new ArrayList<>();

    @OneToMany(mappedBy = "requester")
    private List<Inquiry> requestedInquiries = new ArrayList<>();

    @OneToMany(mappedBy = "target")
    private List<Inquiry> targetedInquiries = new ArrayList<>();

    @OneToMany(mappedBy = "sender")
    private List<InquiryMessage> inquiryMessages = new ArrayList<>();
}

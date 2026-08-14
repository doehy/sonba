package com.sonbadream_server.domain.engineer.entity;

import com.sonbadream_server.domain.user.entity.User;
import com.sonbadream_server.global.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "interested_engineer",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_interested_engineer_user_engineer",
                        columnNames = {"user_id", "engineer_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterestedEngineer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "interested_engineer_id")
    private Long interestedEngineerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "engineer_id", nullable = false)
    private Engineer engineer;

    public static InterestedEngineer create(User user, Engineer engineer) {
        InterestedEngineer interestedEngineer = new InterestedEngineer();
        interestedEngineer.user = user;
        interestedEngineer.engineer = engineer;
        return interestedEngineer;
    }
}

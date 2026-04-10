package com.sonbadrim_server.domain.inquiry.entity;

import com.sonbadrim_server.domain.user.entity.User;
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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.sonbadrim_server.global.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "inquiry")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inquiry_id")
    private Long inquiryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_user_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User target;

    @Enumerated(EnumType.STRING)
    @Column(name = "inquiry_status", nullable = false, length = 20)
    private InquiryStatus inquiryStatus;

    @Column(name = "register_left_at")
    private LocalDateTime requesterLeftAt;

    @Column(name = "target_left_at")
    private LocalDateTime targetLeftAt;

    @Column(name = "requester_last_read_message_id")
    private Long requesterLastReadMessageId;

    @Column(name = "target_last_read_message_id")
    private Long targetLastReadMessageId;

    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @OneToMany(mappedBy = "inquiry")
    private List<InquiryMessage> messages = new ArrayList<>();

    public static Inquiry create(User requester, User target) {
        Inquiry inquiry = new Inquiry();
        inquiry.requester = requester;
        inquiry.target = target;
        inquiry.inquiryStatus = InquiryStatus.OPEN;
        return inquiry;
    }

    public void updateLastMessage(Long messageId) {
        this.lastMessageId = messageId;
        this.lastMessageAt = LocalDateTime.now();
    }
}

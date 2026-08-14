package com.sonbadream_server.domain.inquiry.entity;

import com.sonbadream_server.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import com.sonbadream_server.global.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "inquiry_message",
        indexes = {
                @Index(
                        name = "idx_inquiry_message_inquiry_cursor",
                        columnList = "inquiry_id, inquiry_message_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InquiryMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inquiry_message_id")
    private Long inquiryMessageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inquiry_id", nullable = false)
    private Inquiry inquiry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(name = "context", nullable = false, columnDefinition = "TEXT")
    private String content;

    public static InquiryMessage create(Inquiry inquiry, User sender, String content) {
        InquiryMessage inquiryMessage = new InquiryMessage();
        inquiryMessage.inquiry = inquiry;
        inquiryMessage.sender = sender;
        inquiryMessage.content = content;
        return inquiryMessage;
    }
}

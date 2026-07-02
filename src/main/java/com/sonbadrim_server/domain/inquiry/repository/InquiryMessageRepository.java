package com.sonbadrim_server.domain.inquiry.repository;

import com.sonbadrim_server.domain.inquiry.entity.InquiryMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface InquiryMessageRepository extends JpaRepository<InquiryMessage, Long> {

    List<InquiryMessage> findByInquiry_InquiryIdOrderByInquiryMessageIdAsc(Long inquiryId);

    List<InquiryMessage> findByInquiry_InquiryIdOrderByInquiryMessageIdDesc(Long inquiryId, Pageable pageable);

    List<InquiryMessage> findByInquiry_InquiryIdAndInquiryMessageIdLessThanOrderByInquiryMessageIdDesc(
            Long inquiryId,
            Long inquiryMessageId,
            Pageable pageable
    );

    List<InquiryMessage> findByInquiryMessageIdIn(Collection<Long> inquiryMessageIds);

    @Query("""
            select m.inquiry.inquiryId as inquiryId,
                   count(m) as unreadCount
            from InquiryMessage m
            join m.inquiry i
            where i.inquiryId in :inquiryIds
              and m.sender.userId <> :currentUserId
              and (
                    (i.requester.userId = :currentUserId
                     and (i.requesterLastReadMessageId is null
                          or m.inquiryMessageId > i.requesterLastReadMessageId))
                 or (i.target.userId = :currentUserId
                     and (i.targetLastReadMessageId is null
                          or m.inquiryMessageId > i.targetLastReadMessageId))
              )
            group by m.inquiry.inquiryId
            """)
    List<UnreadCountProjection> countUnreadByInquiryIds(
            Collection<Long> inquiryIds,
            Long currentUserId
    );

    interface UnreadCountProjection {
        Long getInquiryId();

        Long getUnreadCount();
    }
}

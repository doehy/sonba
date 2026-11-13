package com.sonbadream_server.domain.notification.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationBulkRepository {

    private final JdbcTemplate jdbcTemplate;

    public int saveNoticeNotifications(
            Long broadcastId,
            List<Long> userIds,
            Integer notificationTypeId,
            String content
    ) {
        String sql = """
                insert ignore into notification (
                    user_id,
                    notification_type_id,
                    notification_broadcast_id,
                    content,
                    is_read,
                    sse_sent,
                    created_at,
                    modified_at
                )
                values (?, ?, ?, ?, false, false, ?, ?)
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        int[] updateCounts = jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setLong(1, userIds.get(i));
                ps.setInt(2, notificationTypeId);
                ps.setLong(3, broadcastId);
                ps.setString(4, content);
                ps.setTimestamp(5, now);
                ps.setTimestamp(6, now);
            }

            @Override
            public int getBatchSize() {
                return userIds.size();
            }
        });
        // Never commit the cursor unless every requested user has a persisted notification.
        // 데이터 정합성 확보를 위함이다. insert ignore가 일부 에러 코드를 숨길 수 있으니 실제로 존재하는지 확인한다.
        String placeholders = String.join(",", Collections.nCopies(userIds.size(), "?"));
        List<Object> arguments = new ArrayList<>();
        arguments.add(broadcastId);
        arguments.addAll(userIds);
        Long persisted = jdbcTemplate.queryForObject(
                "select count(*) from notification where notification_broadcast_id = ? and user_id in ("
                        + placeholders + ")", Long.class, arguments.toArray());
        if (persisted == null || persisted != userIds.size()) {
            throw new org.springframework.dao.DataIntegrityViolationException("공지 알림 일부 저장 실패");
        }
        return countInsertedRows(updateCounts, userIds.size());
    }

    private int countInsertedRows(int[] updateCounts, int requestedCount) {
        int insertedCount = 0;
        for (int updateCount : updateCounts) {
            if (updateCount == Statement.SUCCESS_NO_INFO) {
                return requestedCount;
            }
            if (updateCount > 0) {
                insertedCount += updateCount;
            }
        }
        return insertedCount;
    }
}

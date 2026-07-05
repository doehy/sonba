package com.sonbadrim_server.domain.notification.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationBulkRepository {

    private final JdbcTemplate jdbcTemplate;

    public void saveNoticeNotifications(List<Long> userIds, Integer notificationTypeId, String content) {
        String sql = """
                insert into notification (
                    user_id,
                    notification_type_id,
                    content,
                    is_read,
                    sse_sent,
                    created_at,
                    modified_at
                )
                values (?, ?, ?, false, false, ?, ?)
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setLong(1, userIds.get(i));
                ps.setInt(2, notificationTypeId);
                ps.setString(3, content);
                ps.setTimestamp(4, now);
                ps.setTimestamp(5, now);
            }

            @Override
            public int getBatchSize() {
                return userIds.size();
            }
        });
    }
}

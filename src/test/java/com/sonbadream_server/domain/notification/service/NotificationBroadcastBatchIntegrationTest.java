package com.sonbadream_server.domain.notification.service;

import com.sonbadream_server.domain.notification.repository.NotificationBulkRepository;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.BatchStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:batch-restart-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@DirtiesContext
class NotificationBroadcastBatchIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationBroadcastJobLauncher worker;
    @Autowired JobRepository jobs;
    @Autowired org.springframework.batch.core.launch.JobOperator operator;
    @Autowired com.sonbadream_server.domain.notification.repository.NotificationBroadcastRepository broadcasts;
    @Autowired NotificationBroadcastProgressService progress;
    @MockitoSpyBean NotificationBulkRepository bulk;

    @Test
    void failedChunkRollsBackAndSameInstanceResumesWithoutDuplicates() {
        jdbc.update("insert into notification_type(notification_type_id,type,template) values(2,'NOTICE','notice')");
        for (int i = 1; i <= 1002; i++) {
            jdbc.update("insert into `user`(kakao_id,nickname,role,status) values(?,?,'USER','ACTIVE')", (long)i, "user" + i);
        }
        jdbc.update("insert into notification_broadcast(notification_type_id,content,status,total_target_count,processed_count,last_processed_user_id,failed_count,created_at,modified_at) values(2,'notice','PENDING',1002,0,0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        Long id = jdbc.queryForObject("select max(notification_broadcast_id) from notification_broadcast", Long.class);
        AtomicInteger chunks = new AtomicInteger();
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (chunks.incrementAndGet() == 2) throw new IllegalStateException("simulated failure after INSERT");
            return result;
        }).when(bulk).saveNoticeNotifications(eq(id), anyList(), anyInt(), anyString());

        worker.process(id);
        assertThat(jdbc.queryForObject("select count(*) from notification", Long.class)).isEqualTo(1000);
        assertThat(jdbc.queryForObject("select processed_count from notification_broadcast where notification_broadcast_id=?", Long.class, id)).isEqualTo(1000);
        var failed = jobs.getLastJobExecution(NotificationBroadcastJobLauncher.JOB_NAME, NotificationBroadcastJobLauncher.parameters(id));
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);

        reset(bulk);
        // Simulate the durable state left by a dead process, then exercise the recovery runner.
        failed.setStatus(BatchStatus.STARTED);
        failed.setEndTime(null);
        jobs.update(failed);
        jdbc.update("update notification_broadcast set status='PROCESSING' where notification_broadcast_id=?", id);
        new NotificationBroadcastRecovery(broadcasts, progress, worker, jobs, operator).run(null);
        var completed = jobs.getLastJobExecution(NotificationBroadcastJobLauncher.JOB_NAME, NotificationBroadcastJobLauncher.parameters(id));
        assertThat(completed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(completed.getJobInstanceId()).isEqualTo(failed.getJobInstanceId());
        assertThat(completed.getId()).isNotEqualTo(failed.getId());
        assertThat(jdbc.queryForObject("select count(*) from notification", Long.class)).isEqualTo(1002);
        assertThat(jdbc.queryForObject("select count(distinct user_id) from notification", Long.class)).isEqualTo(1002);
        assertThat(jdbc.queryForObject("select processed_count from notification_broadcast where notification_broadcast_id=?", Long.class, id)).isEqualTo(1002);
        verify(bulk, times(1)).saveNoticeNotifications(eq(id), eq(java.util.List.of(1001L, 1002L)), anyInt(), anyString());
        assertThat(jdbc.queryForObject("select status from notification_broadcast where notification_broadcast_id=?", String.class, id)).isEqualTo("COMPLETED");
        // A duplicate event after completion cannot generate another execution.
        worker.process(id);
        assertThat(jobs.getLastJobExecution(NotificationBroadcastJobLauncher.JOB_NAME, NotificationBroadcastJobLauncher.parameters(id)).getId()).isEqualTo(completed.getId());
    }
}

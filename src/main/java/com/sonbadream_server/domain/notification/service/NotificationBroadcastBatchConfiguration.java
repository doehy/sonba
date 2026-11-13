package com.sonbadream_server.domain.notification.service;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class NotificationBroadcastBatchConfiguration {
    @Bean
    @StepScope
    JdbcPagingItemReader<Long> broadcastUserReader(DataSource dataSource) throws Exception {
        return new JdbcPagingItemReaderBuilder<Long>()
                .name("broadcastUserReader")
                .dataSource(dataSource)
                .selectClause("select user_id")
                .fromClause("from `user`")
                .whereClause("where status = 'ACTIVE'")
                .sortKeys(Map.of("user_id", Order.ASCENDING))
                .rowMapper((rs, rowNum) -> rs.getLong("user_id"))
                .pageSize(1000)
                .saveState(true)
                .build();
    }

    @Bean
    @StepScope
    ItemWriter<Long> broadcastNotificationWriter(
            @Value("#{jobParameters['broadcastId']}") Long broadcastId,
            NotificationBroadcastProgressService progress) {
        return chunk -> progress.writeChunk(broadcastId, List.copyOf(chunk.getItems()));
    }

    @Bean
    Job notificationBroadcastJob(JobRepository repository, PlatformTransactionManager transactionManager,
                                 NotificationBroadcastProgressService progress,
                                 JdbcPagingItemReader<Long> broadcastUserReader,
                                 ItemWriter<Long> broadcastNotificationWriter) {
        var step = new StepBuilder("saveBroadcastNotifications", repository)
                .<Long, Long>chunk(1000)
                .transactionManager(transactionManager)
                .reader(broadcastUserReader)
                .writer(broadcastNotificationWriter)
                .build();
        return new JobBuilder(NotificationBroadcastJobLauncher.JOB_NAME, repository)
                .start(step)
                .listener(new JobExecutionListener() {
                    @Override
                    public void afterJob(JobExecution execution) {
                        Long id = execution.getJobParameters().getLong("broadcastId");
                        if (execution.getStatus() == BatchStatus.COMPLETED) progress.complete(id);
                        else progress.fail(id, execution.getExitStatus().getExitDescription());
                    }
                }).build();
    }
}

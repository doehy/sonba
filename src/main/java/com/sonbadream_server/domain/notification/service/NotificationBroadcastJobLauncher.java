package com.sonbadream_server.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationBroadcastJobLauncher {
    public static final String JOB_NAME = "notificationBroadcastJob";
    private final Job notificationBroadcastJob;
    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final NotificationBroadcastProgressService progress;

    public static JobParameters parameters(Long broadcastId) {
        // 재시도는 동일한 JobInstance를 식별해야 하므로, 여기에 타임스탬프를 추가하지 마세요.
        return new JobParametersBuilder().addLong("broadcastId", broadcastId).toJobParameters();
    }

    public void process(Long broadcastId) {
        if (!progress.start(broadcastId)) return;
        try {
            var parameters = parameters(broadcastId);
            var previous = jobRepository.getLastJobExecution(JOB_NAME, parameters);
            if (previous != null && previous.getStatus() == BatchStatus.COMPLETED) {
                progress.complete(broadcastId);
            } else {
                jobOperator.start(notificationBroadcastJob, parameters);
            }
        } catch (Exception e) {
            progress.fail(broadcastId, e.getMessage());
        }
    }
}

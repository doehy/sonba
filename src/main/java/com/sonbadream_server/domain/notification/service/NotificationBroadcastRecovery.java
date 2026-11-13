package com.sonbadream_server.domain.notification.service;

import com.sonbadream_server.domain.notification.repository.NotificationBroadcastRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 명시적으로 활성화하는 단일 인스턴스 복구 모드입니다. 기존에 실행 중이던 모든 Worker는 반드시 중지되어 있어야 합니다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.notification-batch.recover-on-startup", havingValue = "true")
public class NotificationBroadcastRecovery implements ApplicationRunner {
    private final NotificationBroadcastRepository broadcasts;
    private final NotificationBroadcastProgressService progress;
    private final NotificationBroadcastJobLauncher worker;
    private final JobRepository jobs;
    private final JobOperator operator;

    @Override
    public void run(ApplicationArguments args) {
        for (Long id : broadcasts.findInterruptedIds()) {
            var execution = jobs.getLastJobExecution(NotificationBroadcastJobLauncher.JOB_NAME,
                    NotificationBroadcastJobLauncher.parameters(id));
            if (execution != null && execution.getStatus().isRunning()) operator.recover(execution);
            progress.prepareRecovery(id);
            worker.process(id);
        }
    }
}

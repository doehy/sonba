package com.sonbadream_server.domain.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationBroadcastJobLauncherTest {
    @Mock Job job;
    @Mock JobOperator operator;
    @Mock JobRepository repository;
    @Mock NotificationBroadcastProgressService progress;
    @InjectMocks NotificationBroadcastJobLauncher worker;

    @Test void launchesWithStableIdentity() throws Exception {
        when(progress.start(7L)).thenReturn(true);
        worker.process(7L);
        verify(operator).start(job, NotificationBroadcastJobLauncher.parameters(7L));
    }

    @Test void duplicateEventDoesNotLaunchJob() {
        when(progress.start(7L)).thenReturn(false);
        worker.process(7L);
        verifyNoInteractions(operator);
    }

    @Test void launchFailureIsVisibleAndRetryable() throws Exception {
        when(progress.start(7L)).thenReturn(true);
        when(operator.start(job, NotificationBroadcastJobLauncher.parameters(7L))).thenThrow(new IllegalStateException("offline"));
        worker.process(7L);
        verify(progress).fail(7L, "offline");
    }
}

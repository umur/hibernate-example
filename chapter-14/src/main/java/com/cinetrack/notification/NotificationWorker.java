package com.cinetrack.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Queue-style notification processor using {@code FOR UPDATE SKIP LOCKED}.
 *
 * <h2>How SKIP LOCKED works</h2>
 * When multiple instances of this worker call {@link #processBatch} at the
 * same time, each issues the same {@code SELECT ... FOR UPDATE SKIP LOCKED}
 * query.  PostgreSQL locks the rows it returns to the first requester and
 * skips those rows for all subsequent requesters — so each worker gets a
 * completely disjoint set of notifications without any waiting or blocking.
 *
 * <h2>Contrast with SELECT FOR UPDATE (without SKIP LOCKED)</h2>
 * Without SKIP LOCKED, the second worker would block on the locked rows until
 * the first worker's transaction commits.  At that point those rows would have
 * status = PROCESSING or DONE and the second worker's query would return them
 * anyway — resulting in duplicate processing.
 *
 * <h2>Transaction boundary</h2>
 * The lock is held for the entire transaction.  We update status to PROCESSING
 * before doing any external work so that a crash mid-processing leaves the row
 * in PROCESSING rather than PENDING (allowing a separate cleanup job to detect
 * stuck rows).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationWorker {

    private static final int DEFAULT_BATCH_SIZE = 10;

    private final NotificationRepository notificationRepository;

    /**
     * Claims up to {@code batchSize} PENDING notifications, transitions them to
     * PROCESSING within the same transaction, then marks them DONE.
     *
     * In a real system the "send notification" step would happen between
     * PROCESSING and DONE, outside the lock (e.g. calling an external API).
     * For the purposes of this chapter the state machine is what matters.
     *
     * @return the number of notifications processed
     */
    @Transactional
    public int processBatch(int batchSize) {
        List<PendingNotification> batch = notificationRepository.claimPendingBatch(batchSize);

        if (batch.isEmpty()) {
            log.debug("No pending notifications to process");
            return 0;
        }

        log.info("Claimed {} notifications for processing", batch.size());

        for (PendingNotification notification : batch) {
            notification.setStatus(NotificationStatus.PROCESSING);
        }
        // Flush PROCESSING status within the same transaction — locks held
        notificationRepository.saveAll(batch);

        // Simulate work (in production: call email/push API here)
        batch.forEach(n -> {
            log.debug("Sending notification {} to user {}: {}", n.getId(), n.getUserId(), n.getMessage());
            n.setStatus(NotificationStatus.DONE);
        });

        notificationRepository.saveAll(batch);
        return batch.size();
    }

    /**
     * Convenience overload using the default batch size.
     */
    @Transactional
    public int processBatch() {
        return processBatch(DEFAULT_BATCH_SIZE);
    }
}

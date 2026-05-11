package com.cinetrack.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<PendingNotification, Long> {

    /**
     * Atomically claims a batch of PENDING notifications for this worker.
     *
     * {@code FOR UPDATE} acquires an exclusive row lock on each claimed row.
     * {@code SKIP LOCKED} skips any row that is already locked by another
     * transaction instead of waiting for it: so two workers running this
     * query concurrently will each get a disjoint set of rows with no
     * blocking and no duplicates.
     *
     * <p>This pattern replaces a message broker for moderate-throughput queues
     * where "at-least-once" delivery within the same database transaction is
     * sufficient.  The caller is responsible for updating {@code status} to
     * PROCESSING before the transaction commits, so that the rows are not
     * re-claimed by the next poll cycle.</p>
     *
     * <p>Note: {@code nativeQuery = true} is required because JPQL does not
     * support {@code FOR UPDATE SKIP LOCKED}.</p>
     */
    @Query(value = "SELECT * FROM notification_queue WHERE status = 'PENDING' " +
                   "ORDER BY id LIMIT :batchSize FOR UPDATE SKIP LOCKED",
           nativeQuery = true)
    List<PendingNotification> claimPendingBatch(@Param("batchSize") int batchSize);
}

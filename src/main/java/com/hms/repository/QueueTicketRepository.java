package com.hms.repository;

import com.hms.entity.QueueTicket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface QueueTicketRepository extends JpaRepository<QueueTicket, Long> {

    /**
     * Locks the day's highest-numbered ticket row (if any) for the life of
     * the caller's transaction — direct analogue of Django's
     * {@code QueueTicket.objects.select_for_update().filter(created_at__date=today).order_by("-queue_number").first()}
     * in appointment_checkin. Callers must run inside a write transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QueueTicket> findFirstByCreatedAtBetweenOrderByQueueNumberDesc(OffsetDateTime dayStart, OffsetDateTime dayEnd);

    List<QueueTicket> findByCreatedAtBetweenAndServedFalseOrderByQueueNumberAsc(OffsetDateTime dayStart, OffsetDateTime dayEnd);
}

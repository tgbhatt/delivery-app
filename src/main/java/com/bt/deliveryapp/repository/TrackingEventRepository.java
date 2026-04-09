package com.bt.deliveryapp.repository;

import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.TrackingEvent;
import com.bt.deliveryapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for TrackingEvent — handles reading and writing status change history.
 *
 * --- What does this store? ---
 * Every time a delivery's status changes, one TrackingEvent is saved here.
 * This repository lets us retrieve that history in order — oldest to newest —
 * so we can display the tracking timeline to the customer.
 *
 * --- Key concept: OrderByTimestampAsc ---
 * The "OrderByTimestampAsc" part of the method name tells Spring to sort
 * results by the timestamp column in ascending order (oldest first).
 * This means the timeline always shows:
 *   "10:00 — Order placed" at the top
 *   "12:05 — Delivered"    at the bottom
 *
 * --- This is Bhavya's repository (Feature 4: Live Status Tracking) ---
 */
@Repository
public interface TrackingEventRepository extends JpaRepository<TrackingEvent, Long> {

    // Get the full history of status changes for a delivery, oldest first
    // This is the data that builds the tracking timeline on the customer's page
    // SQL: SELECT * FROM tracking_events WHERE delivery_request_id = ? ORDER BY timestamp ASC
    List<TrackingEvent> findByDeliveryRequestOrderByTimestampAsc(DeliveryRequest deliveryRequest);

    // Same as above but using just the ID — more convenient when you only have the ID
    List<TrackingEvent> findByDeliveryRequestIdOrderByTimestampAsc(Long deliveryRequestId);

    // Get the most recent event for a delivery — useful for showing "current status" quickly
    // "First" + "OrderByTimestampDesc" = the latest one
    Optional<TrackingEvent> findFirstByDeliveryRequestOrderByTimestampDesc(DeliveryRequest deliveryRequest);

    // Find all tracking events where a specific user made the status update.
    // Used when deleting an agent — we need to "unlink" their name from history
    // before we can delete their User record from the database.
    // SQL: SELECT * FROM tracking_events WHERE updated_by_user_id = ?
    List<TrackingEvent> findByUpdatedBy(User updatedBy);
}

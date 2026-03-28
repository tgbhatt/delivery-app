package com.bt.deliveryapp.repository;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.enums.Priority;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * The repository for the DeliveryRequest table in MySQL.
 *
 * --- What queries do we need? ---
 * Think about what the app needs to look up:
 * 1. A customer opens "My Bookings" → we need all requests made by THAT customer
 * 2. Admin opens the dashboard → we need all requests with status PENDING or ASSIGNED
 * 3. Feature 2 (slot scheduling) → we need all requests with a certain status or priority
 *
 * Each of these becomes one method below. Spring generates the SQL automatically
 * just from the method name.
 *
 * --- Method naming rules ---
 * findBy + FieldName         → WHERE field = ?
 * findBy + Field1 + And + Field2 → WHERE field1 = ? AND field2 = ?
 * findBy + Field + OrderBy + OtherField + Desc → ORDER BY
 *
 * So "findByCustomerAndStatus" becomes:
 * SELECT * FROM delivery_requests WHERE customer_id = ? AND status = ?
 */
@Repository
public interface DeliveryRequestRepository extends JpaRepository<DeliveryRequest, Long> {

    // All bookings made by a specific customer (for "My Bookings" page)
    // Results sorted newest first (most recent booking at the top)
    List<DeliveryRequest> findByCustomerOrderByCreatedAtDesc(User customer);

    // All deliveries with a given status (e.g. all PLACED requests awaiting scheduling)
    // Used by Feature 2 and Feature 3
    List<DeliveryRequest> findByStatus(DeliveryStatusEnum status);

    // All deliveries for a customer with a specific status
    // e.g. "show me all my DELIVERED orders"
    List<DeliveryRequest> findByCustomerAndStatus(User customer, DeliveryStatusEnum status);

    // All requests with a given priority (e.g. to process URGENT ones first)
    List<DeliveryRequest> findByPriority(Priority priority);

    // All requests that have NOT yet been scheduled (still sitting as PLACED)
    // Feature 2 uses this to find deliveries that still need a slot assigned
    List<DeliveryRequest> findByStatusOrderByPriorityDesc(DeliveryStatusEnum status);

    // Count how many active deliveries a customer currently has
    // "active" means anything that isn't DELIVERED or FAILED
    long countByCustomerAndStatusNot(User customer, DeliveryStatusEnum status);
}

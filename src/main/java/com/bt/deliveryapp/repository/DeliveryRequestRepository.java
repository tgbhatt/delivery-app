package com.bt.deliveryapp.repository;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.enums.Priority;
import com.bt.deliveryapp.model.Agent;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for DeliveryRequest — handles all database reads and writes for orders.
 *
 * --- What is a Repository? ---
 * A repository is the layer between your Java code and the database.
 * Instead of writing raw SQL like "SELECT * FROM delivery_requests WHERE status = 'PLACED'",
 * you just write a method name in a specific format and Spring auto-generates the SQL for you.
 *
 * --- How does it work? ---
 * By extending JpaRepository<DeliveryRequest, Long>, we get basic operations for free:
 *   save(request)        → INSERT or UPDATE row in delivery_requests table
 *   findById(id)         → SELECT * FROM delivery_requests WHERE id = ?
 *   findAll()            → SELECT * FROM delivery_requests
 *   delete(request)      → DELETE FROM delivery_requests WHERE id = ?
 *
 * The custom methods below follow Spring's naming convention:
 *   findBy[FieldName]    → Spring generates the WHERE clause automatically
 *
 * --- OOP concept ---
 * This is the REPOSITORY pattern — a classic design pattern that separates
 * data access logic from business logic. TrackingService uses this repository
 * but never writes a single line of SQL — clean separation of concerns.
 */
@Repository
public interface DeliveryRequestRepository extends JpaRepository<DeliveryRequest, Long> {

    // Get all orders placed by a specific customer
    // SQL generated: SELECT * FROM delivery_requests WHERE customer_id = ?
    List<DeliveryRequest> findByCustomer(User customer);

    // Get all orders currently at a specific status (e.g. all PLACED orders)
    // SQL generated: SELECT * FROM delivery_requests WHERE status = ?
    List<DeliveryRequest> findByStatus(DeliveryStatusEnum status);

    // Get all orders for a customer that are at a specific status
    // e.g. "show me all PLACED orders for this customer"
    List<DeliveryRequest> findByCustomerAndStatus(User customer, DeliveryStatusEnum status);

    // Get all orders assigned to a specific agent
    // Used by the agent dashboard (Feature 5) to show their deliveries
    List<DeliveryRequest> findByAgent(Agent agent);

    // Get all orders assigned to a specific agent, newest first
    // Used by DeliveryAgentService.getAssignedOrders()
    // SQL generated: SELECT * FROM delivery_requests WHERE agent_id = ? ORDER BY created_at DESC
    List<DeliveryRequest> findByAgentOrderByCreatedAtDesc(Agent agent);

    // Get all immediate orders (Order Now) at a given status
    // Used by admin dashboard to show the live feed
    List<DeliveryRequest> findByIsImmediateAndStatus(boolean isImmediate, DeliveryStatusEnum status);

    // Get all orders for a specific agent at a given status
    // e.g. all OUT_FOR_DELIVERY orders for agent X
    List<DeliveryRequest> findByAgentAndStatus(Agent agent, DeliveryStatusEnum status);

    // ---- Methods added to support DeliveryBookingService (Feature 1) ----

    // All orders placed by a customer, newest first — used for "My Orders" page
    // SQL generated: SELECT * FROM delivery_requests WHERE customer_id = ? ORDER BY created_at DESC
    List<DeliveryRequest> findByCustomerOrderByCreatedAtDesc(User customer);

    // ---- Methods added to support DashboardService (Feature 5) ----

    // All orders filtered by whether they are immediate (true) or scheduled (false)
    // DashboardService calls findByIsImmediate(true) for the live feed
    // and findByIsImmediate(false) for the scheduled queue
    // SQL generated: SELECT * FROM delivery_requests WHERE is_immediate = ?
    List<DeliveryRequest> findByIsImmediate(boolean isImmediate);

    // ---- Methods added to support RouteOptimisationService (Feature 3) ----

    // COUNT how many orders an agent has with a given status — faster than loading all objects
    // SQL generated: SELECT COUNT(*) FROM delivery_requests WHERE agent_id = ? AND status = ?
    long countByAgentAndStatus(Agent agent, DeliveryStatusEnum status);

    // COUNT how many orders in a given pickup zone have a given status
    // Used by getZoneStats() to show the admin a snapshot of orders per zone
    // SQL generated: SELECT COUNT(*) FROM delivery_requests WHERE status = ? AND pickup_zone = ?
    long countByStatusAndPickupZone(DeliveryStatusEnum status, String pickupZone);

    // All orders with a given priority — used by slot scheduling
    List<DeliveryRequest> findByPriority(Priority priority);

    // All orders at a status, sorted by priority descending — URGENT orders first
    // SQL generated: SELECT * FROM delivery_requests WHERE status = ? ORDER BY priority DESC
    List<DeliveryRequest> findByStatusOrderByPriorityDesc(DeliveryStatusEnum status);
}

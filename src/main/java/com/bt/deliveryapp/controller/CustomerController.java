package com.bt.deliveryapp.controller;

import com.bt.deliveryapp.enums.Priority;
import com.bt.deliveryapp.enums.UserRole;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.TimeSlot;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.service.DeliveryBookingService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CustomerController — the web layer for all customer-facing pages.
 *
 * ─── What does a Controller do? ─────────────────────────────────────────────
 * A Controller is the "front desk" of our application. When a customer's
 * browser sends a request (e.g. "show me my orders"), this class receives
 * that request, asks the service for the data it needs, then tells Thymeleaf
 * which HTML file to show and what data to fill it with.
 *
 * The Controller itself does NOT do business logic — that lives in the Service.
 * This separation is called "Separation of Concerns" — each class has one job.
 *
 * ─── What pages does this controller handle? ────────────────────────────────
 * GET  /customer/orders          → "My Orders" page — all their bookings
 * GET  /customer/book            → Booking form — place a new delivery
 * POST /customer/book            → Actually create the booking (form submit)
 * POST /customer/cancel/{id}     → Cancel a specific order
 *
 * ─── How does login work here? ──────────────────────────────────────────────
 * Every method checks the HttpSession for "loggedInUser".
 * If no user is logged in, or if the user is not a CUSTOMER, they are
 * redirected to /login. This prevents admins or agents from accidentally
 * landing on customer pages.
 *
 * ─── POST-Redirect-GET pattern ──────────────────────────────────────────────
 * After a form submission (POST /customer/book), we do NOT render a page.
 * We redirect to GET /customer/orders instead. This prevents the browser
 * from resubmitting the form if the user presses refresh.
 * RedirectAttributes lets us pass a one-time flash message across the redirect.
 */
@Controller
public class CustomerController {

    // Spring auto-injects DeliveryBookingService — we never call "new"
    @Autowired
    private DeliveryBookingService deliveryBookingService;

    // =========================================================================
    // GET /customer/orders — "My Orders" page
    // =========================================================================

    /**
     * Loads and displays all orders placed by the logged-in customer.
     *
     * What happens step by step:
     *   1. Read "loggedInUser" from the session — redirect to /login if missing
     *   2. Confirm the user is a CUSTOMER — admins and agents use different pages
     *   3. Ask the service for all orders this customer has placed, newest first
     *   4. Put the list in the model so Thymeleaf can loop through it with th:each
     *   5. Return "customer/orders" — Thymeleaf finds templates/customer/orders.html
     *
     * The template loops over ${orders} and shows each order as a card.
     * If the list is empty, it shows an empty-state message with a "Book" button.
     *
     * @param model   holds the data Thymeleaf will render into the HTML
     * @param session holds the logged-in user object (set at login)
     * @return the template name, or a redirect string if not logged in
     */
    @GetMapping("/customer/orders")
    public String showOrders(Model model, HttpSession session) {

        // --- Session check ---
        // getAttribute() returns Object (not User), so we cast it.
        // If nothing is stored under "loggedInUser", it returns null.
        User loggedInUser = (User) session.getAttribute("loggedInUser");

        if (loggedInUser == null) {
            // Not logged in at all → send to login page
            return "redirect:/login";
        }

        if (loggedInUser.getRole() != UserRole.CUSTOMER) {
            // Logged in but not a customer (e.g. an admin or agent) → login page
            // In a production app you would show a 403 Forbidden page instead.
            return "redirect:/login";
        }

        // --- Fetch orders ---
        // The service calls: SELECT * FROM delivery_requests
        //                    WHERE customer_id = ? ORDER BY created_at DESC
        List<DeliveryRequest> orders = deliveryBookingService.getOrdersForCustomer(loggedInUser);

        // --- Put data in the Model ---
        // model.addAttribute("key", value) means: in the HTML, ${key} = value
        model.addAttribute("orders", orders);
        model.addAttribute("loggedInUser", loggedInUser);

        // --- Return the template name ---
        // Spring looks for: src/main/resources/templates/customer/orders.html
        return "customer/orders";
    }

    // =========================================================================
    // GET /customer/book — Booking Form page
    // =========================================================================

    /**
     * Shows the booking wizard where a customer can create a new delivery order.
     *
     * The form has two modes:
     *   - Immediate (Order Now): no slot needed, goes straight into the live queue
     *   - Scheduled (Choose a slot): customer picks from available time windows
     *
     * For the Scheduled option, we pre-load available slots so the dropdown
     * is already populated when the page loads. We check today + the next 7 days.
     *
     * @param model   holds data Thymeleaf uses to render the form
     * @param session holds the logged-in user
     * @return the template name, or a redirect if not logged in
     */
    @GetMapping("/customer/book")
    public String showBookingForm(Model model, HttpSession session) {

        // --- Session check (same pattern as above) ---
        User loggedInUser = (User) session.getAttribute("loggedInUser");

        if (loggedInUser == null || loggedInUser.getRole() != UserRole.CUSTOMER) {
            return "redirect:/login";
        }

        // --- Load available time slots ---
        // We check today and the next 7 days, collecting all bookable slots.
        // A slot is "available" if its date is in this window AND it still has capacity.
        List<TimeSlot> availableSlots = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int daysAhead = 0; daysAhead < 7; daysAhead++) {
            // getAvailableSlots() calls: SELECT * FROM time_slots
            //                           WHERE slot_date = ? AND available = true
            List<TimeSlot> slotsForDay = deliveryBookingService.getAvailableSlots(
                    today.plusDays(daysAhead));
            availableSlots.addAll(slotsForDay);
        }

        model.addAttribute("availableSlots", availableSlots);
        model.addAttribute("loggedInUser", loggedInUser);

        // Spring looks for: src/main/resources/templates/customer/book.html
        return "customer/book";
    }

    // =========================================================================
    // POST /customer/book — Handle form submission (create the order)
    // =========================================================================

    /**
     * Receives the booking form data and creates a new delivery order.
     *
     * --- How form data arrives here ---
     * When the customer clicks "Confirm Booking", the browser sends a POST
     * request with all the form fields as parameters. Spring reads each one
     * via @RequestParam and passes them directly to this method as arguments.
     *
     * --- Parameter mapping ---
     * The form uses field names: pickupAddress, deliveryAddress, packageDescription
     * The service uses parameter names: restaurantAddress, customerAddress, orderDescription
     * We do the mapping right here in the controller — this is normal.
     *
     * --- Two paths depending on isImmediate ---
     * isImmediate = "true"  → call placeImmediateOrder() (no slot needed)
     * isImmediate = "false" → call placeScheduledOrder() with the chosen timeSlotId
     *
     * --- RedirectAttributes (flash messages) ---
     * After the POST, we redirect to GET /customer/orders.
     * RedirectAttributes lets us pass a one-time message across that redirect:
     *   addFlashAttribute("successMessage", "...") shows a green banner on arrival
     *   addFlashAttribute("errorMessage", "...") shows a red banner on arrival
     * After one display, these flash attributes disappear automatically.
     *
     * @param pickupAddress       the restaurant address (from form field "pickupAddress")
     * @param deliveryAddress     the customer's drop-off address
     * @param packageDescription  what is being delivered
     * @param priority            HIGH, MEDIUM, LOW, or URGENT (from the priority dropdown)
     * @param isImmediate         "true" for Order Now, "false" for Schedule for Later
     * @param timeSlotId          the chosen slot ID (null if isImmediate = "true")
     * @param specialInstructions optional notes for the delivery agent
     * @param session             to get the logged-in user
     * @param redirectAttributes  to pass a flash message across the redirect
     * @return redirect to the orders page
     */
    @PostMapping("/customer/book")
    public String placeOrder(
            @RequestParam("pickupAddress")      String pickupAddress,
            @RequestParam("deliveryAddress")    String deliveryAddress,
            @RequestParam("packageDescription") String packageDescription,
            @RequestParam("priority")           String priorityStr,
            @RequestParam("isImmediate")        String isImmediateStr,
            @RequestParam(value = "timeSlotId", required = false) Long timeSlotId,
            @RequestParam(value = "specialInstructions", required = false) String specialInstructions,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        // --- Session check ---
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null || loggedInUser.getRole() != UserRole.CUSTOMER) {
            return "redirect:/login";
        }

        try {
            // --- Parse the priority from the form string ---
            // The form sends "HIGH", "MEDIUM", "LOW", or "URGENT" as a plain string.
            // Priority.valueOf() converts that string into the enum constant.
            // If the string doesn't match any enum value, it throws an exception —
            // the catch block below handles that.
            Priority priority = Priority.valueOf(priorityStr.toUpperCase());

            // --- Parse the isImmediate flag ---
            // The form radio sends "true" or "false" as a string, not a boolean.
            boolean immediate = "true".equalsIgnoreCase(isImmediateStr);

            if (immediate) {
                // --- Path A: Immediate order (Order Now) ---
                // placeImmediateOrder() creates a PLACED order with HIGH priority.
                // The priority the customer chose is passed in but may be
                // overridden to HIGH internally by the service (immediate = urgent).
                deliveryBookingService.placeImmediateOrder(
                        loggedInUser,
                        pickupAddress,      // maps to restaurantAddress in the model
                        deliveryAddress,    // maps to customerAddress in the model
                        packageDescription  // maps to orderDescription in the model
                );

            } else {
                // --- Path B: Scheduled order (Schedule for Later) ---
                // placeScheduledOrder() requires a timeSlotId.
                // If none was chosen, reject with a clear message.
                if (timeSlotId == null) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Please choose a time slot for your scheduled delivery.");
                    return "redirect:/customer/book";
                }

                deliveryBookingService.placeScheduledOrder(
                        loggedInUser,
                        pickupAddress,
                        deliveryAddress,
                        packageDescription,
                        timeSlotId
                );
            }

            // --- Success: redirect back to orders list ---
            redirectAttributes.addFlashAttribute("successMessage",
                    "Your delivery has been booked successfully!");
            return "redirect:/customer/orders";

        } catch (IllegalArgumentException e) {
            // IllegalArgumentException covers:
            //   - Invalid priority string (Priority.valueOf failed)
            //   - Slot not found or already full (from placeScheduledOrder)
            //   - Missing required fields (from validateOrderInputs in the service)
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Could not place your order: " + e.getMessage());
            return "redirect:/customer/book";

        } catch (Exception e) {
            // Catch-all for unexpected errors — always show something helpful
            redirectAttributes.addFlashAttribute("errorMessage",
                    "An unexpected error occurred. Please try again.");
            return "redirect:/customer/book";
        }
    }

    // =========================================================================
    // POST /customer/cancel/{id} — Cancel an order
    // =========================================================================

    /**
     * Cancels a specific delivery order belonging to the logged-in customer.
     *
     * --- Why POST and not DELETE? ---
     * HTML forms only support GET and POST — there is no <form method="delete">.
     * Using POST /cancel/{id} is a common and acceptable workaround in web apps.
     * REST APIs would use DELETE /orders/{id}, but this is a form-based web app.
     *
     * --- Business rule (enforced in the service) ---
     * An order can only be cancelled if it is in PLACED or SCHEDULED status.
     * Once ASSIGNED or OUT_FOR_DELIVERY, it is too late — the agent is already moving.
     * The service also checks that this order actually belongs to this customer.
     *
     * --- How we communicate the result ---
     * We use a flash message:
     *   cancelOrder() returns Optional.of(order) → cancellation succeeded
     *   cancelOrder() returns Optional.empty()   → cancellation was blocked
     *
     * @param orderId            the ID of the order to cancel (from the URL path)
     * @param session            to get the logged-in customer
     * @param redirectAttributes to pass the result as a flash message
     * @return redirect to the orders page
     */
    @PostMapping("/customer/cancel/{id}")
    public String cancelOrder(
            @PathVariable("id") Long orderId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        // --- Session check ---
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null || loggedInUser.getRole() != UserRole.CUSTOMER) {
            return "redirect:/login";
        }

        // --- Attempt the cancellation ---
        // cancelOrder() returns an Optional:
        //   Optional.of(order)  → success (order was cancelled)
        //   Optional.empty()    → failure (order wasn't found, wrong owner, or too late)
        Optional<DeliveryRequest> result = deliveryBookingService.cancelOrder(orderId, loggedInUser);

        if (result.isPresent()) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Order #" + orderId + " has been cancelled.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Order #" + orderId + " could not be cancelled. " +
                    "It may already be assigned to a delivery agent.");
        }

        // Always redirect back to orders page regardless of outcome
        return "redirect:/customer/orders";
    }
}

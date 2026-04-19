package com.bt.deliveryapp.controller;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.enums.UserRole;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.TrackingEvent;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.service.TrackingService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

/**
 * TrackingController — the web layer for Feature 4: Live Status Tracking.
 *
 * --- What does a Controller do? ---
 * A controller is the bridge between the user's browser and your app's logic.
 *
 * When a user visits a URL (like /track/42), the browser sends a GET request.
 * Spring sees the URL, finds the matching @GetMapping method in this controller,
 * runs it, and returns an HTML page. That's it — the controller's only job is:
 *   1. Receive the request
 *   2. Ask the service for the data
 *   3. Hand the data to the HTML template (Thymeleaf)
 *   4. Return the template name
 *
 * The controller has NO business logic — no state machine, no DB queries directly.
 * All of that lives in TrackingService. This is the "Separation of Concerns" principle.
 *
 * --- Two endpoints ---
 * GET  /track/{orderId}         → Show the tracking page for an order
 * POST /track/{orderId}/update  → Agent submits a status change
 *
 * --- Session-based login ---
 * We use HttpSession to check who is logged in.
 * When a user logs in (LoginController, to be built later), a User object is stored
 * in the session under the key "loggedInUser". This controller reads that key.
 * If it's null, nobody is logged in → redirect to /login.
 *
 * --- This is Bhavya's controller (Feature 4: Live Status Tracking) ---
 */
@Controller
public class TrackingController {

    // Spring automatically creates and injects TrackingService — we never call "new"
    @Autowired
    private TrackingService trackingService;

    // =========================================================================
    // GET /track/{orderId} — Show the tracking page
    // =========================================================================

    /**
     * Handles: GET /track/42 (where 42 is the order ID)
     *
     * What this method does step by step:
     *   1. Checks the session — is anyone logged in?
     *   2. Fetches the order from the DB (via TrackingService → DeliveryRequestRepository)
     *   3. Fetches the full history of status changes (the tracking timeline)
     *   4. Calculates which status buttons to show the agent (using the state machine)
     *   5. Puts everything into the Model so Thymeleaf can use it in the HTML template
     *   6. Returns "tracking" — Spring loads tracking.html from /templates/tracking.html
     *
     * @param orderId  the order ID taken from the URL path (e.g. /track/42 → orderId = 42)
     * @param session  the HTTP session — holds "loggedInUser" if someone is logged in
     * @param model    the container we use to send data to the HTML template
     * @return         the name of the Thymeleaf template to render ("tracking")
     */
    @GetMapping("/track/{orderId}")
    public String showTrackingPage(@PathVariable Long orderId,
                                   HttpSession session,
                                   Model model) {

        // --- Step 1: Check the user is logged in ---
        // The login controller (to be built) stores the User in session when they log in.
        // If nobody is logged in, redirect them to the login page.
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        // Always pass the logged-in user to the template (for the navbar)
        model.addAttribute("loggedInUser", loggedInUser);

        // --- Step 2: Fetch the order ---
        // findById returns an Optional — a container that is either:
        //   - Present: the order was found in the DB
        //   - Empty:   no order with that ID exists
        Optional<DeliveryRequest> orderOpt = trackingService.getOrderById(orderId);

        if (orderOpt.isEmpty()) {
            // Order doesn't exist — show an error on the tracking page
            model.addAttribute("errorMessage", "Order #" + orderId + " was not found.");
            return "tracking";
        }

        DeliveryRequest order = orderOpt.get();

        // --- Step 3: Fetch the tracking timeline ---
        // This is the full list of status changes, sorted oldest → newest.
        // Example: [PLACED→ASSIGNED at 10:00, ASSIGNED→OUT_FOR_DELIVERY at 11:30]
        // An empty list just means the order was placed but no agent has acted yet.
        List<TrackingEvent> timeline = trackingService.getTrackingTimeline(orderId);

        // --- Step 4: What status buttons should the agent see? ---
        // The state machine in TrackingService decides which transitions are valid.
        // Example: if status is ASSIGNED, only "Mark as Out for Delivery" is valid.
        // The template loops over this list and renders one button per entry.
        List<DeliveryStatusEnum> validNextStatuses = trackingService.getValidNextStatuses(order);

        // --- Step 5: Add everything to the model for the HTML template ---
        model.addAttribute("order", order);
        model.addAttribute("timeline", timeline);
        model.addAttribute("validNextStatuses", validNextStatuses);

        // isAgent = true if the logged-in user is an AGENT or ADMIN (both can update status)
        // isAgent is used in the template to decide whether to show the action panel
        boolean isAgent = loggedInUser.getRole() == UserRole.AGENT
                       || loggedInUser.getRole() == UserRole.ADMIN;
        model.addAttribute("isAgent", isAgent);

        // isOwner = true if the logged-in customer placed this order
        // Used to control whether a customer can even see this page
        boolean isOwner = order.getCustomer().getId().equals(loggedInUser.getId());
        model.addAttribute("isOwner", isOwner);

        return "tracking"; // → loads src/main/resources/templates/tracking.html
    }

    // =========================================================================
    // POST /track/{orderId}/update — Agent submits a status update
    // =========================================================================

    /**
     * Handles: POST /track/42/update (submitted by an agent clicking a button)
     *
     * --- The POST-Redirect-GET pattern (important concept!) ---
     * After a form submission (POST), we always redirect back to the tracking page (GET).
     * Why? If we returned HTML directly after a POST, refreshing the browser would
     * RE-SUBMIT the form — the status would change twice by accident.
     * By redirecting, a refresh just re-loads the GET page safely.
     *
     * --- Flash attributes ---
     * RedirectAttributes lets us pass a message ACROSS the redirect.
     * The successMessage or errorMessage is stored temporarily, survives the redirect,
     * and is then available in the GET request so the template can display it.
     *
     * @param orderId            the order being updated (from the URL)
     * @param newStatus          the status string from the form (e.g. "DELIVERED")
     * @param note               optional note from the agent (e.g. "Nobody home")
     * @param session            HTTP session — used to get the logged-in user
     * @param redirectAttributes lets us pass flash messages across the redirect
     * @return                   a redirect instruction back to the tracking page
     */
    @PostMapping("/track/{orderId}/update")
    public String updateStatus(@PathVariable Long orderId,
                               @RequestParam String newStatus,
                               @RequestParam(required = false) String note,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {

        // --- Step 1: Check login ---
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        // --- Step 2: Only agents and admins can update status ---
        // Customers can view the tracking page but cannot submit status changes.
        if (loggedInUser.getRole() != UserRole.AGENT
                && loggedInUser.getRole() != UserRole.ADMIN) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Only delivery agents can update order status.");
            return "redirect:/track/" + orderId;
        }

        try {
            // --- Step 3: Convert the status string into an enum value ---
            // The form sends "DELIVERED" as plain text. We convert it to the actual
            // DeliveryStatusEnum.DELIVERED value so Java can work with it.
            // If the string is invalid (e.g. typo), valueOf() throws IllegalArgumentException.
            DeliveryStatusEnum statusEnum = DeliveryStatusEnum.valueOf(newStatus);

            // --- Step 4: Let TrackingService do all the real work ---
            // This is where the state machine runs, the TrackingEvent is saved,
            // and the DeliveryRequest status is updated in the DB.
            trackingService.updateStatus(orderId, statusEnum, loggedInUser, note);

            // Success — tell the user what happened
            String displayName = statusEnum.name().replace("_", " ");
            redirectAttributes.addFlashAttribute("successMessage",
                    "✅ Order status updated to: " + displayName);

        } catch (IllegalArgumentException e) {
            // DeliveryStatusEnum.valueOf() failed — the status string wasn't valid
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Unknown status: " + newStatus + ". Please use the buttons on the tracking page.");

        } catch (IllegalStateException e) {
            // TrackingService rejected the transition — the state machine said no
            // e.g. trying to go PLACED → DELIVERED directly
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        // Always redirect back to the tracking page (POST-Redirect-GET)
        return "redirect:/track/" + orderId;
    }

    // =========================================================================
    // POST /track/{orderId}/arrive — Agent clicked "I've Arrived"
    // =========================================================================

    /**
     * Handles: POST /track/42/arrive
     *
     * This is called when the agent physically arrives at the customer's address
     * and clicks the "I've Arrived" button. It triggers OTP generation in
     * TrackingService and sets the order status to ARRIVED.
     *
     * After this, the customer's tracking page will display the OTP,
     * and the agent's page will show an OTP entry form.
     */
    @PostMapping("/track/{orderId}/arrive")
    public String agentArrived(@PathVariable Long orderId,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {

        // Check login
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        // Only agents and admins can do this
        if (loggedInUser.getRole() != UserRole.AGENT
                && loggedInUser.getRole() != UserRole.ADMIN) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Only delivery agents can mark an order as Arrived.");
            return "redirect:/track/" + orderId;
        }

        try {
            trackingService.arriveAndGenerateOtp(orderId, loggedInUser);
            redirectAttributes.addFlashAttribute("successMessage",
                    "✅ Marked as Arrived! An OTP has been generated and is now visible on the customer's tracking page.");
        } catch (Exception e) {
            // Catch ALL exceptions (including database errors) so we never show
            // a raw 500 page — always redirect back with a readable error message.
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Something went wrong: " + e.getMessage());
        }

        return "redirect:/track/" + orderId;
    }

    // =========================================================================
    // POST /track/{orderId}/confirm-otp — Agent entered the customer's OTP
    // =========================================================================

    /**
     * Handles: POST /track/42/confirm-otp
     *
     * Called when the agent submits the 4-digit OTP they got from the customer.
     * TrackingService validates the OTP and, if correct, marks the order DELIVERED.
     *
     * If the OTP is wrong, an error message is shown and the agent can try again.
     * The order stays in ARRIVED status until the correct OTP is entered or the
     * agent marks it as FAILED.
     *
     * @param otp  the 4-digit code entered by the agent (from the form input)
     */
    @PostMapping("/track/{orderId}/confirm-otp")
    public String confirmOtp(@PathVariable Long orderId,
                             @RequestParam String otp,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {

        // Check login
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        // Only agents and admins can confirm delivery
        if (loggedInUser.getRole() != UserRole.AGENT
                && loggedInUser.getRole() != UserRole.ADMIN) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Only delivery agents can confirm delivery.");
            return "redirect:/track/" + orderId;
        }

        try {
            trackingService.confirmDeliveryWithOtp(orderId, otp, loggedInUser);
            redirectAttributes.addFlashAttribute("successMessage",
                    "✅ OTP confirmed! Order has been marked as Delivered.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Something went wrong: " + e.getMessage());
        }

        return "redirect:/track/" + orderId;
    }
}

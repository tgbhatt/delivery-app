package com.bt.deliveryapp.controller;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.enums.UserRole;
import com.bt.deliveryapp.model.Agent;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.repository.AgentRepository;
import com.bt.deliveryapp.service.DashboardService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

/**
 * DashboardController — the web layer for Feature 5: Admin & Agent Dashboard.
 *
 * --- What does a Controller do? ---
 * It receives HTTP requests from the browser, asks the service for data,
 * puts that data into the Model (so Thymeleaf can use it in HTML), and
 * returns the name of the template to render.
 *
 * The controller has no business logic — no filtering, no counting, no DB queries.
 * All of that lives in DashboardService. This is the Separation of Concerns principle.
 *
 * --- Three endpoints ---
 * GET  /admin/dashboard              → Admin view (with optional filter params)
 * GET  /agent/dashboard              → Agent's personal view
 * POST /admin/assign                 → Admin assigns an agent to an order
 *
 * --- Role-based access ---
 * Admin dashboard: only accessible by users with role ADMIN
 * Agent dashboard: only accessible by users with role AGENT
 * If the wrong role tries to access a page, they are redirected to /login
 *
 * --- Session-based login ---
 * Same pattern as TrackingController — we read "loggedInUser" from HttpSession.
 * This is set by LoginController (to be built) when a user logs in successfully.
 *
 * --- This is Bhavya's controller (Feature 5: Admin/Agent Dashboard) ---
 */
@Controller
public class DashboardController {

    // Spring auto-injects both beans — we never call "new"
    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private AgentRepository agentRepository;

    // =========================================================================
    // GET /admin/dashboard — Admin view
    // =========================================================================

    /**
     * Handles: GET /admin/dashboard
     * Also handles: GET /admin/dashboard?status=PLACED&isImmediate=true&agentId=2
     *
     * The three @RequestParam fields are all optional (required = false).
     * When the admin first loads the page, they are all null → show everything.
     * When the admin applies a filter, they are filled in → show filtered results.
     *
     * What this method does step by step:
     *   1. Check the session — is an admin logged in?
     *   2. Decide whether to apply filters or show the default unfiltered view
     *   3. Get live orders and scheduled orders (either filtered or default)
     *   4. Get summary counts (always unfiltered — they show the real totals)
     *   5. Get all agents (for the filter dropdown and assign forms)
     *   6. Put everything in the Model and return "admin-dashboard"
     *
     * @param status      optional filter: e.g. "PLACED", "ASSIGNED" (null = no filter)
     * @param isImmediate optional filter: true = immediate only, false = scheduled only
     * @param agentId     optional filter: agent ID to filter by (null = all agents)
     * @param session     HTTP session — holds "loggedInUser" if logged in
     * @param model       the container we use to send data to the HTML template
     * @return            the name of the Thymeleaf template to render
     */
    @GetMapping("/admin/dashboard")
    public String showAdminDashboard(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean isImmediate,
            @RequestParam(required = false) Long agentId,
            HttpSession session,
            Model model) {

        // --- Step 1: Check login and role ---
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }
        if (loggedInUser.getRole() != UserRole.ADMIN) {
            // Only admins can see this page — redirect anyone else
            return "redirect:/login";
        }

        model.addAttribute("loggedInUser", loggedInUser);

        // --- Step 2 & 3: Build the order lists ---
        // If any filter parameter is provided, use the filter method.
        // Otherwise, use the clean default methods (getLiveOrders / getScheduledOrders).
        boolean filterApplied = (status != null && !status.isEmpty())
                || isImmediate != null
                || agentId != null;

        List<DeliveryRequest> liveOrders;
        List<DeliveryRequest> scheduledOrders;

        if (filterApplied) {
            // Filter was applied — run the combined filter, then split by type
            // so each panel still shows only its own order type
            List<DeliveryRequest> filtered = dashboardService.filterOrders(status, isImmediate, agentId);
            liveOrders = filtered.stream()
                    .filter(DeliveryRequest::isImmediate)
                    .collect(java.util.stream.Collectors.toList());
            scheduledOrders = filtered.stream()
                    .filter(o -> !o.isImmediate())
                    .collect(java.util.stream.Collectors.toList());
        } else {
            // No filter — show all active orders split by type
            liveOrders = dashboardService.getLiveOrders();
            scheduledOrders = dashboardService.getScheduledOrders();
        }

        // --- Step 4: Summary counts (always show real totals — not filtered) ---
        model.addAttribute("totalActive", dashboardService.countTotalActive());
        model.addAttribute("deliveredToday", dashboardService.countDeliveredToday());
        model.addAttribute("failedToday", dashboardService.countFailedToday());
        model.addAttribute("pendingAssignment", dashboardService.countPendingAssignment());

        // --- Step 5: Agent list for dropdowns ---
        model.addAttribute("allAgents", dashboardService.getAllAgents());
        model.addAttribute("availableAgents", dashboardService.getAvailableAgents());

        // --- Step 6: Order lists for the two panels ---
        model.addAttribute("liveOrders", liveOrders);
        model.addAttribute("scheduledOrders", scheduledOrders);

        // Pass all statuses to the filter dropdown so the admin can pick one
        model.addAttribute("allStatuses", DeliveryStatusEnum.values());

        // Pass the current filter values back to the template
        // so the form can show what's currently selected
        model.addAttribute("filterStatus", status);
        model.addAttribute("filterIsImmediate", isImmediate);
        model.addAttribute("filterAgentId", agentId);
        model.addAttribute("filterApplied", filterApplied);

        return "admin-dashboard"; // → loads src/main/resources/templates/admin-dashboard.html
    }

    // =========================================================================
    // POST /admin/assign — Admin assigns an agent to an order
    // =========================================================================

    /**
     * Handles: POST /admin/assign (form submission from the admin dashboard)
     *
     * The form on the dashboard sends two fields:
     *   orderId  — the order being assigned
     *   agentId  — the agent being assigned to it
     *
     * Uses POST-Redirect-GET: after the assignment, we redirect back to the
     * admin dashboard. This means refreshing won't accidentally re-assign.
     *
     * @param orderId             the order to assign an agent to
     * @param agentId             the agent to assign
     * @param session             HTTP session — confirms admin is logged in
     * @param redirectAttributes  lets us pass a flash message across the redirect
     * @return                    redirect back to the admin dashboard
     */
    @PostMapping("/admin/assign")
    public String assignAgent(@RequestParam Long orderId,
                              @RequestParam Long agentId,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {

        // Check login and role
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null || loggedInUser.getRole() != UserRole.ADMIN) {
            return "redirect:/login";
        }

        try {
            // Ask the service to do the actual assignment
            dashboardService.assignAgentToOrder(orderId, agentId);

            // Find the agent's name to use in the success message
            // We re-fetch so we can show a proper name rather than just the ID
            Optional<Agent> agentOpt = agentRepository.findById(agentId);
            String agentName = agentOpt.map(a -> a.getUser().getName()).orElse("Agent #" + agentId);

            redirectAttributes.addFlashAttribute("successMessage",
                    "✅ Order #" + orderId + " assigned to " + agentName + ".");

        } catch (IllegalArgumentException e) {
            // Order or agent not found
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        // Always redirect back to the admin dashboard (POST-Redirect-GET)
        return "redirect:/admin/dashboard";
    }

    // =========================================================================
    // GET /agent/dashboard — Agent's personal view
    // =========================================================================

    /**
     * Handles: GET /agent/dashboard
     *
     * Shows the logged-in agent their personal workload:
     *   - "Right Now": orders they are currently handling (ASSIGNED or OUT_FOR_DELIVERY)
     *   - "Today's Schedule": scheduled orders assigned to them, sorted by time slot
     *
     * What this method does step by step:
     *   1. Check the session — is an agent logged in?
     *   2. Look up their Agent profile (Agent is different from User — see Agent.java)
     *   3. Get their current active orders
     *   4. Get their upcoming scheduled orders
     *   5. Put everything in the Model and return "agent-dashboard"
     *
     * @param session  HTTP session — holds "loggedInUser" if logged in
     * @param model    the container we use to send data to the HTML template
     * @return         the name of the Thymeleaf template to render
     */
    @GetMapping("/agent/dashboard")
    public String showAgentDashboard(HttpSession session, Model model) {

        // --- Step 1: Check login and role ---
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }
        if (loggedInUser.getRole() != UserRole.AGENT) {
            // Only agents can see this page
            return "redirect:/login";
        }

        model.addAttribute("loggedInUser", loggedInUser);

        // --- Step 2: Find the Agent profile for this user ---
        // User = login/identity (email, password, role)
        // Agent = operational profile (availability, delivery count)
        // Each agent User has exactly one Agent profile — we fetch it here.
        Optional<Agent> agentOpt = agentRepository.findByUser(loggedInUser);

        if (agentOpt.isEmpty()) {
            // The logged-in user has AGENT role but no Agent profile exists yet
            // This shouldn't happen in normal use but we handle it gracefully
            model.addAttribute("errorMessage",
                    "No agent profile found for your account. Please contact an admin.");
            return "agent-dashboard";
        }

        Agent agent = agentOpt.get();
        model.addAttribute("agent", agent);

        // --- Step 3: Get their active orders (Right Now section) ---
        // These are orders with status ASSIGNED or OUT_FOR_DELIVERY
        List<DeliveryRequest> currentOrders = dashboardService.getAgentCurrentOrders(agent);
        model.addAttribute("currentOrders", currentOrders);

        // --- Step 4: Get their scheduled orders (Today's Schedule section) ---
        // These are SCHEDULED orders assigned to this agent, sorted by time slot
        List<DeliveryRequest> scheduledOrders = dashboardService.getAgentScheduledOrders(agent);
        model.addAttribute("scheduledOrders", scheduledOrders);

        return "agent-dashboard"; // → loads src/main/resources/templates/agent-dashboard.html
    }
}

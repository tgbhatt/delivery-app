package com.bt.deliveryapp.controller;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.enums.UserRole;
import com.bt.deliveryapp.model.Agent;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.repository.AgentRepository;
import com.bt.deliveryapp.service.DashboardService;
import com.bt.deliveryapp.service.RouteOptimisationService;
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

    // Spring auto-injects all beans — we never call "new"
    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private RouteOptimisationService routeOptimisationService;

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
            @RequestParam(required = false) String zone,
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
                || agentId != null
                || (zone != null && !zone.isEmpty());

        List<DeliveryRequest> liveOrders;
        List<DeliveryRequest> scheduledOrders;

        if (filterApplied) {
            // Filter was applied — run the combined filter, then split by type
            // so each panel still shows only its own order type
            List<DeliveryRequest> filtered = dashboardService.filterOrders(status, isImmediate, agentId, zone);
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
        model.addAttribute("filterZone", zone);
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
    // POST /admin/create-agent — Admin creates a new delivery agent
    // =========================================================================

    /**
     * Handles: POST /admin/create-agent (form submission from the admin dashboard)
     *
     * The admin fills in the "Create Agent" form with: name, email, password, phone, zone.
     * This method asks the DashboardService to:
     *   1. Create a User account with role AGENT
     *   2. Create an Agent profile linked to that User
     *
     * --- Why are the fields @RequestParam? ---
     * The form sends the data as URL-encoded form fields (the standard way HTML forms work).
     * @RequestParam tells Spring to pull each field from the submitted form data.
     *
     * --- POST-Redirect-GET ---
     * After creating the agent, we redirect back to /admin/dashboard instead of
     * rendering a page directly. This prevents the form from being re-submitted
     * if the admin refreshes their browser.
     *
     * @param name               the agent's full name (required)
     * @param email              their login email (required, must be unique)
     * @param password           their login password (required)
     * @param phone              their phone number (optional)
     * @param zone               their delivery zone (optional, e.g. "NORTH")
     * @param session            HTTP session — confirms admin is logged in
     * @param redirectAttributes for passing flash messages across the redirect
     * @return redirect back to the admin dashboard
     */
    @PostMapping("/admin/create-agent")
    public String createAgent(
            @RequestParam("agentName")     String name,
            @RequestParam("agentEmail")    String email,
            @RequestParam("agentPassword") String password,
            @RequestParam(value = "agentPhone", required = false) String phone,
            @RequestParam(value = "agentZone",  required = false) String zone,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        // --- Check login and role ---
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null || loggedInUser.getRole() != UserRole.ADMIN) {
            return "redirect:/login";
        }

        try {
            // Ask the service to create the User + Agent pair
            Agent newAgent = dashboardService.createAgent(name, email, password, phone, zone);

            // Success! Show a confirmation message with the agent's name
            redirectAttributes.addFlashAttribute("successMessage",
                    "Agent '" + newAgent.getUser().getName() + "' has been created successfully! "
                    + "They can now log in with: " + email);

        } catch (IllegalArgumentException e) {
            // Validation error (empty field, duplicate email, etc.)
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            // Unexpected error — show details for debugging
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Could not create agent: " + e.getMessage());
        }

        // Redirect to the Agents page (not the main dashboard)
        return "redirect:/admin/agents";
    }

    // =========================================================================
    // GET /admin/agents — Dedicated Agents management page
    // =========================================================================

    /**
     * Handles: GET /admin/agents
     *
     * Shows a dedicated page listing all delivery agents in the system,
     * with their details (name, email, zone, availability, active deliveries).
     * Also contains the "Create Agent" form and a "Delete" button per agent.
     *
     * @param session  HTTP session — confirms admin is logged in
     * @param model    the container we use to send data to the HTML template
     * @return         the name of the Thymeleaf template to render
     */
    @GetMapping("/admin/agents")
    public String showAgentsPage(HttpSession session, Model model) {

        // --- Check login and role ---
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null || loggedInUser.getRole() != UserRole.ADMIN) {
            return "redirect:/login";
        }

        model.addAttribute("loggedInUser", loggedInUser);

        // Pass all agents to the page for display in the table
        model.addAttribute("allAgents", dashboardService.getAllAgents());

        return "admin-agents"; // → loads src/main/resources/templates/admin-agents.html
    }

    // =========================================================================
    // POST /admin/delete-agent/{agentId} — Delete a delivery agent
    // =========================================================================

    /**
     * Handles: POST /admin/delete-agent/{agentId}
     *
     * Deletes the agent profile and their linked User account.
     * The service will reject deletion if the agent has active orders.
     *
     * Why POST and not DELETE?
     * HTML forms only support GET and POST — they can't send a DELETE request.
     * Using POST with a path variable is the standard workaround in web apps.
     *
     * --- POST-Redirect-GET ---
     * After deletion, we redirect back to /admin/agents with a flash message.
     *
     * @param agentId             the ID of the agent to delete (from URL path)
     * @param session             HTTP session — confirms admin is logged in
     * @param redirectAttributes  for passing flash messages across the redirect
     * @return redirect back to the agents page
     */
    @PostMapping("/admin/delete-agent/{agentId}")
    public String deleteAgent(@PathVariable Long agentId,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {

        // --- Check login and role ---
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null || loggedInUser.getRole() != UserRole.ADMIN) {
            return "redirect:/login";
        }

        try {
            dashboardService.deleteAgent(agentId);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Agent has been deleted successfully.");

        } catch (IllegalArgumentException e) {
            // Agent has active orders, or not found
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Could not delete agent: " + e.getMessage());
        }

        return "redirect:/admin/agents";
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

    // =========================================================================
    // POST /admin/run-auto-assign — Bulk auto-assign unassigned orders
    // =========================================================================

    /**
     * Handles: POST /admin/run-auto-assign
     *
     * Triggered by the "Run Auto-Assign" button on the admin dashboard.
     * Finds every order that currently has no agent assigned (in PLACED or
     * SCHEDULED status) and uses RouteOptimisationService to assign the best
     * available agent to each one.
     *
     * --- Why is this useful? ---
     * Auto-assign normally runs at booking time. But orders placed before
     * auto-assign was set up (or orders that were skipped because no agents
     * were available at that moment) stay unassigned. This button is a
     * manual "catch-up" that processes all of them in one click.
     *
     * --- POST-Redirect-GET pattern ---
     * After running, we redirect back to /admin/dashboard with a flash message
     * telling the admin how many orders were assigned.
     */
    @PostMapping("/admin/run-auto-assign")
    public String runAutoAssign(HttpSession session, RedirectAttributes redirectAttributes) {

        // --- Check login and role ---
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null || loggedInUser.getRole() != UserRole.ADMIN) {
            return "redirect:/login";
        }

        try {
            // Run the bulk assignment — returns how many orders were assigned
            int count = routeOptimisationService.runAutoAssignAll();

            if (count == 0) {
                // No orders were assigned — either all already assigned, or no agents available
                redirectAttributes.addFlashAttribute("successMessage",
                        "Auto-assign ran successfully — no unassigned orders found, or no agents are available.");
            } else {
                redirectAttributes.addFlashAttribute("successMessage",
                        "✅ Auto-assign complete! " + count + " order" + (count == 1 ? "" : "s") + " assigned.");
            }

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Auto-assign failed: " + e.getMessage());
        }

        return "redirect:/admin/dashboard";
    }
}

package com.bt.deliveryapp.controller;

import com.bt.deliveryapp.enums.UserRole;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.TimeSlot;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.service.DeliveryBookingService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * CustomerController — handles all web pages that belong to the customer.
 *
 * Routes:
 *   GET  /customer/orders  → Show the customer's order list
 *   GET  /customer/book    → Show the booking form
 *   POST /customer/book    → Submit a new delivery booking
 *
 * All routes require the user to be logged in with the CUSTOMER role.
 */
@Controller
@RequestMapping("/customer")
public class CustomerController {

    @Autowired
    private DeliveryBookingService deliveryBookingService;

    // =========================================================================
    // GET /customer/orders — Customer's order list
    // =========================================================================

    /**
     * Shows all orders placed by the logged-in customer.
     * The template at customer/orders.html displays these as cards.
     */
    @GetMapping("/orders")
    public String showOrders(HttpSession session, Model model) {

        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }
        if (loggedInUser.getRole() != UserRole.CUSTOMER) {
            return "redirect:/login";
        }

        model.addAttribute("loggedInUser", loggedInUser);

        // Fetch all orders placed by this customer, newest first
        List<DeliveryRequest> orders = deliveryBookingService.getOrdersForCustomer(loggedInUser);
        model.addAttribute("orders", orders);

        return "customer/orders"; // → templates/customer/orders.html
    }

    // =========================================================================
    // GET /customer/book — Show booking form
    // =========================================================================

    /**
     * Shows the multi-step booking wizard.
     * Passes today's available time slots so the customer can pick one.
     */
    @GetMapping("/book")
    public String showBookingForm(HttpSession session, Model model) {

        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }
        if (loggedInUser.getRole() != UserRole.CUSTOMER) {
            return "redirect:/login";
        }

        model.addAttribute("loggedInUser", loggedInUser);

        // Pass all upcoming available slots so the customer can pick one
        List<TimeSlot> availableSlots = deliveryBookingService.getUpcomingAvailableSlots();
        model.addAttribute("availableSlots", availableSlots);

        return "customer/book"; // → templates/customer/book.html
    }

    // =========================================================================
    // POST /customer/book — Process booking form submission
    // =========================================================================

    /**
     * Processes the booking form.
     * Depending on whether the customer picked "immediate" or "scheduled",
     * calls the appropriate service method.
     *
     * Form fields sent from book.html:
     *   pickupAddress       — restaurant/pickup address
     *   deliveryAddress     — customer's delivery address
     *   packageDescription  — description of what's being delivered
     *   isImmediate         — "true" for immediate, "false" for scheduled
     *   priority            — HIGH, MEDIUM, or LOW
     *   specialInstructions — optional delivery notes
     *   timeSlotId          — only present for scheduled orders
     */
    @PostMapping("/book")
    public String processBooking(
            @RequestParam String pickupAddress,
            @RequestParam String deliveryAddress,
            @RequestParam String packageDescription,
            @RequestParam String isImmediate,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String specialInstructions,
            @RequestParam(required = false) Long timeSlotId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            return "redirect:/login";
        }
        if (loggedInUser.getRole() != UserRole.CUSTOMER) {
            return "redirect:/login";
        }

        try {
            if ("true".equals(isImmediate)) {
                // Order Now path
                deliveryBookingService.placeImmediateOrder(
                        loggedInUser, pickupAddress, deliveryAddress, packageDescription,
                        priority, specialInstructions);
                redirectAttributes.addFlashAttribute("successMessage",
                        "✅ Your immediate delivery has been placed! We'll assign an agent shortly.");
            } else {
                // Scheduled path — timeSlotId is required
                if (timeSlotId == null) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Please select a time slot for your scheduled delivery.");
                    return "redirect:/customer/book";
                }
                deliveryBookingService.placeScheduledOrder(
                        loggedInUser, pickupAddress, deliveryAddress, packageDescription,
                        priority, specialInstructions, timeSlotId);
                redirectAttributes.addFlashAttribute("successMessage",
                        "✅ Your delivery has been scheduled! You can track it from your orders.");
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/customer/book";
        }

        // Redirect to orders page so the customer can see their new order
        return "redirect:/customer/orders";
    }
}

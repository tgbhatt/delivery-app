package com.bt.deliveryapp.controller;

import com.bt.deliveryapp.enums.DeliveryStatusEnum;
import com.bt.deliveryapp.enums.Priority;
import com.bt.deliveryapp.enums.RecurrenceFrequency;
import com.bt.deliveryapp.enums.UserRole;
import com.bt.deliveryapp.model.DeliveryRequest;
import com.bt.deliveryapp.model.TimeSlot;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.service.DeliveryBookingService;
import com.bt.deliveryapp.service.RecurringDeliveryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/customer")
public class CustomerController {

    @Autowired
    private DeliveryBookingService deliveryBookingService;

    @Autowired
    private RecurringDeliveryService recurringDeliveryService;

    private static final List<DeliveryStatusEnum> ACTIVE_STATUSES = Arrays.asList(
            DeliveryStatusEnum.PLACED,
            DeliveryStatusEnum.SCHEDULED,
            DeliveryStatusEnum.ASSIGNED,
            DeliveryStatusEnum.OUT_FOR_DELIVERY,
            DeliveryStatusEnum.ARRIVED
    );

    private static final List<DeliveryStatusEnum> PAST_STATUSES = Arrays.asList(
            DeliveryStatusEnum.DELIVERED,
            DeliveryStatusEnum.FAILED,
            DeliveryStatusEnum.RESCHEDULED
    );

    // ── Active orders ────────────────────────────────────────────────────────

    @GetMapping("/orders")
    public String showOrders(HttpSession session, Model model) {
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null || loggedInUser.getRole() != UserRole.CUSTOMER) {
            return "redirect:/login";
        }

        List<DeliveryRequest> activeOrders = deliveryBookingService.getOrdersForCustomer(loggedInUser)
                .stream()
                .filter(o -> ACTIVE_STATUSES.contains(o.getCurrentStatus()))
                .collect(Collectors.toList());

        model.addAttribute("loggedInUser", loggedInUser);
        model.addAttribute("orders", activeOrders);
        return "customer/orders";
    }

    // ── Past / completed orders ──────────────────────────────────────────────

    @GetMapping("/past-orders")
    public String showPastOrders(HttpSession session, Model model) {
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null || loggedInUser.getRole() != UserRole.CUSTOMER) {
            return "redirect:/login";
        }

        List<DeliveryRequest> pastOrders = deliveryBookingService.getOrdersForCustomer(loggedInUser)
                .stream()
                .filter(o -> PAST_STATUSES.contains(o.getCurrentStatus()))
                .collect(Collectors.toList());

        model.addAttribute("loggedInUser", loggedInUser);
        model.addAttribute("orders", pastOrders);
        return "customer/past-orders";
    }

    // ── Booking form ─────────────────────────────────────────────────────────

    @GetMapping("/book")
    public String showBookingForm(HttpSession session, Model model) {
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null || loggedInUser.getRole() != UserRole.CUSTOMER) {
            return "redirect:/login";
        }

        model.addAttribute("loggedInUser", loggedInUser);
        model.addAttribute("availableSlots", buildAvailableSlots());
        model.addAttribute("prefill", null);
        return "customer/book";
    }

    // ── Book Again (pre-fill form from a past order) ─────────────────────────

    @GetMapping("/book-again/{orderId}")
    public String bookAgain(@PathVariable("orderId") Long orderId,
                            HttpSession session,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null || loggedInUser.getRole() != UserRole.CUSTOMER) {
            return "redirect:/login";
        }

        Optional<DeliveryRequest> orderOpt = deliveryBookingService.getOrderById(orderId, loggedInUser);
        if (orderOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Order not found.");
            return "redirect:/customer/past-orders";
        }

        model.addAttribute("loggedInUser", loggedInUser);
        model.addAttribute("availableSlots", buildAvailableSlots());
        model.addAttribute("prefill", orderOpt.get());
        model.addAttribute("prefillMessage", "Pre-filled from Order #" + orderId + ". Just pick a new time slot and confirm.");
        return "customer/book";
    }

    // ── Place order ──────────────────────────────────────────────────────────

    @PostMapping("/book")
    public String placeOrder(
            @RequestParam("pickupAddress") String pickupAddress,
            @RequestParam("pickupZone") String pickupZone,
            @RequestParam("deliveryAddress") String deliveryAddress,
            @RequestParam("packageDescription") String packageDescription,
            @RequestParam(value = "priority", required = false, defaultValue = "MEDIUM") String priorityStr,
            @RequestParam(value = "specialInstructions", required = false) String specialInstructions,
            @RequestParam("isImmediate") String isImmediate,
            @RequestParam(value = "timeSlotId", required = false) Long timeSlotId,
            @RequestParam(value = "recurringEnabled", required = false) String recurringEnabledStr,
            @RequestParam(value = "recurrenceFrequency", required = false) String recurrenceFrequencyStr,
            @RequestParam(value = "recurrenceIntervalDays", required = false) Integer recurrenceIntervalDays,
            @RequestParam(value = "recurrenceDaysOfWeek", required = false) List<String> recurrenceDaysOfWeek,
            @RequestParam(value = "recurrenceDayOfMonth", required = false) Integer recurrenceDayOfMonth,
            @RequestParam(value = "recurrenceEndDate", required = false) String recurrenceEndDateStr,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null || loggedInUser.getRole() != UserRole.CUSTOMER) {
            return "redirect:/login";
        }

        try {
            boolean immediate = "true".equalsIgnoreCase(isImmediate);
            boolean recurringEnabled = "on".equals(recurringEnabledStr);

            Priority priority;
            try {
                priority = Priority.valueOf(priorityStr.toUpperCase());
            } catch (Exception e) {
                priority = immediate ? Priority.HIGH : Priority.MEDIUM;
            }

            DeliveryRequest savedOrder;

            if (immediate) {
                savedOrder = deliveryBookingService.placeImmediateOrder(
                        loggedInUser, pickupAddress, pickupZone,
                        deliveryAddress, packageDescription, priority, specialInstructions);
            } else {
                if (timeSlotId == null) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Please choose a time slot for your scheduled delivery.");
                    return "redirect:/customer/book";
                }
                savedOrder = deliveryBookingService.placeScheduledOrder(
                        loggedInUser, pickupAddress, pickupZone,
                        deliveryAddress, packageDescription, timeSlotId, priority, specialInstructions);

                if (recurringEnabled && recurrenceFrequencyStr != null && !recurrenceFrequencyStr.isBlank()) {
                    try {
                        RecurrenceFrequency frequency = RecurrenceFrequency.valueOf(recurrenceFrequencyStr);
                        LocalDate endDate = null;
                        if (recurrenceEndDateStr != null && !recurrenceEndDateStr.isBlank()) {
                            endDate = LocalDate.parse(recurrenceEndDateStr);
                        }
                        String daysOfWeekStr = null;
                        if (recurrenceDaysOfWeek != null && !recurrenceDaysOfWeek.isEmpty()) {
                            daysOfWeekStr = String.join(",", recurrenceDaysOfWeek);
                        }
                        recurringDeliveryService.createRecurring(
                                loggedInUser, pickupAddress, pickupZone, deliveryAddress,
                                packageDescription, specialInstructions, priority, frequency,
                                recurrenceIntervalDays, daysOfWeekStr, recurrenceDayOfMonth,
                                savedOrder.getTimeSlot().getSlotDate(), endDate);
                    } catch (Exception e) {
                        redirectAttributes.addFlashAttribute("successMessage",
                                "Delivery booked! Note: recurring schedule could not be set up — " + e.getMessage());
                        return "redirect:/customer/orders";
                    }
                }
            }

            redirectAttributes.addFlashAttribute("successMessage",
                    recurringEnabled
                            ? "Delivery booked and recurring schedule created!"
                            : "Your delivery has been booked successfully!");
            return "redirect:/customer/orders";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Could not place your order: " + e.getMessage());
            return "redirect:/customer/book";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "An unexpected error occurred: " + e.getMessage());
            return "redirect:/customer/book";
        }
    }

    // ── Cancel order ─────────────────────────────────────────────────────────

    @PostMapping("/cancel/{id}")
    public String cancelOrder(
            @PathVariable("id") Long orderId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null || loggedInUser.getRole() != UserRole.CUSTOMER) {
            return "redirect:/login";
        }

        Optional<DeliveryRequest> result = deliveryBookingService.cancelOrder(orderId, loggedInUser);
        if (result.isPresent()) {
            redirectAttributes.addFlashAttribute("successMessage", "Order #" + orderId + " has been cancelled.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Order #" + orderId + " could not be cancelled. It may already be assigned to a delivery agent.");
        }
        return "redirect:/customer/orders";
    }

    // ── Recurring schedules ──────────────────────────────────────────────────

    @GetMapping("/recurring")
    public String showRecurring(HttpSession session, Model model) {
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null || loggedInUser.getRole() != UserRole.CUSTOMER) {
            return "redirect:/login";
        }
        model.addAttribute("loggedInUser", loggedInUser);
        model.addAttribute("schedules", recurringDeliveryService.getRecurringForCustomer(loggedInUser));
        return "customer/recurring";
    }

    @PostMapping("/recurring/pause/{id}")
    public String pauseRecurring(@PathVariable("id") Long id, HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null || user.getRole() != UserRole.CUSTOMER) return "redirect:/login";
        boolean ok = recurringDeliveryService.pauseRecurring(id, user);
        redirectAttributes.addFlashAttribute(ok ? "successMessage" : "errorMessage",
                ok ? "Schedule paused." : "Could not pause schedule.");
        return "redirect:/customer/recurring";
    }

    @PostMapping("/recurring/resume/{id}")
    public String resumeRecurring(@PathVariable("id") Long id, HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null || user.getRole() != UserRole.CUSTOMER) return "redirect:/login";
        boolean ok = recurringDeliveryService.resumeRecurring(id, user);
        redirectAttributes.addFlashAttribute(ok ? "successMessage" : "errorMessage",
                ok ? "Schedule resumed." : "Could not resume — schedule may have passed its end date.");
        return "redirect:/customer/recurring";
    }

    @PostMapping("/recurring/cancel/{id}")
    public String cancelRecurring(@PathVariable("id") Long id, HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null || user.getRole() != UserRole.CUSTOMER) return "redirect:/login";
        boolean ok = recurringDeliveryService.cancelRecurring(id, user);
        redirectAttributes.addFlashAttribute(ok ? "successMessage" : "errorMessage",
                ok ? "Recurring schedule cancelled." : "Could not cancel schedule.");
        return "redirect:/customer/recurring";
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private List<TimeSlot> buildAvailableSlots() {
        LocalDate today = LocalDate.now();
        LocalDate upTo = today.plusDays(7);
        LocalTime now = LocalTime.now();
        LocalTime cutoff = now.plusHours(1);
        boolean midnightWrapped = cutoff.isBefore(now);

        // Single DB query for the entire date range — avoids 7 separate round trips
        return deliveryBookingService.getAvailableSlotsFrom(today).stream()
                .filter(s -> !s.getSlotDate().isAfter(upTo))
                .filter(s -> {
                    if (!s.getSlotDate().equals(today)) return true;
                    if (midnightWrapped) return false;
                    return s.getStartTime().isAfter(cutoff);
                })
                .collect(Collectors.toList());
    }
}

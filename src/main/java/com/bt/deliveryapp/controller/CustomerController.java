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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/customer")
public class CustomerController {

    @Autowired
    private DeliveryBookingService deliveryBookingService;

    @GetMapping("/orders")
    public String showOrders(HttpSession session, Model model) {
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null || loggedInUser.getRole() != UserRole.CUSTOMER) {
            return "redirect:/login";
        }

        List<DeliveryRequest> orders = deliveryBookingService.getOrdersForCustomer(loggedInUser);
        model.addAttribute("loggedInUser", loggedInUser);
        model.addAttribute("orders", orders);
        return "customer/orders";
    }

    @GetMapping("/book")
    public String showBookingForm(HttpSession session, Model model) {
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null || loggedInUser.getRole() != UserRole.CUSTOMER) {
            return "redirect:/login";
        }

        List<TimeSlot> availableSlots = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int daysAhead = 0; daysAhead < 7; daysAhead++) {
            availableSlots.addAll(deliveryBookingService.getAvailableSlots(today.plusDays(daysAhead)));
        }

        model.addAttribute("loggedInUser", loggedInUser);
        model.addAttribute("availableSlots", availableSlots);
        return "customer/book";
    }

    @PostMapping("/book")
    public String placeOrder(
            @RequestParam("pickupAddress") String pickupAddress,
            @RequestParam("pickupZone") String pickupZone,
            @RequestParam("deliveryAddress") String deliveryAddress,
            @RequestParam("packageDescription") String packageDescription,
            @RequestParam("isImmediate") String isImmediate,
            @RequestParam(value = "timeSlotId", required = false) Long timeSlotId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser == null || loggedInUser.getRole() != UserRole.CUSTOMER) {
            return "redirect:/login";
        }

        try {
            boolean immediate = "true".equalsIgnoreCase(isImmediate);

            if (immediate) {
                deliveryBookingService.placeImmediateOrder(
                        loggedInUser,
                        pickupAddress,
                        pickupZone,
                        deliveryAddress,
                        packageDescription
                );
            } else {
                if (timeSlotId == null) {
                    redirectAttributes.addFlashAttribute(
                            "errorMessage",
                            "Please choose a time slot for your scheduled delivery."
                    );
                    return "redirect:/customer/book";
                }

                deliveryBookingService.placeScheduledOrder(
                        loggedInUser,
                        pickupAddress,
                        pickupZone,
                        deliveryAddress,
                        packageDescription,
                        timeSlotId
                );
            }

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Your delivery has been booked successfully!"
            );
            return "redirect:/customer/orders";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Could not place your order: " + e.getMessage()
            );
            return "redirect:/customer/book";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "An unexpected error occurred: " + e.getMessage()
            );
            return "redirect:/customer/book";
        }
    }

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
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Order #" + orderId + " could not be cancelled. It may already be assigned to a delivery agent."
            );
        }

        return "redirect:/customer/orders";
    }
}

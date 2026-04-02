package com.bt.deliveryapp.controller;

import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.service.LoginService;
import com.bt.deliveryapp.service.RegisterService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * RegisterController — handles the customer registration page.
 *
 * Two endpoints:
 *   GET  /register → show the registration form
 *   POST /register → process the form submission
 *
 * After successful registration, the user is automatically logged in
 * (we put them in the session) and redirected to /customer/orders
 * with a welcome message. This means they do not have to log in
 * separately right after creating their account — a better user experience.
 */
@Controller
public class RegisterController {

    private static final String SESSION_KEY = "loggedInUser";

    private final RegisterService registerService;

    public RegisterController(RegisterService registerService) {
        this.registerService = registerService;
    }

    /**
     * Shows the registration form.
     *
     * If the user is already logged in, redirect them away —
     * they don't need to register again.
     */
    @GetMapping("/register")
    public String showRegisterPage(HttpSession session) {

        // If already logged in, send them home (same pattern as LoginController)
        if (session.getAttribute(SESSION_KEY) != null) {
            return "redirect:/customer/orders";
        }

        // Show the registration page
        // Spring looks for: src/main/resources/templates/register.html
        return "register";
    }

    /**
     * Processes the registration form.
     *
     * On success:
     *   1. The new User is saved to the database
     *   2. The user is automatically logged in (session attribute set)
     *   3. A welcome flash message is added
     *   4. They are redirected to /customer/orders
     *
     * On failure (email taken, passwords don't match, etc.):
     *   1. The error message is added as a flash attribute
     *   2. The user is redirected back to /register
     *
     * @param name            from the "Full Name" field
     * @param email           from the "Email Address" field
     * @param phone           from the "Phone Number" field
     * @param password        from the "Password" field
     * @param confirmPassword from the "Confirm Password" field
     */
    @PostMapping("/register")
    public String processRegistration(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String phone,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            // Attempt to create the account — RegisterService validates and saves
            User newUser = registerService.register(name, email, phone, password, confirmPassword);

            // Auto-login: put the new user in the session immediately
            // They're now logged in — no need to go back to the login page
            session.setAttribute(SESSION_KEY, newUser);

            // Add a welcome flash message — /customer/orders will display this
            // as a green toast notification (Bhavya's toast component reads it)
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Welcome, " + newUser.getName() + "! Your account is ready."
            );

            // Redirect to the customer's home page
            return "redirect:/customer/orders";

        } catch (RuntimeException e) {
            // Something went wrong — send the error back to the form
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/register";
        }
    }
}

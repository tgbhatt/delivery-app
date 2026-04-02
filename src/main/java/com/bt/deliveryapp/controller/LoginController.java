package com.bt.deliveryapp.controller;

import com.bt.deliveryapp.enums.UserRole;
import com.bt.deliveryapp.model.User;
import com.bt.deliveryapp.service.LoginService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * LoginController — handles all web requests related to login and logout.
 *
 * --- What is a Controller? ---
 * A Controller is the "front door" of the web application. It sits between
 * the browser and the service layer:
 *   Browser → Controller → Service → Repository → Database
 *
 * The controller's job is:
 * 1. Receive the HTTP request from the browser (GET or POST)
 * 2. Call the appropriate service method
 * 3. Send a response back (either a page to display, or a redirect)
 *
 * The controller does NOT contain business logic — that lives in LoginService.
 *
 * --- What is @Controller? ---
 * @Controller tells Spring: "this class handles web requests."
 * Each method inside it maps to a URL using @GetMapping or @PostMapping.
 *
 * --- What is HttpSession? ---
 * HttpSession is a server-side storage that remembers data about a specific
 * user across multiple requests. Think of it like a locker assigned to each
 * browser tab. We use it to remember "who is logged in" between page loads.
 * The key we use is "loggedInUser" — this same key is used everywhere in the
 * app to check who is currently logged in.
 *
 * --- What is the POST-Redirect-GET pattern? ---
 * After processing a form (POST), we always REDIRECT to a new URL (GET)
 * instead of showing a page directly. This prevents the browser from
 * re-submitting the form if the user refreshes the page. Every form in this
 * app follows this pattern.
 */
@Controller
public class LoginController {

    // The session key used throughout the entire app to identify the logged-in user
    // It is declared as a constant so we never mistype it in different places
    private static final String SESSION_KEY = "loggedInUser";

    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    // =========================================================================
    // GET /login — show the login page
    // =========================================================================

    /**
     * Shows the login form.
     *
     * But first: if the user is ALREADY logged in (they have a session),
     * there's no point showing the login form. We redirect them straight to
     * their dashboard. This prevents logged-in users from accidentally
     * landing on the login page.
     *
     * @param session the current HTTP session for this browser
     * @return the name of the Thymeleaf template to render, or a redirect
     */
    @GetMapping("/login")
    public String showLoginPage(HttpSession session) {

        // Check if there's already a logged-in user in the session
        User loggedInUser = (User) session.getAttribute(SESSION_KEY);

        if (loggedInUser != null) {
            // User is already logged in — send them to their correct home page
            return redirectForRole(loggedInUser.getRole());
        }

        // No user in session — show the login page
        // Spring looks for a file at: src/main/resources/templates/login.html
        return "login";
    }

    // =========================================================================
    // POST /login — process the submitted login form
    // =========================================================================

    /**
     * Processes the login form submission.
     *
     * @RequestParam extracts the values the user typed into the form fields.
     * "email" and "password" match the 'name' attributes in login.html.
     *
     * RedirectAttributes lets us send a flash message to the next page.
     * A "flash attribute" is a piece of data that survives exactly ONE redirect
     * and then disappears. We use it to show error messages after a failed login.
     *
     * @param email              what the user typed in the email field
     * @param password           what the user typed in the password field
     * @param session            the current HTTP session
     * @param redirectAttributes used to send error message back to login page
     * @return a redirect to the correct dashboard, or back to /login on failure
     */
    @PostMapping("/login")
    public String processLogin(
            @RequestParam String email,
            @RequestParam String password,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            // Try to authenticate — LoginService checks email + password
            User user = loginService.authenticate(email, password);

            // Success: store the user in the session so every other page knows who is logged in
            // "loggedInUser" is the key — every controller in this app uses this exact key
            session.setAttribute(SESSION_KEY, user);

            // Redirect to the correct page based on their role
            return redirectForRole(user.getRole());

        } catch (RuntimeException e) {
            // Authentication failed — add the error message as a flash attribute
            // "errorMessage" is the name Thymeleaf uses in login.html to show the red box
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());

            // Redirect back to the login page (GET /login will pick up the flash message)
            return "redirect:/login";
        }
    }

    // =========================================================================
    // GET /logout — log the user out
    // =========================================================================

    /**
     * Logs the user out by destroying their session.
     *
     * session.invalidate() completely wipes the session — all stored data
     * including "loggedInUser" is deleted. The next request from this browser
     * will get a brand new, empty session.
     *
     * After logout we redirect to /login so the user sees the login page again.
     */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    // =========================================================================
    // PRIVATE HELPER
    // =========================================================================

    /**
     * Returns the correct redirect string for a given user role.
     *
     * This private helper is called in multiple places (showLoginPage and
     * processLogin both need it). Putting it in one method means if we ever
     * change a URL, we only change it here — not in every place it's used.
     * This is the DRY principle: Don't Repeat Yourself.
     *
     * @param role the UserRole of the logged-in user
     * @return a Spring redirect string like "redirect:/customer/orders"
     */
    private String redirectForRole(UserRole role) {
        return switch (role) {
            case CUSTOMER -> "redirect:/customer/orders";
            case AGENT    -> "redirect:/agent/dashboard";
            case ADMIN    -> "redirect:/admin/dashboard";
        };
    }
}

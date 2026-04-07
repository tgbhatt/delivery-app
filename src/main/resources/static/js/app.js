/* ============================================================
   DeliverIQ — app.js
   Interactions: animations, toasts, stepper, form helpers
   ============================================================ */

(function () {
  'use strict';

  /* ── 1. Page fade-in ─────────────────────────────────────── */
  document.addEventListener('DOMContentLoaded', function () {
    const page = document.querySelector('.diq-page');
    if (page) page.style.opacity = '1';
  });

  /* ── 2. Navbar scroll effect ─────────────────────────────── */
  (function initNavbar() {
    const navbar = document.querySelector('.diq-navbar');
    if (!navbar) return;

    function updateNavbar() {
      if (window.scrollY > 10) {
        navbar.classList.add('scrolled');
      } else {
        navbar.classList.remove('scrolled');
      }
    }

    window.addEventListener('scroll', updateNavbar, { passive: true });
    updateNavbar();
  })();

  /* ── 3. Toast Notification System ───────────────────────── */
  window.diqToast = (function () {
    let container = document.getElementById('diq-toast-container');

    function ensureContainer() {
      if (!container) {
        container = document.createElement('div');
        container.id = 'diq-toast-container';
        document.body.appendChild(container);
      }
    }

    function show(message, type = 'info', duration = 4000) {
      ensureContainer();

      const icons = {
        success: 'bi bi-check-circle-fill',
        error:   'bi bi-x-circle-fill',
        info:    'bi bi-info-circle-fill',
        warning: 'bi bi-exclamation-triangle-fill'
      };

      const toast = document.createElement('div');
      toast.className = 'diq-toast diq-toast-' + type;
      toast.innerHTML =
        '<i class="' + (icons[type] || icons.info) + ' toast-icon"></i>' +
        '<span class="toast-msg">' + escapeHtml(message) + '</span>';

      container.appendChild(toast);

      // Auto-dismiss
      const timer = setTimeout(() => dismiss(toast), duration);
      toast.addEventListener('click', () => { clearTimeout(timer); dismiss(toast); });

      return toast;
    }

    function dismiss(toast) {
      toast.classList.add('toast-out');
      toast.addEventListener('animationend', () => toast.remove(), { once: true });
    }

    function escapeHtml(str) {
      const d = document.createElement('div');
      d.textContent = str;
      return d.innerHTML;
    }

    return { show };
  })();

  /* ── 4. Auto-dismiss flash alerts ────────────────────────── */
  (function initAlerts() {
    document.querySelectorAll('.diq-alert[data-auto-dismiss]').forEach(function (el) {
      const delay = parseInt(el.dataset.autoDismiss, 10) || 5000;
      setTimeout(() => {
        el.style.transition = 'opacity 0.4s, transform 0.4s';
        el.style.opacity = '0';
        el.style.transform = 'translateY(-8px)';
        setTimeout(() => el.remove(), 400);
      }, delay);
    });

    // Close button
    document.querySelectorAll('.alert-close').forEach(function (btn) {
      btn.addEventListener('click', function () {
        const alert = btn.closest('.diq-alert');
        if (!alert) return;
        alert.style.transition = 'opacity 0.3s';
        alert.style.opacity = '0';
        setTimeout(() => alert.remove(), 300);
      });
    });
  })();

  /* ── 5. Password Visibility Toggle ──────────────────────── */
  window.togglePassword = function (btnEl) {
    const wrap  = btnEl.closest('.diq-input-wrap');
    const input = wrap ? wrap.querySelector('.diq-input') : null;
    const icon  = btnEl.querySelector('i');
    if (!input) return;

    if (input.type === 'password') {
      input.type = 'text';
      if (icon) { icon.classList.remove('bi-eye'); icon.classList.add('bi-eye-slash'); }
    } else {
      input.type = 'password';
      if (icon) { icon.classList.remove('bi-eye-slash'); icon.classList.add('bi-eye'); }
    }
  };

  /* ── 6. Login shake on error ─────────────────────────────── */
  (function initLoginShake() {
    const errorAlert = document.querySelector('.diq-auth-form-inner .diq-alert-error');
    if (!errorAlert) return;
    const form = document.querySelector('.diq-auth-form-inner');
    if (form) {
      form.classList.add('shake');
      form.addEventListener('animationend', () => form.classList.remove('shake'), { once: true });
    }
  })();

  /* ── 7. Register — real-time field validation ────────────── */
  (function initRegisterValidation() {
    const form = document.getElementById('registerForm');
    if (!form) return;

    function validateEmail(val) {
      return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(val);
    }
    function validatePhone(val) {
      return val === '' || /^\d{10}$/.test(val);
    }
    function validatePassword(val) {
      return val.length >= 6;
    }

    function setFieldState(input, valid, msg) {
      const errorEl = document.getElementById(input.id + '-error');
      if (valid) {
        input.classList.remove('is-invalid');
        input.classList.add('is-valid');
        if (errorEl) errorEl.textContent = '';
      } else {
        input.classList.remove('is-valid');
        input.classList.add('is-invalid');
        if (errorEl) errorEl.textContent = msg;
      }
    }

    function clearFieldState(input) {
      input.classList.remove('is-valid', 'is-invalid');
      const errorEl = document.getElementById(input.id + '-error');
      if (errorEl) errorEl.textContent = '';
    }

    const nameInput  = form.querySelector('[name="name"]');
    const emailInput = form.querySelector('[name="email"]');
    const phoneInput = form.querySelector('[name="phone"]');
    const passInput  = form.querySelector('[name="password"]');
    const confInput  = form.querySelector('[name="confirmPassword"]');

    if (nameInput) {
      nameInput.addEventListener('blur', function () {
        if (this.value.trim()) {
          setFieldState(this, this.value.trim().length >= 2, 'Name must be at least 2 characters');
        } else {
          clearFieldState(this);
        }
      });
    }

    if (emailInput) {
      emailInput.addEventListener('blur', function () {
        if (this.value.trim()) {
          setFieldState(this, validateEmail(this.value.trim()), 'Enter a valid email address');
        } else {
          clearFieldState(this);
        }
      });
    }

    if (phoneInput) {
      phoneInput.addEventListener('blur', function () {
        if (this.value.trim()) {
          setFieldState(this, validatePhone(this.value.trim()), 'Phone must be 10 digits');
        } else {
          clearFieldState(this);
        }
      });
    }

    if (passInput) {
      passInput.addEventListener('blur', function () {
        if (this.value) {
          setFieldState(this, validatePassword(this.value), 'Password must be at least 6 characters');
        } else {
          clearFieldState(this);
        }
      });
    }

    if (confInput && passInput) {
      confInput.addEventListener('blur', function () {
        if (this.value) {
          setFieldState(this, this.value === passInput.value, 'Passwords do not match');
        } else {
          clearFieldState(this);
        }
      });
    }

    // Pre-submission validation
    form.addEventListener('submit', function (e) {
      let valid = true;

      if (nameInput && !nameInput.value.trim()) {
        setFieldState(nameInput, false, 'Full name is required');
        valid = false;
      }
      if (emailInput && !validateEmail(emailInput.value.trim())) {
        setFieldState(emailInput, false, 'Enter a valid email address');
        valid = false;
      }
      if (phoneInput && !validatePhone(phoneInput.value.trim())) {
        setFieldState(phoneInput, false, 'Phone must be 10 digits');
        valid = false;
      }
      if (passInput && !validatePassword(passInput.value)) {
        setFieldState(passInput, false, 'Password must be at least 6 characters');
        valid = false;
      }
      if (confInput && confInput.value !== passInput.value) {
        setFieldState(confInput, false, 'Passwords do not match');
        valid = false;
      }

      if (!valid) {
        e.preventDefault();
        const firstInvalid = form.querySelector('.is-invalid');
        if (firstInvalid) firstInvalid.focus();
      }
    });
  })();

  /* ── 8. Timeline stagger animation ──────────────────────── */
  (function initTimeline() {
    const items = document.querySelectorAll('.diq-timeline-item');
    items.forEach(function (item, i) {
      item.style.animationDelay = (i * 100) + 'ms';
    });
  })();

  /* ── 9. Booking Wizard / Stepper ─────────────────────────── */
  (function initStepper() {
    const stepper = document.getElementById('diqStepper');
    if (!stepper) return;

    const steps      = Array.from(stepper.querySelectorAll('.diq-step'));
    const panels     = Array.from(document.querySelectorAll('.diq-step-panel'));
    const progressEl = stepper.querySelector('.diq-stepper-progress');
    let current      = 0;

    function goTo(index) {
      if (index < 0 || index >= steps.length) return;

      steps.forEach(function (s, i) {
        s.classList.remove('active', 'done');
        if (i < index)  s.classList.add('done');
        if (i === index) s.classList.add('active');
      });

      panels.forEach(function (p, i) {
        p.classList.toggle('active', i === index);
      });

      if (progressEl) {
        const pct = steps.length > 1
          ? (index / (steps.length - 1)) * 100
          : 0;
        progressEl.style.width = pct + '%';
      }

      current = index;

      // Scroll to top of wizard
      const wizardEl = document.getElementById('bookingWizard');
      if (wizardEl) {
        wizardEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }

    // Expose globally for button onclick handlers
    window.diqStepNext = function () { goTo(current + 1); };
    window.diqStepPrev = function () { goTo(current - 1); };
    window.diqStepGoTo = function (i) { goTo(i); };

    // Validate before going to next step
    window.diqStepNextValidate = function (requiredIds) {
      let ok = true;
      requiredIds.forEach(function (id) {
        const el = document.getElementById(id);
        if (!el) return;
        if (!el.value || el.value.trim() === '') {
          el.classList.add('is-invalid');
          el.focus();
          ok = false;
        } else {
          el.classList.remove('is-invalid');
        }
      });
      if (ok) goTo(current + 1);
    };

    goTo(0);
  })();

  /* ── 10. Animated stat counters ─────────────────────────── */
  (function initCounters() {
    const counters = document.querySelectorAll('[data-count-to]');
    if (!counters.length) return;

    function animateCounter(el, target, duration) {
      const start = performance.now();
      function update(now) {
        const elapsed = now - start;
        const progress = Math.min(elapsed / duration, 1);
        // Ease out cubic
        const eased = 1 - Math.pow(1 - progress, 3);
        el.textContent = Math.round(eased * target);
        if (progress < 1) requestAnimationFrame(update);
      }
      requestAnimationFrame(update);
    }

    const observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          const el = entry.target;
          const target = parseInt(el.dataset.countTo, 10);
          animateCounter(el, target, 900);
          observer.unobserve(el);
        }
      });
    }, { threshold: 0.4 });

    counters.forEach(function (el) { observer.observe(el); });
  })();

  /* ── 11. Admin — agent assignment inline form ────────────── */
  (function initAssignForm() {
    document.querySelectorAll('.assign-form select').forEach(function (sel) {
      sel.addEventListener('change', function () {
        const btn = this.closest('.assign-form').querySelector('button[type="submit"]');
        if (btn) btn.disabled = !this.value;
      });
    });
  })();

  /* ── 12. Filter active badge toggle ─────────────────────── */
  (function initFilterBadge() {
    const filterBadge = document.getElementById('filterActiveBadge');
    if (!filterBadge) return;
    const selects = document.querySelectorAll('.diq-filter-bar .diq-select');
    selects.forEach(function (s) {
      s.addEventListener('change', function () {
        const anyActive = Array.from(selects).some(el => el.value);
        filterBadge.style.display = anyActive ? 'inline-flex' : 'none';
      });
    });
  })();

  /* ── 13. Tooltip shimmer on stat cards ───────────────────── */
  (function initStatHover() {
    document.querySelectorAll('.diq-stat-card').forEach(function (card) {
      card.addEventListener('mousemove', function (e) {
        const rect = card.getBoundingClientRect();
        const x = ((e.clientX - rect.left) / rect.width * 100).toFixed(1);
        const y = ((e.clientY - rect.top)  / rect.height * 100).toFixed(1);
        card.style.setProperty('--mouse-x', x + '%');
        card.style.setProperty('--mouse-y', y + '%');
      });
    });
  })();

})();

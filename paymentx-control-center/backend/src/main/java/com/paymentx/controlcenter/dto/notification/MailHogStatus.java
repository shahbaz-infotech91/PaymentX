package com.paymentx.controlcenter.dto.notification;

/**
 * ENGLISH: The real, live state of the platform's MailHog SMTP-capture
 * instance (infra/docker-compose.yml) - whether it's actually
 * reachable right now, and its real captured-message count from
 * MailHog's own API (never a value this backend invents). webUiUrl is
 * the real, safe link the frontend opens in a new tab to preview real
 * captured emails directly in MailHog's own UI - this backend never
 * proxies or renders email content itself.
 *
 * HINGLISH: Platform ke MailHog SMTP-capture instance
 * (infra/docker-compose.yml) ka real, live state - kya wo abhi
 * actually reachable hai, aur MailHog ke apne API se uska real
 * captured-message count (kabhi ek value jo ye backend invent kare
 * nahi). webUiUrl wo real, safe link hai jise frontend ek naye tab me
 * kholta hai real captured emails ko seedhe MailHog ke apne UI me
 * preview karne ke liye - ye backend kabhi email content khud proxy ya
 * render nahi karta.
 */
public record MailHogStatus(
        boolean reachable,
        String errorMessage,
        Long messageCount,
        String webUiUrl
) {
}

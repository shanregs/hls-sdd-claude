package com.hls.recruitment.internal;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders the printable offer letter as a self-contained HTML page. Everything that comes from a user is HTML-escaped;
 * the page carries its own print stylesheet and no script.
 */
@Component
class OfferLetterRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);

    String render(OfferService.OfferDto offer) {
        StringBuilder html = new StringBuilder(2048);
        html.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<title>Offer letter - ").append(esc(offer.candidateName())).append("</title>")
                .append("<style>body{font-family:Arial,Helvetica,sans-serif;max-width:720px;margin:2rem auto;padding:0 1rem;color:#111}")
                .append("h1{font-size:1.3rem}table{border-collapse:collapse;width:100%}td,th{border:1px solid #888;padding:.4rem;text-align:left}")
                .append(".status{font-weight:bold}@media print{body{margin:0}}</style></head><body>");
        html.append("<h1>Offer of employment</h1>");
        html.append("<p class=\"status\">Status: ").append(esc(offer.status())).append("</p>");
        html.append("<p>Dear ").append(esc(offer.candidateName())).append(",</p>");
        html.append("<p>We are pleased to offer you the position of <strong>").append(esc(offer.role()))
                .append("</strong> with HLS. This offer is dated ").append(offer.offerDate().format(DATE))
                .append(" and is open for your reply until ").append(offer.responseDeadline().format(DATE)).append(".</p>");
        html.append("<table><tr><th>Designation</th><td>").append(esc(offer.role())).append("</td></tr>");
        html.append("<tr><th>Monthly salary</th><td>").append(esc(rupees(offer.monthlySalary()))).append("</td></tr>");
        html.append("<tr><th>Allowances</th><td>").append(esc(orDash(offer.allowances()))).append("</td></tr>");
        html.append("<tr><th>Expected joining</th><td>")
                .append(offer.expectedJoining() == null ? "To be confirmed" : offer.expectedJoining().format(DATE))
                .append("</td></tr>");
        html.append("<tr><th>Notice, bond and other terms</th><td>").append(esc(orDash(offer.terms()))).append("</td></tr></table>");
        html.append("<h2>Training</h2><p>You will first attend a one-month induction. The induction is unpaid; your salary starts when you are placed in a School.</p>");
        html.append("<p>Yours sincerely,<br>HLS</p></body></html>");
        return html.toString();
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    /** Rupees with Indian digit grouping (12,34,567.00). */
    static String rupees(String amount) {
        String[] parts = amount.split("\\.");
        String whole = parts[0];
        String fraction = parts.length > 1 ? parts[1] : "00";
        StringBuilder grouped = new StringBuilder();
        int len = whole.length();
        if (len <= 3) {
            grouped.append(whole);
        } else {
            String last3 = whole.substring(len - 3);
            String rest = whole.substring(0, len - 3);
            StringBuilder head = new StringBuilder();
            for (int i = 0; i < rest.length(); i++) {
                if (i > 0 && (rest.length() - i) % 2 == 0) {
                    head.append(',');
                }
                head.append(rest.charAt(i));
            }
            grouped.append(head).append(',').append(last3);
        }
        return "Rs. " + grouped + "." + fraction;
    }
}

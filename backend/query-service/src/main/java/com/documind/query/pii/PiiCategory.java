package com.documind.query.pii;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public enum PiiCategory {
    // Domain labels are matched explicitly so sentence punctuation is not absorbed into the
    // address. Swallowing a trailing dot makes the same address hash to two placeholders.
    EMAIL("EMAIL", Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]{2,})+"), value -> true),

    IBAN("IBAN", Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}\\b"), value -> true),

    // Checked with Luhn so that order numbers, clause numbers, and long figures in a
    // contract are not mistaken for card numbers.
    CREDIT_CARD("CARD", Pattern.compile("\\b(?:\\d[ -]?){12,18}\\d\\b"), PiiCategory::passesLuhn),

    NATIONAL_ID("NATIONAL_ID", Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"), value -> true),

    PHONE(
            "PHONE",
            Pattern.compile("(?<![\\w.])\\+\\d[\\d\\s().-]{7,17}\\d(?![\\w.])"),
            value -> true),

    IP_ADDRESS(
            "IP",
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"),
            PiiCategory::isPlausibleIpv4);

    private final String label;
    private final Pattern pattern;
    private final Predicate<String> confirms;

    PiiCategory(String label, Pattern pattern, Predicate<String> confirms) {
        this.label = label;
        this.pattern = pattern;
        this.confirms = confirms;
    }

    public String label() {
        return label;
    }

    public Pattern pattern() {
        return pattern;
    }

    public boolean confirms(String candidate) {
        return confirms.test(candidate);
    }

    private static boolean passesLuhn(String candidate) {
        String digits = candidate.replaceAll("\\D", "");
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }

        int sum = 0;
        boolean doubling = false;
        for (int index = digits.length() - 1; index >= 0; index--) {
            int digit = digits.charAt(index) - '0';
            if (doubling) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubling = !doubling;
        }
        return sum % 10 == 0;
    }

    private static boolean isPlausibleIpv4(String candidate) {
        for (String octet : candidate.split("\\.")) {
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }
}

package com.saleh.enezi;

import java.util.ArrayList;
import java.util.List;

/** Locale-independent parsing for Arabic and Latin monetary/quantity input. */
public final class NumericInput {
    private NumericInput() {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder mapped = new StringBuilder();
        boolean negative = false;
        boolean invalid = false;
        String value = raw.trim();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '٠' && c <= '٩') c = (char) ('0' + c - '٠');
            else if (c >= '۰' && c <= '۹') c = (char) ('0' + c - '۰');
            if (c == '٫') c = '\u0001'; // Explicit Arabic decimal separator.
            if (c == '٬' || Character.isWhitespace(c) || c == '\u00a0' || c == '\u202f' || c == '\'') continue;
            if (c == '-' && mapped.length() == 0) { negative = true; continue; }
            if ((c >= '0' && c <= '9') || c == '.' || c == ',' || c == '\u0001') mapped.append(c);
        }
        String rawNumber = mapped.toString();
        if (rawNumber.isEmpty()) return "";
        int explicitArabicDecimal = rawNumber.lastIndexOf('\u0001');
        int lastDot = rawNumber.lastIndexOf('.');
        int lastComma = rawNumber.lastIndexOf(',');
        int decimalAt = -1;
        if (explicitArabicDecimal >= 0) decimalAt = explicitArabicDecimal;
        else if (lastDot >= 0 && lastComma >= 0) decimalAt = Math.max(lastDot, lastComma);
        else {
            char sep = lastDot >= 0 ? '.' : (lastComma >= 0 ? ',' : 0);
            if (sep != 0) {
                int last = Math.max(lastDot, lastComma);
                int after = rawNumber.length() - last - 1;
                int occurrences = 0;
                for (int i = 0; i < rawNumber.length(); i++) if (rawNumber.charAt(i) == sep) occurrences++;
                // A single three-digit group (or repeated groups of three) is thousands grouping.
                boolean grouped = sep == ',' && after == 3;
                if (occurrences > 1) {
                    String[] groups = rawNumber.split(sep == '.' ? "\\." : ",", -1);
                    grouped = groups.length > 1;
                    for (int i = 1; i < groups.length; i++) {
                        if (groups[i].length() != 3) { grouped = false; break; }
                    }
                }
                if (!grouped) decimalAt = last;
            }
        }
        StringBuilder out = new StringBuilder();
        if (negative) out.append('-');
        for (int i = 0; i < rawNumber.length(); i++) {
            char c = rawNumber.charAt(i);
            if (i == decimalAt) out.append('.');
            else if (c >= '0' && c <= '9') out.append(c);
        }
        return out.toString();
    }

    public static double parseDouble(String raw, double fallback) {
        try {
            String normalized = normalize(raw);
            if (normalized.isEmpty() || "-".equals(normalized) || ".".equals(normalized) || "-.".equals(normalized)) return fallback;
            return Double.parseDouble(normalized);
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}

package com.gallery.generator.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class to match filenames against filesystem wildcards (* and ?) case-insensitively.
 */
public class MaskMatcher {
    private final List<Pattern> patterns = new ArrayList<>();

    public MaskMatcher(String maskString) {
        if (maskString == null || maskString.isBlank()) {
            return;
        }
        String[] tokens = maskString.split(",");
        for (String token : tokens) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                patterns.add(convertWildcardToPattern(trimmed));
            }
        }
    }

    public boolean matches(String filename) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(filename).matches()) {
                return true;
            }
        }
        return false;
    }

    private Pattern convertWildcardToPattern(String wildcard) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < wildcard.length(); i++) {
            char c = wildcard.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                case '\\', '.', '[', ']', '{', '}', '(', ')', '+', '^', '$', '|' -> sb.append("\\").append(c);
                default -> sb.append(c);
            }
        }
        sb.append("$");
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }
}


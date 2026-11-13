package com.navith.amap.car;

/**
 * Highway exit-sign text from {@code AMapExitDirectionInfo}. This is a road
 * sign label, not a roundabout exit index.
 */
public final class AmapExitLabel {
    static final int MAX_LENGTH = 80;
    private AmapExitLabel() {}

    public static String join(String[] names, String[] directions) {
        StringBuilder text = new StringBuilder();
        append(text, names);
        append(text, directions);
        if (text.length() > MAX_LENGTH) return text.substring(0, MAX_LENGTH).trim();
        return text.toString();
    }

    private static void append(StringBuilder text, String[] parts) {
        if (parts == null) return;
        for (String part : parts) {
            if (part == null) continue;
            String token = part.replace('\n', ' ').replace('\r', ' ').trim().replaceAll(" +", " ");
            if (token.isEmpty() || containsToken(text, token)) continue;
            if (text.length() > 0) {
                if (text.length() + 3 >= MAX_LENGTH) return;
                text.append(" / ");
            }
            if (text.length() >= MAX_LENGTH) return;
            text.append(token);
        }
    }

    private static boolean containsToken(StringBuilder text, String token) {
        String existing = text.toString();
        if (existing.equals(token)) return true;
        return existing.startsWith(token + " / ")
                || existing.endsWith(" / " + token)
                || existing.contains(" / " + token + " / ");
    }
}

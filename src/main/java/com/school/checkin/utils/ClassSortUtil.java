package com.school.checkin.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClassSortUtil {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");
    private static final Map<Character, Integer> CHINESE_NUMBER_MAP = new HashMap<>();

    static {
        CHINESE_NUMBER_MAP.put('零', 0);
        CHINESE_NUMBER_MAP.put('一', 1);
        CHINESE_NUMBER_MAP.put('二', 2);
        CHINESE_NUMBER_MAP.put('三', 3);
        CHINESE_NUMBER_MAP.put('四', 4);
        CHINESE_NUMBER_MAP.put('五', 5);
        CHINESE_NUMBER_MAP.put('六', 6);
        CHINESE_NUMBER_MAP.put('七', 7);
        CHINESE_NUMBER_MAP.put('八', 8);
        CHINESE_NUMBER_MAP.put('九', 9);
        CHINESE_NUMBER_MAP.put('十', 10);
    }

    private ClassSortUtil() {
    }

    public static <T> Comparator<T> comparingGradeAndClass(Function<T, String> gradeGetter,
                                                           Function<T, String> classGetter) {
        return Comparator
                .comparing((T item) -> extractGradeOrder(gradeGetter.apply(item)))
                .thenComparing(item -> StringUtils.defaultString(gradeGetter.apply(item)))
                .thenComparing(item -> extractClassOrder(classGetter.apply(item)))
                .thenComparing(item -> StringUtils.defaultString(classGetter.apply(item)));
    }

    public static int extractGradeOrder(String gradeName) {
        return extractOrderValue(gradeName);
    }

    public static int extractClassOrder(String className) {
        return extractOrderValue(className);
    }

    private static int extractOrderValue(String text) {
        String normalized = normalizeDigits(StringUtils.trimToEmpty(text));
        if (StringUtils.isBlank(normalized)) {
            return Integer.MAX_VALUE;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        Integer chineseValue = parseChineseNumber(normalized);
        return chineseValue == null ? Integer.MAX_VALUE : chineseValue;
    }

    private static Integer parseChineseNumber(String text) {
        String normalized = text.replace("年级", "")
                .replace("班", "")
                .replace("第", "")
                .replace("高", "")
                .replace("初", "")
                .replace("小", "")
                .replace("(", "")
                .replace(")", "")
                .trim();
        if (StringUtils.isBlank(normalized)) {
            return null;
        }
        if (normalized.length() == 1) {
            return CHINESE_NUMBER_MAP.get(normalized.charAt(0));
        }
        if (normalized.length() == 2 && normalized.charAt(0) == '十') {
            Integer last = CHINESE_NUMBER_MAP.get(normalized.charAt(1));
            return last == null ? 10 : 10 + last;
        }
        if (normalized.length() == 2 && normalized.charAt(1) == '十') {
            Integer first = CHINESE_NUMBER_MAP.get(normalized.charAt(0));
            return first == null ? null : first * 10;
        }
        if (normalized.length() == 3 && normalized.charAt(1) == '十') {
            Integer first = CHINESE_NUMBER_MAP.get(normalized.charAt(0));
            Integer last = CHINESE_NUMBER_MAP.get(normalized.charAt(2));
            if (first == null || last == null) {
                return null;
            }
            return first * 10 + last;
        }
        return null;
    }

    private static String normalizeDigits(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current >= '０' && current <= '９') {
                builder.append((char) (current - '０' + '0'));
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }
}

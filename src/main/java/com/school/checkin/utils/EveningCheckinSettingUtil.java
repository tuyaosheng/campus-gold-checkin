package com.school.checkin.utils;

import com.school.checkin.common.ErrorCode;
import com.school.checkin.dto.EveningCheckinRuleRowRequest;
import com.school.checkin.dto.EveningCheckinSettingSaveRequest;
import com.school.checkin.exception.BusinessException;
import com.school.checkin.vo.EveningCheckinRuleRowVO;
import com.school.checkin.vo.EveningCheckinSettingVO;
import org.apache.commons.lang3.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EveningCheckinSettingUtil {

    private EveningCheckinSettingUtil() {
    }

    public static EveningCheckinSettingVO defaultSettings() {
        EveningCheckinSettingVO vo = new EveningCheckinSettingVO();
        vo.setDefaultReturnWeekday(DayOfWeek.SUNDAY.name());
        vo.setSuggestedReturnDate(nextOrSameSunday(LocalDate.now()).toString());
        List<EveningCheckinRuleRowVO> rules = new ArrayList<>();
        rules.add(buildRow("七年级", "06:00", "07:10", "21:30", "21:50", "19:00", "19:20"));
        rules.add(buildRow("八年级", "06:00", "07:10", "21:30", "21:50", "19:20", "19:40"));
        rules.add(buildRow("九年级", "06:00", "07:10", "21:30", "21:50", "19:40", "20:00"));
        vo.setRules(rules);
        return vo;
    }

    public static EveningCheckinSettingVO deserialize(String content, List<String> fallbackGrades) {
        EveningCheckinSettingVO result = defaultSettings();
        Map<String, EveningCheckinRuleRowVO> parsedRules = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(content)) {
            String[] lines = content.split("\\r?\\n");
            for (String line : lines) {
                String[] pair = line.split("=", 2);
                if (pair.length != 2) {
                    continue;
                }
                String key = pair[0].trim();
                String value = pair[1].trim();
                if ("defaultReturnWeekday".equals(key)) {
                    result.setDefaultReturnWeekday(normalizeWeekday(value));
                    continue;
                }
                if ("overrideReturnDate".equals(key)) {
                    result.setOverrideReturnDate(StringUtils.trimToNull(value));
                    continue;
                }
                if (key.startsWith("rule.")) {
                    String[] items = value.split("\\|");
                    if (items.length == 7) {
                        parsedRules.put(items[0], buildRow(items[0], items[1], items[2], items[3], items[4], items[5], items[6]));
                    } else if (items.length == 5) {
                        parsedRules.put(items[0], buildRow(items[0], "06:00", "07:10", items[1], items[2], items[3], items[4]));
                    }
                }
            }
        }
        List<String> grades = fallbackGrades == null || fallbackGrades.isEmpty()
                ? Arrays.asList("七年级", "八年级", "九年级")
                : fallbackGrades;
        List<EveningCheckinRuleRowVO> rows = new ArrayList<>();
        for (String grade : grades) {
            EveningCheckinRuleRowVO row = parsedRules.get(grade);
            if (row == null) {
                row = buildRow(grade, "06:00", "07:10", "21:30", "21:50", "19:00", "19:20");
            }
            rows.add(row);
        }
        result.setRules(rows);
        result.setDefaultReturnWeekday(normalizeWeekday(result.getDefaultReturnWeekday()));
        result.setSuggestedReturnDate(nextOrSameSunday(LocalDate.now()).toString());
        result.setEffectiveReturnDate(resolveEffectiveReturnDate(LocalDate.now(), result).toString());
        return result;
    }

    public static String serialize(EveningCheckinSettingVO setting) {
        List<String> lines = new ArrayList<>();
        lines.add("defaultReturnWeekday=" + normalizeWeekday(setting.getDefaultReturnWeekday()));
        lines.add("overrideReturnDate=" + safe(setting.getOverrideReturnDate()));
        int index = 1;
        for (EveningCheckinRuleRowVO row : setting.getRules()) {
            lines.add("rule." + index++ + "=" + String.join("|",
                    safe(row.getGradeName()),
                    safe(row.getMorningStart()),
                    safe(row.getMorningEnd()),
                    safe(row.getNormalStart()),
                    safe(row.getNormalEnd()),
                    safe(row.getReturnStart()),
                    safe(row.getReturnEnd())));
        }
        return String.join("\n", lines);
    }

    public static void validate(EveningCheckinSettingSaveRequest request) {
        if (request.getRules() == null || request.getRules().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "至少需要配置 1 个年级的晚间打卡规则");
        }
        if (StringUtils.isNotBlank(request.getOverrideReturnDate())) {
            try {
                LocalDate.parse(request.getOverrideReturnDate());
            } catch (Exception ex) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "返校日日期格式错误，应为 yyyy-MM-dd");
            }
        }
        for (EveningCheckinRuleRowRequest row : request.getRules()) {
            if (StringUtils.isBlank(row.getGradeName())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "年级不能为空");
            }
            assertTimeRange(row.getMorningStart(), row.getMorningEnd(), row.getGradeName() + " 早上");
            assertTimeRange(row.getNormalStart(), row.getNormalEnd(), row.getGradeName() + " 平时晚上");
            assertTimeRange(row.getReturnStart(), row.getReturnEnd(), row.getGradeName() + " 返校日晚上");
        }
    }

    public static DayOfWeek parseWeekday(String weekday) {
        return DayOfWeek.valueOf(normalizeWeekday(weekday));
    }

    public static boolean isReturnDay(LocalDate today, EveningCheckinSettingVO setting) {
        return today.equals(resolveEffectiveReturnDate(today, setting));
    }

    public static LocalDate resolveEffectiveReturnDate(LocalDate today, EveningCheckinSettingVO setting) {
        LocalDate overrideDate = parseDate(setting.getOverrideReturnDate());
        if (overrideDate != null && isSameWeek(today, overrideDate)) {
            return overrideDate;
        }
        return nextOrSame(today, parseWeekday(setting.getDefaultReturnWeekday()));
    }

    public static EveningCheckinRuleRowVO findRule(EveningCheckinSettingVO setting, String gradeName) {
        if (setting == null || setting.getRules() == null || setting.getRules().isEmpty()) {
            return null;
        }
        if (StringUtils.isBlank(gradeName)) {
            return setting.getRules().get(0);
        }
        return setting.getRules().stream()
                .filter(item -> gradeName.equals(item.getGradeName()))
                .findFirst()
                .orElse(setting.getRules().get(0));
    }

    private static void assertTimeRange(String start, String end, String label) {
        LocalTime startTime = LocalTime.parse(start);
        LocalTime endTime = LocalTime.parse(end);
        if (!startTime.isBefore(endTime)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + " 的开始时间必须早于结束时间");
        }
    }

    private static String normalizeWeekday(String weekday) {
        try {
            return DayOfWeek.valueOf(StringUtils.trimToEmpty(weekday).toUpperCase()).name();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "返校日必须是 MONDAY 到 SUNDAY 之一");
        }
    }

    private static LocalDate parseDate(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean isSameWeek(LocalDate first, LocalDate second) {
        LocalDate firstStart = first.with(DayOfWeek.MONDAY);
        LocalDate secondStart = second.with(DayOfWeek.MONDAY);
        return firstStart.equals(secondStart);
    }

    private static LocalDate nextOrSameSunday(LocalDate baseDate) {
        return baseDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    private static LocalDate nextOrSame(LocalDate baseDate, DayOfWeek dayOfWeek) {
        return baseDate.with(TemporalAdjusters.nextOrSame(dayOfWeek));
    }

    private static EveningCheckinRuleRowVO buildRow(String gradeName, String morningStart, String morningEnd, String normalStart, String normalEnd, String returnStart, String returnEnd) {
        EveningCheckinRuleRowVO row = new EveningCheckinRuleRowVO();
        row.setGradeName(gradeName);
        row.setMorningStart(morningStart);
        row.setMorningEnd(morningEnd);
        row.setNormalStart(normalStart);
        row.setNormalEnd(normalEnd);
        row.setReturnStart(returnStart);
        row.setReturnEnd(returnEnd);
        return row;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

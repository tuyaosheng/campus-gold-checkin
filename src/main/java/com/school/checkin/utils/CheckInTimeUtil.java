package com.school.checkin.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.checkin.common.ErrorCode;
import com.school.checkin.entity.SysSetting;
import com.school.checkin.enums.TimeSlot;
import com.school.checkin.exception.BusinessException;
import com.school.checkin.mapper.SysSettingMapper;
import com.school.checkin.vo.EveningCheckinRuleRowVO;
import com.school.checkin.vo.EveningCheckinSettingVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CheckInTimeUtil {

    private static final double QUALITY_SCORE_SCALE = 10D;
    private static final long SETTING_CACHE_MILLIS = 10_000L;

    private final LocalTime morningStart;
    private final LocalTime morningEnd;
    private final SysSettingMapper sysSettingMapper;

    private volatile CachedEveningSetting cachedEveningSetting;
    private volatile CachedBooleanSetting cachedDebugSetting;

    public CheckInTimeUtil(@Value("${checkin.time-windows.morning-start}") String morningStart,
                           @Value("${checkin.time-windows.morning-end}") String morningEnd,
                           SysSettingMapper sysSettingMapper) {
        this.morningStart = LocalTime.parse(morningStart);
        this.morningEnd = LocalTime.parse(morningEnd);
        this.sysSettingMapper = sysSettingMapper;
    }

    public void assertAllowedForPublicApi() {
        getCurrentTimeSlot(null);
    }

    public boolean isCheckInTimeNow(String gradeName) {
        return isDebugModeEnabled() || getCurrentTimeSlotOrNull(gradeName) != null;
    }

    public TimeSlot getCurrentTimeSlotOrNull(String gradeName) {
        LocalTime now = LocalTime.now();
        ParsedEveningRule rule = resolveParsedEveningRule(gradeName);
        if (!now.isBefore(rule.morningStart) && !now.isAfter(rule.morningEnd)) {
            return TimeSlot.MORNING;
        }
        if (!now.isBefore(rule.normalStart) && !now.isAfter(rule.normalEnd)) {
            return TimeSlot.EVENING;
        }
        if (isReturnDay()) {
            if (!now.isBefore(rule.returnStart) && !now.isAfter(rule.returnEnd)) {
                return TimeSlot.EVENING;
            }
        }
        return null;
    }

    public TimeSlot getCurrentTimeSlot(String gradeName) {
        if (isDebugModeEnabled()) {
            return resolveDisplayTimeSlot(gradeName);
        }
        TimeSlot timeSlot = getCurrentTimeSlotOrNull(gradeName);
        if (timeSlot != null) {
            return timeSlot;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, getPublicAccessMessage(gradeName));
    }

    public TimeSlot resolveDisplayTimeSlot(String gradeName) {
        TimeSlot currentTimeSlot = getCurrentTimeSlotOrNull(gradeName);
        if (currentTimeSlot != null) {
            return currentTimeSlot;
        }
        LocalTime now = LocalTime.now();
        ParsedEveningRule rule = resolveParsedEveningRule(gradeName);
        LocalTime eveningStart = isReturnDay() && rule.returnStart.isBefore(rule.normalStart)
                ? rule.returnStart : rule.normalStart;
        return now.isBefore(eveningStart) ? TimeSlot.MORNING : TimeSlot.EVENING;
    }

    public String getPublicAccessMessage(String gradeName) {
        ParsedEveningRule rule = resolveParsedEveningRule(gradeName);
        if (isReturnDay()) {
            return "当前不在打卡时间段，仅允许 " + rule.morningStartText + "-" + rule.morningEndText + "、" + rule.normalStartText + "-" + rule.normalEndText
                    + " 或 " + rule.returnStartText + "-" + rule.returnEndText + " 提交打卡";
        }
        return "当前不在打卡时间段，仅允许 " + rule.morningStartText + "-" + rule.morningEndText + " 或 " + rule.normalStartText + "-" + rule.normalEndText + " 提交打卡";
    }

    public LocalTime resolveManualCheckinTime(LocalDate targetDate, String gradeName, TimeSlot timeSlot) {
        CachedEveningSetting cached = loadCachedEveningSetting();
        ParsedEveningRule rule = findRule(cached, gradeName);
        if (TimeSlot.MORNING.equals(timeSlot)) {
            return rule.morningStart;
        }
        if (EveningCheckinSettingUtil.isReturnDay(targetDate, cached.setting)) {
            return rule.returnStart.isBefore(rule.normalStart) ? rule.returnStart : rule.normalStart;
        }
        return rule.normalStart;
    }

    public double calculateQualityScore(String gradeName, String timeSlot, LocalDateTime checkinTime) {
        if (checkinTime == null) {
            return 0D;
        }
        if (TimeSlot.MORNING.name().equalsIgnoreCase(timeSlot)) {
            ParsedEveningRule rule = resolveParsedEveningRule(gradeName);
            return calculateWindowScore(checkinTime.toLocalTime(), rule.morningStart, rule.morningEnd, true);
        }
        ParsedEveningRule rule = resolveParsedEveningRule(gradeName);
        LocalDate targetDate = checkinTime.toLocalDate();
        LocalTime actualTime = checkinTime.toLocalTime();
        if (isReturnDay(targetDate) && isWithinWindow(actualTime, rule.returnStart, rule.returnEnd)) {
            return calculateWindowScore(actualTime, rule.returnStart, rule.returnEnd, true);
        }
        return calculateWindowScore(actualTime, rule.normalStart, rule.normalEnd, false);
    }

    private double calculateWindowScore(LocalTime actualTime, LocalTime start, LocalTime end, boolean earlierBetter) {
        int totalSeconds = Math.max(1, end.toSecondOfDay() - start.toSecondOfDay());
        int clampedOffset = Math.max(0, Math.min(actualTime.toSecondOfDay() - start.toSecondOfDay(), totalSeconds));
        double ratio = clampedOffset * 1.0 / totalSeconds;
        double score = earlierBetter ? (1D - ratio) * QUALITY_SCORE_SCALE : ratio * QUALITY_SCORE_SCALE;
        return Math.round(score * 100D) / 100D;
    }

    private boolean isWithinWindow(LocalTime time, LocalTime start, LocalTime end) {
        return !time.isBefore(start) && !time.isAfter(end);
    }

    private boolean isDebugModeEnabled() {
        long now = System.currentTimeMillis();
        CachedBooleanSetting cached = cachedDebugSetting;
        if (cached != null && now - cached.loadedAt <= SETTING_CACHE_MILLIS) {
            return cached.enabled;
        }
        SysSetting setting = sysSettingMapper.selectOne(new LambdaQueryWrapper<SysSetting>()
                .eq(SysSetting::getSettingKey, "checkin_debug_enabled")
                .last("limit 1"));
        boolean enabled = setting != null && "1".equals(String.valueOf(setting.getSettingValue()));
        cachedDebugSetting = new CachedBooleanSetting(enabled, now);
        return enabled;
    }

    private boolean isReturnDay() {
        return isReturnDay(LocalDate.now());
    }

    private boolean isReturnDay(LocalDate targetDate) {
        CachedEveningSetting cached = loadCachedEveningSetting();
        return EveningCheckinSettingUtil.isReturnDay(targetDate, cached.setting);
    }

    private ParsedEveningRule resolveParsedEveningRule(String gradeName) {
        return findRule(loadCachedEveningSetting(), gradeName);
    }

    private ParsedEveningRule findRule(CachedEveningSetting cached, String gradeName) {
        ParsedEveningRule rule = cached.parsedRuleMap.get(gradeName);
        if (rule != null) {
            return rule;
        }
        EveningCheckinRuleRowVO fallbackRule = EveningCheckinSettingUtil.findRule(cached.setting, gradeName);
        return ParsedEveningRule.from(fallbackRule);
    }

    private CachedEveningSetting loadCachedEveningSetting() {
        long now = System.currentTimeMillis();
        CachedEveningSetting cached = cachedEveningSetting;
        if (cached != null && now - cached.loadedAt <= SETTING_CACHE_MILLIS) {
            return cached;
        }
        SysSetting setting = sysSettingMapper.selectOne(new LambdaQueryWrapper<SysSetting>()
                .eq(SysSetting::getSettingKey, "evening_checkin_settings")
                .last("limit 1"));
        List<String> fallbackGrades = Arrays.asList("七年级", "八年级", "九年级");
        EveningCheckinSettingVO settingVO = EveningCheckinSettingUtil.deserialize(setting == null ? "" : setting.getSettingValue(), fallbackGrades);
        Map<String, ParsedEveningRule> parsedMap = new HashMap<>();
        if (settingVO.getRules() != null) {
            for (EveningCheckinRuleRowVO row : settingVO.getRules()) {
                parsedMap.put(row.getGradeName(), ParsedEveningRule.from(row));
            }
        }
        CachedEveningSetting reloaded = new CachedEveningSetting(settingVO, parsedMap, now);
        cachedEveningSetting = reloaded;
        return reloaded;
    }

    private static final class CachedBooleanSetting {
        private final boolean enabled;
        private final long loadedAt;

        private CachedBooleanSetting(boolean enabled, long loadedAt) {
            this.enabled = enabled;
            this.loadedAt = loadedAt;
        }
    }

    private static final class CachedEveningSetting {
        private final EveningCheckinSettingVO setting;
        private final Map<String, ParsedEveningRule> parsedRuleMap;
        private final long loadedAt;

        private CachedEveningSetting(EveningCheckinSettingVO setting, Map<String, ParsedEveningRule> parsedRuleMap, long loadedAt) {
            this.setting = setting;
            this.parsedRuleMap = parsedRuleMap;
            this.loadedAt = loadedAt;
        }
    }

    private static final class ParsedEveningRule {
        private final String gradeName;
        private final String morningStartText;
        private final String morningEndText;
        private final String normalStartText;
        private final String normalEndText;
        private final String returnStartText;
        private final String returnEndText;
        private final LocalTime morningStart;
        private final LocalTime morningEnd;
        private final LocalTime normalStart;
        private final LocalTime normalEnd;
        private final LocalTime returnStart;
        private final LocalTime returnEnd;

        private ParsedEveningRule(String gradeName,
                                  String morningStartText,
                                  String morningEndText,
                                  String normalStartText,
                                  String normalEndText,
                                  String returnStartText,
                                  String returnEndText,
                                  LocalTime morningStart,
                                  LocalTime morningEnd,
                                  LocalTime normalStart,
                                  LocalTime normalEnd,
                                  LocalTime returnStart,
                                  LocalTime returnEnd) {
            this.gradeName = gradeName;
            this.morningStartText = morningStartText;
            this.morningEndText = morningEndText;
            this.normalStartText = normalStartText;
            this.normalEndText = normalEndText;
            this.returnStartText = returnStartText;
            this.returnEndText = returnEndText;
            this.morningStart = morningStart;
            this.morningEnd = morningEnd;
            this.normalStart = normalStart;
            this.normalEnd = normalEnd;
            this.returnStart = returnStart;
            this.returnEnd = returnEnd;
        }

        private static ParsedEveningRule from(EveningCheckinRuleRowVO row) {
            String morningStartText = StringUtils.defaultIfBlank(row.getMorningStart(), "06:00");
            String morningEndText = StringUtils.defaultIfBlank(row.getMorningEnd(), "07:10");
            return new ParsedEveningRule(
                    row.getGradeName(),
                    morningStartText,
                    morningEndText,
                    row.getNormalStart(),
                    row.getNormalEnd(),
                    row.getReturnStart(),
                    row.getReturnEnd(),
                    LocalTime.parse(morningStartText),
                    LocalTime.parse(morningEndText),
                    LocalTime.parse(row.getNormalStart()),
                    LocalTime.parse(row.getNormalEnd()),
                    LocalTime.parse(row.getReturnStart()),
                    LocalTime.parse(row.getReturnEnd())
            );
        }
    }
}

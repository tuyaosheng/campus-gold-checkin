package com.school.checkin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.checkin.common.ErrorCode;
import com.school.checkin.dto.CheckInRevokeRequest;
import com.school.checkin.dto.CheckInSubmitRequest;
import com.school.checkin.entity.CheckInRecord;
import com.school.checkin.entity.ClassInfo;
import com.school.checkin.entity.StudentInfo;
import com.school.checkin.entity.SysSetting;
import com.school.checkin.enums.TimeSlot;
import com.school.checkin.exception.BusinessException;
import com.school.checkin.mapper.CheckInRecordMapper;
import com.school.checkin.mapper.ClassInfoMapper;
import com.school.checkin.mapper.StudentInfoMapper;
import com.school.checkin.mapper.SysSettingMapper;
import com.school.checkin.mapper.SysUserMapper;
import com.school.checkin.service.CheckInService;
import com.school.checkin.utils.CheckInTimeUtil;
import com.school.checkin.utils.RankRuleUtil;
import com.school.checkin.vo.CheckInResultVO;
import com.school.checkin.vo.ClassRosterVO;
import com.school.checkin.vo.LeaderboardVO;
import com.school.checkin.vo.SchoolTopStudentVO;
import com.school.checkin.vo.StudentQualityRecordVO;
import com.school.checkin.vo.StudentSimpleVO;
import com.school.checkin.vo.StudentWeeklyLeaderboardVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class CheckInServiceImpl implements CheckInService {

    private static final long SETTING_CACHE_MILLIS = 10_000L;

    private final ClassInfoMapper classInfoMapper;
    private final StudentInfoMapper studentInfoMapper;
    private final CheckInRecordMapper checkInRecordMapper;
    private final SysUserMapper sysUserMapper;
    private final SysSettingMapper sysSettingMapper;
    private final CheckInTimeUtil checkInTimeUtil;
    private final Map<String, CachedSettingValue> settingCache = new ConcurrentHashMap<>();

    public CheckInServiceImpl(ClassInfoMapper classInfoMapper,
                              StudentInfoMapper studentInfoMapper,
                              CheckInRecordMapper checkInRecordMapper,
                              SysUserMapper sysUserMapper,
                              SysSettingMapper sysSettingMapper,
                              CheckInTimeUtil checkInTimeUtil) {
        this.classInfoMapper = classInfoMapper;
        this.studentInfoMapper = studentInfoMapper;
        this.checkInRecordMapper = checkInRecordMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysSettingMapper = sysSettingMapper;
        this.checkInTimeUtil = checkInTimeUtil;
    }

    @Override
    public ClassRosterVO getRosterByUniqueCode(String uniqueCode) {
        ClassInfo classInfo = getEnabledClassByCode(uniqueCode);
        TimeSlot displayTimeSlot = checkInTimeUtil.resolveDisplayTimeSlot(classInfo.getGradeName());
        boolean interactionAllowed = checkInTimeUtil.isCheckInTimeNow(classInfo.getGradeName());
        List<StudentInfo> students = studentInfoMapper.selectList(new LambdaQueryWrapper<StudentInfo>()
                .eq(StudentInfo::getClassId, classInfo.getId())
                .eq(StudentInfo::getEnabled, 1));
        students.sort(Comparator
                .comparing((StudentInfo item) -> resolveDisplayOrderNo(item.getDisplayOrderNo(), item.getStudentNo()), Comparator.nullsLast(Integer::compareTo))
                .thenComparing(StudentInfo::getStudentNo, Comparator.nullsLast(String::compareTo))
                .thenComparing(StudentInfo::getId, Comparator.nullsLast(Long::compareTo)));
        List<CheckInRecord> records = checkInRecordMapper.selectList(new LambdaQueryWrapper<CheckInRecord>()
                .eq(CheckInRecord::getClassId, classInfo.getId())
                .eq(CheckInRecord::getCheckinDate, LocalDate.now())
                .eq(CheckInRecord::getTimeSlot, displayTimeSlot.name()));
        Map<Long, CheckInRecord> recordMap = new HashMap<>();
        for (CheckInRecord record : records) {
            recordMap.put(record.getStudentId(), record);
        }
        ClassRosterVO rosterVO = new ClassRosterVO();
        rosterVO.setClassId(classInfo.getId());
        rosterVO.setClassName(classInfo.getClassName());
        rosterVO.setGradeName(classInfo.getGradeName());
        String teacherName = classInfo.getTeacherName();
        if (StringUtils.isBlank(teacherName) && classInfo.getId() != null) {
            teacherName = sysUserMapper.selectTeacherNameByClassId(classInfo.getId());
            if (StringUtils.isNotBlank(teacherName)) {
                classInfo.setTeacherName(teacherName);
                classInfoMapper.updateById(classInfo);
            }
        }
        rosterVO.setTeacherName(teacherName);
        rosterVO.setUniqueCode(classInfo.getUniqueCode());
        rosterVO.setTimeSlot(displayTimeSlot.name());
        rosterVO.setTimeSlotLabel(TimeSlot.MORNING.equals(displayTimeSlot) ? "早上打卡" : "晚上打卡");
        rosterVO.setInteractionAllowed(interactionAllowed);
        rosterVO.setInteractionMessage(interactionAllowed ? "" : checkInTimeUtil.getPublicAccessMessage(classInfo.getGradeName()));
        rosterVO.setCurrentWeek(calculateCurrentWeek(getSemesterStartDate()));
        rosterVO.setDailyQuote(resolveTodayQuote(displayTimeSlot));
        rosterVO.setRankRuleTip(RankRuleUtil.buildRuleTip(RankRuleUtil.deserialize(getSettingValue("rank_rule_settings"))));
        SchoolTopStudentVO topStudent = resolveGradeTopStudent(classInfo.getGradeName());
        rosterVO.setSchoolTopStudentName(maskName(topStudent == null ? null : topStudent.getStudentName()));
        rosterVO.setSchoolTopStudentClassName(topStudent == null || StringUtils.isBlank(topStudent.getClassName()) ? "--" : topStudent.getClassName());
        rosterVO.setSchoolTopStudentCount(topStudent == null ? 0 : topStudent.getTotalCheckinCount());
        applyLeaderboards(rosterVO, classInfo.getId(), classInfo.getGradeName());
        rosterVO.setStudents(students.stream().map(student -> {
            StudentSimpleVO vo = new StudentSimpleVO();
            vo.setStudentId(student.getId());
            vo.setStudentNo(student.getStudentNo());
            vo.setStudentName(student.getStudentName());
            vo.setDisplayNo(resolveDisplayOrderNo(student.getDisplayOrderNo(), student.getStudentNo()));
            CheckInRecord record = recordMap.get(student.getId());
            vo.setCheckedIn(record != null);
            if (record != null) {
                vo.setCheckinTime(record.getCheckinTime().format(DateTimeFormatter.ofPattern("HH:mm")));
            }
            return vo;
        }).collect(Collectors.toList()));
        return rosterVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckInResultVO submitCheckIn(CheckInSubmitRequest request) {
        ClassInfo classInfo = getEnabledClassByCode(request.getUniqueCode());
        TimeSlot timeSlot = checkInTimeUtil.getCurrentTimeSlot(classInfo.getGradeName());
        StudentInfo student = studentInfoMapper.selectOne(new LambdaQueryWrapper<StudentInfo>()
                .eq(StudentInfo::getId, request.getStudentId())
                .eq(StudentInfo::getClassId, classInfo.getId())
                .eq(StudentInfo::getEnabled, 1)
                .last("limit 1"));
        if (student == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学生不存在或不属于当前班级");
        }

        LocalDate today = LocalDate.now();
        Long exists = checkInRecordMapper.selectCount(new LambdaQueryWrapper<CheckInRecord>()
                .eq(CheckInRecord::getStudentId, student.getId())
                .eq(CheckInRecord::getCheckinDate, today)
                .eq(CheckInRecord::getTimeSlot, timeSlot.name()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该学生当前时段已打卡，请勿重复提交");
        }

        LocalDateTime now = LocalDateTime.now();
        CheckInRecord record = new CheckInRecord();
        record.setClassId(classInfo.getId());
        record.setStudentId(student.getId());
        record.setCheckinDate(today);
        record.setTimeSlot(timeSlot.name());
        record.setCheckinTime(now);
        checkInRecordMapper.insert(record);

        CheckInResultVO resultVO = new CheckInResultVO();
        resultVO.setRecordId(record.getId());
        resultVO.setStudentId(student.getId());
        resultVO.setStudentName(student.getStudentName());
        resultVO.setTimeSlot(timeSlot.name());
        resultVO.setCheckinTime(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return resultVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeCheckIn(CheckInRevokeRequest request) {
        ClassInfo classInfo = getEnabledClassByCode(request.getUniqueCode());
        TimeSlot timeSlot = checkInTimeUtil.getCurrentTimeSlot(classInfo.getGradeName());
        StudentInfo student = studentInfoMapper.selectOne(new LambdaQueryWrapper<StudentInfo>()
                .eq(StudentInfo::getId, request.getStudentId())
                .eq(StudentInfo::getClassId, classInfo.getId())
                .eq(StudentInfo::getEnabled, 1)
                .last("limit 1"));
        if (student == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学生不存在或不属于当前班级");
        }
        CheckInRecord record = checkInRecordMapper.selectOne(new LambdaQueryWrapper<CheckInRecord>()
                .eq(CheckInRecord::getStudentId, student.getId())
                .eq(CheckInRecord::getCheckinDate, LocalDate.now())
                .eq(CheckInRecord::getTimeSlot, timeSlot.name())
                .orderByDesc(CheckInRecord::getId)
                .last("limit 1"));
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前时段暂无可撤销的打卡记录");
        }
        checkInRecordMapper.deleteById(record.getId());
    }

    @Override
    public List<LeaderboardVO> getPublicLeaderboard(LocalDate date, String timeSlot) {
        String fallbackTimeSlot = checkInTimeUtil.resolveDisplayTimeSlot(null).name();
        String normalizedTimeSlot = StringUtils.defaultIfBlank(timeSlot, fallbackTimeSlot).toUpperCase();
        if (!TimeSlot.MORNING.name().equals(normalizedTimeSlot) && !TimeSlot.EVENING.name().equals(normalizedTimeSlot)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "timeSlot 仅支持 MORNING 或 EVENING");
        }
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        return checkInRecordMapper.selectTodayLeaderboard(targetDate, normalizedTimeSlot, null, null);
    }

    private ClassInfo getEnabledClassByCode(String uniqueCode) {
        ClassInfo classInfo = classInfoMapper.selectOne(new LambdaQueryWrapper<ClassInfo>()
                .eq(ClassInfo::getUniqueCode, uniqueCode)
                .eq(ClassInfo::getEnabled, 1)
                .last("limit 1"));
        if (classInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "班级特征码无效");
        }
        return classInfo;
    }

    private LocalDate getSemesterStartDate() {
        String value = getSettingValue("semester_start_date");
        if (StringUtils.isBlank(value)) {
            return LocalDate.now();
        }
        return LocalDate.parse(value);
    }

    private int calculateCurrentWeek(LocalDate startDate) {
        LocalDate today = LocalDate.now();
        if (today.isBefore(startDate)) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(startDate, today);
        return (int) (days / 7) + 1;
    }

    private void applyLeaderboards(ClassRosterVO rosterVO, Long classId, String gradeName) {
        LocalDate semesterStartDate = getSemesterStartDate();
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate monthStart = today.withDayOfMonth(1);

        List<StudentWeeklyLeaderboardVO> totalRows = buildLeaderboard(classId, gradeName, semesterStartDate);
        Map<Long, StudentWeeklyLeaderboardVO> totalByStudentId = totalRows.stream()
                .collect(Collectors.toMap(StudentWeeklyLeaderboardVO::getStudentId, row -> row, (a, b) -> a));

        List<StudentWeeklyLeaderboardVO> weekRows = buildLeaderboard(classId, gradeName, weekStart.isAfter(semesterStartDate) ? weekStart : semesterStartDate);
        List<StudentWeeklyLeaderboardVO> monthRows = buildLeaderboard(classId, gradeName, monthStart.isAfter(semesterStartDate) ? monthStart : semesterStartDate);
        applyRankTitlesFromTotal(weekRows, totalByStudentId);
        applyRankTitlesFromTotal(monthRows, totalByStudentId);

        rosterVO.setTotalLeaderboard(totalRows);
        rosterVO.setWeekLeaderboard(weekRows);
        rosterVO.setMonthLeaderboard(monthRows);
    }

    private List<StudentWeeklyLeaderboardVO> buildLeaderboard(Long classId, String gradeName, LocalDate rangeStartDate) {
        if (classId == null) {
            return new ArrayList<>();
        }
        LocalDateTime startTime = rangeStartDate.atStartOfDay();
        LocalDateTime endTime = LocalDate.now().plusDays(1).atStartOfDay();
        List<StudentQualityRecordVO> records = checkInRecordMapper.selectStudentQualityRecords(startTime, endTime, classId, null);
        Map<Long, StudentWeeklyLeaderboardVO> grouped = new HashMap<>();
        for (StudentQualityRecordVO record : records) {
            StudentWeeklyLeaderboardVO row = grouped.computeIfAbsent(record.getStudentId(), key -> {
                StudentWeeklyLeaderboardVO item = new StudentWeeklyLeaderboardVO();
                item.setStudentId(record.getStudentId());
                item.setStudentNo(record.getStudentNo());
                item.setStudentName(record.getStudentName());
                item.setTotalCheckinCount(0);
                item.setQualityScore(0D);
                return item;
            });
            row.setTotalCheckinCount((row.getTotalCheckinCount() == null ? 0 : row.getTotalCheckinCount()) + 1);
            double score = (row.getQualityScore() == null ? 0D : row.getQualityScore())
                    + checkInTimeUtil.calculateQualityScore(gradeName, record.getTimeSlot(), LocalDateTime.parse(record.getCheckinTime(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            row.setQualityScore(Math.round(score * 100D) / 100D);
        }
        List<StudentWeeklyLeaderboardVO> rows = grouped.values().stream()
                .sorted(Comparator
                        .comparing(StudentWeeklyLeaderboardVO::getTotalCheckinCount, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(StudentWeeklyLeaderboardVO::getQualityScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(StudentWeeklyLeaderboardVO::getStudentNo, Comparator.nullsLast(String::compareTo))
                        .thenComparing(StudentWeeklyLeaderboardVO::getStudentId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.toList());
        applyRankTitles(rows);
        return rows;
    }

    private void applyRankTitles(List<StudentWeeklyLeaderboardVO> rows) {
        com.school.checkin.vo.RankSettingVO rankSetting = RankRuleUtil.deserialize(getSettingValue("rank_rule_settings"));
        String mode = RankRuleUtil.normalizeMode(rankSetting.getMode());
        for (int i = 0; i < rows.size(); i++) {
            StudentWeeklyLeaderboardVO row = rows.get(i);
            RankRuleUtil.RankResult rankResult = RankRuleUtil.MODE_PERCENT.equals(mode)
                    ? RankRuleUtil.resolveByPercent(i, rows.size(), rankSetting)
                    : RankRuleUtil.resolveByCount(row.getTotalCheckinCount() == null ? 0 : row.getTotalCheckinCount(), rankSetting);
            row.setRankTitle(rankResult.getTitle());
            row.setRankCssCode(rankResult.getCssCode());
        }
    }

    private void applyRankTitlesFromTotal(List<StudentWeeklyLeaderboardVO> rows, Map<Long, StudentWeeklyLeaderboardVO> totalByStudentId) {
        for (StudentWeeklyLeaderboardVO row : rows) {
            StudentWeeklyLeaderboardVO totalRow = totalByStudentId.get(row.getStudentId());
            if (totalRow != null) {
                row.setRankTitle(totalRow.getRankTitle());
                row.setRankCssCode(totalRow.getRankCssCode());
            }
        }
    }

    private SchoolTopStudentVO resolveGradeTopStudent(String gradeName) {
        LocalDate semesterStartDate = getSemesterStartDate();
        LocalDateTime startTime = semesterStartDate.atStartOfDay();
        LocalDateTime endTime = LocalDate.now().plusDays(1).atStartOfDay();
        List<StudentQualityRecordVO> records = checkInRecordMapper.selectStudentQualityRecords(startTime, endTime, null, gradeName);
        Map<Long, SchoolTopStudentVO> grouped = new HashMap<>();
        Map<Long, Double> qualityMap = new HashMap<>();
        for (StudentQualityRecordVO record : records) {
            SchoolTopStudentVO row = grouped.computeIfAbsent(record.getStudentId(), key -> {
                SchoolTopStudentVO item = new SchoolTopStudentVO();
                item.setStudentId(record.getStudentId());
                item.setStudentName(record.getStudentName());
                item.setGradeName(record.getGradeName());
                item.setClassName(record.getClassName());
                item.setTotalCheckinCount(0);
                return item;
            });
            row.setTotalCheckinCount((row.getTotalCheckinCount() == null ? 0 : row.getTotalCheckinCount()) + 1);
            double quality = qualityMap.getOrDefault(record.getStudentId(), 0D)
                    + checkInTimeUtil.calculateQualityScore(record.getGradeName(), record.getTimeSlot(), LocalDateTime.parse(record.getCheckinTime(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            qualityMap.put(record.getStudentId(), Math.round(quality * 100D) / 100D);
        }
        return grouped.values().stream()
                .sorted(Comparator
                        .comparing(SchoolTopStudentVO::getTotalCheckinCount, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing((SchoolTopStudentVO item) -> qualityMap.getOrDefault(item.getStudentId(), 0D), Comparator.reverseOrder())
                        .thenComparing(SchoolTopStudentVO::getClassName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(SchoolTopStudentVO::getStudentId, Comparator.nullsLast(Long::compareTo)))
                .findFirst()
                .orElse(null);
    }

    private Integer resolveDisplayOrderNo(Integer displayOrderNo, String studentNo) {
        if (displayOrderNo != null) {
            return displayOrderNo;
        }
        String digits = StringUtils.defaultString(studentNo).replaceAll("\\D", "");
        if (StringUtils.isBlank(digits)) {
            return null;
        }
        if (digits.length() == 1) {
            return Integer.parseInt(digits);
        }
        return Integer.parseInt(digits.substring(digits.length() - 2));
    }

    private String maskName(String name) {
        if (StringUtils.isBlank(name)) {
            return "暂无";
        }
        String trimmed = name.trim();
        if (trimmed.length() == 1) {
            return trimmed;
        }
        if (trimmed.length() == 2) {
            return trimmed.charAt(0) + "*";
        }
        return trimmed.substring(0, 1) + StringUtils.repeat('*', trimmed.length() - 2) + trimmed.substring(trimmed.length() - 1);
    }

    private String resolveTodayQuote(TimeSlot timeSlot) {
        String quoteKey = TimeSlot.EVENING.equals(timeSlot) ? "daily_quotes_evening" : "daily_quotes_morning";
        String startIndexKey = TimeSlot.EVENING.equals(timeSlot) ? "daily_quotes_evening_start_index" : "daily_quotes_morning_start_index";
        String startDateKey = TimeSlot.EVENING.equals(timeSlot) ? "daily_quotes_evening_start_date" : "daily_quotes_morning_start_date";
        List<String> quotes = Arrays.stream(StringUtils.defaultString(getSettingValue(quoteKey)).split("\\r?\\n"))
                .map(StringUtils::trimToEmpty)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
        if (quotes.isEmpty()) {
            return "我相信——我的坚持，可以成就更好的自己！";
        }
        int startIndex = readQuoteStartIndex(startIndexKey, quotes.size());
        LocalDate startDate = readQuoteStartDate(startDateKey);
        long offsetDays = ChronoUnit.DAYS.between(startDate, LocalDate.now());
        int todayIndex = (int) Math.floorMod((startIndex - 1) + offsetDays, quotes.size());
        return quotes.get(todayIndex);
    }

    private String getSettingValue(String key) {
        long now = System.currentTimeMillis();
        CachedSettingValue cached = settingCache.get(key);
        if (cached != null && now - cached.loadedAt <= SETTING_CACHE_MILLIS) {
            return cached.value;
        }
        SysSetting setting = sysSettingMapper.selectOne(new LambdaQueryWrapper<SysSetting>()
                .eq(SysSetting::getSettingKey, key)
                .last("limit 1"));
        String value = setting == null ? "" : StringUtils.defaultString(setting.getSettingValue());
        settingCache.put(key, new CachedSettingValue(value, now));
        return value;
    }

    private int readQuoteStartIndex(String settingKey, int quoteCount) {
        if (quoteCount <= 0) {
            return 1;
        }
        String value = getSettingValue(settingKey);
        try {
            int parsed = Integer.parseInt(StringUtils.trimToEmpty(value));
            return Math.max(1, Math.min(parsed, quoteCount));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private LocalDate readQuoteStartDate(String settingKey) {
        String value = getSettingValue(settingKey);
        if (StringUtils.isBlank(value)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(StringUtils.trim(value));
        } catch (Exception ex) {
            return LocalDate.now();
        }
    }
    private static final class CachedSettingValue {
        private final String value;
        private final long loadedAt;

        private CachedSettingValue(String value, long loadedAt) {
            this.value = value;
            this.loadedAt = loadedAt;
        }
    }
}

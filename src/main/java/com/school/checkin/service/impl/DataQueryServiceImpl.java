package com.school.checkin.service.impl;

import com.school.checkin.common.ErrorCode;
import com.school.checkin.entity.CheckInRecord;
import com.school.checkin.entity.ClassInfo;
import com.school.checkin.entity.StudentInfo;
import com.school.checkin.entity.SysSetting;
import com.school.checkin.enums.RoleCode;
import com.school.checkin.enums.TimeSlot;
import com.school.checkin.exception.BusinessException;
import com.school.checkin.mapper.ClassInfoMapper;
import com.school.checkin.mapper.CheckInRecordMapper;
import com.school.checkin.mapper.StudentInfoMapper;
import com.school.checkin.mapper.SysSettingMapper;
import com.school.checkin.security.LoginUser;
import com.school.checkin.security.UserContext;
import com.school.checkin.service.DataQueryService;
import com.school.checkin.utils.CheckInTimeUtil;
import com.school.checkin.utils.ClassSortUtil;
import com.school.checkin.utils.RankRuleUtil;
import com.school.checkin.vo.ClassAccessVO;
import com.school.checkin.vo.CorrectionRecordVO;
import com.school.checkin.vo.ExportCheckInRow;
import com.school.checkin.vo.ExportStudentScopeVO;
import com.school.checkin.vo.LeaderboardVO;
import com.school.checkin.vo.OverviewStatsVO;
import com.school.checkin.vo.StudentQualityLeaderboardVO;
import com.school.checkin.vo.StudentQualityRecordVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class DataQueryServiceImpl implements DataQueryService {

    private static final long SETTING_CACHE_MILLIS = 10_000L;

    private final CheckInRecordMapper checkInRecordMapper;
    private final SysSettingMapper sysSettingMapper;
    private final ClassInfoMapper classInfoMapper;
    private final StudentInfoMapper studentInfoMapper;
    private final CheckInTimeUtil checkInTimeUtil;
    private final Map<String, CachedSettingValue> settingCache = new ConcurrentHashMap<>();

    public DataQueryServiceImpl(CheckInRecordMapper checkInRecordMapper,
                                SysSettingMapper sysSettingMapper,
                                ClassInfoMapper classInfoMapper,
                                StudentInfoMapper studentInfoMapper,
                                CheckInTimeUtil checkInTimeUtil) {
        this.checkInRecordMapper = checkInRecordMapper;
        this.sysSettingMapper = sysSettingMapper;
        this.classInfoMapper = classInfoMapper;
        this.studentInfoMapper = studentInfoMapper;
        this.checkInTimeUtil = checkInTimeUtil;
    }

    @Override
    public List<LeaderboardVO> getLeaderboard(LocalDate date, String timeSlot) {
        LoginUser loginUser = requireLoginUser();
        String normalizedTimeSlot = StringUtils.defaultIfBlank(timeSlot, "MORNING").toUpperCase();
        if (!"MORNING".equals(normalizedTimeSlot) && !"EVENING".equals(normalizedTimeSlot)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "timeSlot 仅支持 MORNING 或 EVENING");
        }
        String gradeName = RoleCode.GRADE_LEADER.equals(loginUser.getRoleCode()) ? loginUser.getGradeName() : null;
        Long classId = RoleCode.TEACHER.equals(loginUser.getRoleCode()) ? loginUser.getClassId() : null;
        List<LeaderboardVO> rows = checkInRecordMapper.selectTodayLeaderboard(date, normalizedTimeSlot, gradeName, classId);
        rows.sort(Comparator
                .comparing(LeaderboardVO::getAttendanceRate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(LeaderboardVO::getCheckedStudents, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ClassSortUtil.comparingGradeAndClass(LeaderboardVO::getGradeName, LeaderboardVO::getClassName)));
        return rows;
    }

    @Override
    public List<StudentQualityLeaderboardVO> getStudentQualityLeaderboard(Long requestedClassId) {
        LoginUser loginUser = requireLoginUser();
        if (!RoleCode.ADMIN.equals(loginUser.getRoleCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可查看质量分榜");
        }
        LocalDateTime startTime = getSemesterStartDate().atStartOfDay();
        LocalDateTime endTime = LocalDate.now().plusDays(1).atStartOfDay();
        String gradeName = RoleCode.GRADE_LEADER.equals(loginUser.getRoleCode()) ? loginUser.getGradeName() : null;
        Long classId = resolveScopedClassId(loginUser, requestedClassId);
        List<StudentQualityRecordVO> records = checkInRecordMapper.selectStudentQualityRecords(startTime, endTime, classId, gradeName);
        Map<Long, StudentQualityLeaderboardVO> grouped = new HashMap<>();
        for (StudentQualityRecordVO record : records) {
            StudentQualityLeaderboardVO row = grouped.computeIfAbsent(record.getStudentId(), key -> {
                StudentQualityLeaderboardVO item = new StudentQualityLeaderboardVO();
                item.setStudentId(record.getStudentId());
                item.setGradeName(record.getGradeName());
                item.setClassName(record.getClassName());
                item.setStudentNo(record.getStudentNo());
                item.setStudentName(record.getStudentName());
                item.setTotalCheckinCount(0);
                item.setQualityScore(0D);
                return item;
            });
            row.setTotalCheckinCount((row.getTotalCheckinCount() == null ? 0 : row.getTotalCheckinCount()) + 1);
            double score = (row.getQualityScore() == null ? 0D : row.getQualityScore())
                    + checkInTimeUtil.calculateQualityScore(record.getGradeName(), record.getTimeSlot(), LocalDateTime.parse(record.getCheckinTime(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            row.setQualityScore(Math.round(score * 100D) / 100D);
        }
        List<StudentQualityLeaderboardVO> rows = new ArrayList<>(grouped.values());
        rows.sort(Comparator
                .comparing(StudentQualityLeaderboardVO::getTotalCheckinCount, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(StudentQualityLeaderboardVO::getQualityScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ClassSortUtil.comparingGradeAndClass(StudentQualityLeaderboardVO::getGradeName, StudentQualityLeaderboardVO::getClassName))
                .thenComparing(StudentQualityLeaderboardVO::getStudentNo, Comparator.nullsLast(String::compareTo))
                .thenComparing(StudentQualityLeaderboardVO::getStudentId, Comparator.nullsLast(Long::compareTo)));
        applyStudentQualityRanks(rows);
        return rows;
    }

    @Override
    public List<ExportCheckInRow> getExportRows(String scope, LocalDate date, Integer weekIndex, String month) {
        LoginUser loginUser = requireLoginUser();
        LocalDate anchorDate = date == null ? LocalDate.now() : date;
        LocalDate semesterStartDate = getSemesterStartDate();
        LocalDateTime startTime;
        LocalDateTime endTime;

        String normalizedScope = StringUtils.defaultIfBlank(scope, "daily").toLowerCase();
        switch (normalizedScope) {
            case "weekly":
                if (weekIndex == null || weekIndex < 1) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "按周导出时 weekIndex 必须大于等于 1");
                }
                startTime = semesterStartDate.plusWeeks(weekIndex - 1L).atStartOfDay();
                endTime = startTime.plusDays(7);
                break;
            case "monthly":
                if (StringUtils.isBlank(month)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "按月导出时 month 不能为空，格式为 yyyy-MM");
                }
                YearMonth yearMonth;
                try {
                    yearMonth = YearMonth.parse(month);
                } catch (Exception ex) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "month 格式错误，应为 yyyy-MM");
                }
                startTime = yearMonth.atDay(1).atStartOfDay();
                endTime = startTime.plusMonths(1);
                break;
            case "semester":
                startTime = semesterStartDate.atStartOfDay();
                endTime = LocalDate.now().plusDays(1).atStartOfDay();
                break;
            case "daily":
                startTime = anchorDate.atStartOfDay();
                endTime = anchorDate.plusDays(1).atStartOfDay();
                break;
            default:
                throw new BusinessException(ErrorCode.BAD_REQUEST, "scope 仅支持 daily、weekly、monthly、semester");
        }

        String gradeName = RoleCode.GRADE_LEADER.equals(loginUser.getRoleCode()) ? loginUser.getGradeName() : null;
        Long classId = RoleCode.TEACHER.equals(loginUser.getRoleCode()) ? loginUser.getClassId() : null;
        List<ExportCheckInRow> checkedRows = checkInRecordMapper.selectExportRows(startTime, endTime, gradeName, classId);
        Map<String, ExportCheckInRow> checkedMap = checkedRows.stream().collect(Collectors.toMap(
                row -> {
                    normalizeExportRow(row);
                    row.setCheckinStatus("已打卡");
                    return buildExportKey(row.getCheckinDate(), row.getStudentId(), row.getTimeSlot());
                },
                row -> row,
                (left, right) -> left
        ));
        List<ExportStudentScopeVO> students = studentInfoMapper.selectExportStudents(gradeName, classId);
        List<LocalDate> exportDates = resolveExportDates(normalizedScope, anchorDate, startTime.toLocalDate(), endTime.toLocalDate().minusDays(1));
        List<ExportCheckInRow> rows = new ArrayList<>();
        for (LocalDate exportDate : exportDates) {
            for (ExportStudentScopeVO student : students) {
                rows.add(buildExportRow(exportDate, student, "MORNING", checkedMap.get(buildExportKey(String.valueOf(exportDate), student.getStudentId(), "早上"))));
                rows.add(buildExportRow(exportDate, student, "EVENING", checkedMap.get(buildExportKey(String.valueOf(exportDate), student.getStudentId(), "晚上"))));
            }
        }
        rows.sort(ClassSortUtil.comparingGradeAndClass(ExportCheckInRow::getGradeName, ExportCheckInRow::getClassName)
                .thenComparing(ExportCheckInRow::getCheckinDate, Comparator.nullsLast(String::compareTo))
                .thenComparing(ExportCheckInRow::getTimeSlot, Comparator.nullsLast(String::compareTo))
                .thenComparing(ExportCheckInRow::getCheckinTime, Comparator.nullsLast(String::compareTo))
                .thenComparing(ExportCheckInRow::getStudentNo, Comparator.nullsLast(String::compareTo))
                .thenComparing(ExportCheckInRow::getStudentName, Comparator.nullsLast(String::compareTo)));
        return rows;
    }

    @Override
    public OverviewStatsVO getOverviewStats() {
        List<ClassAccessVO> visibleClasses = getVisibleClasses();
        OverviewStatsVO vo = new OverviewStatsVO();
        vo.setClassCount(visibleClasses.size());
        vo.setStudentCount(visibleClasses.stream().mapToInt(item -> item.getStudentCount() == null ? 0 : item.getStudentCount()).sum());
        if (visibleClasses.size() == 1) {
            ClassAccessVO item = visibleClasses.get(0);
            vo.setPrimaryClassLabel(StringUtils.defaultString(item.getGradeName()) + StringUtils.defaultString(item.getClassName()));
        }
        return vo;
    }

    @Override
    public List<ClassAccessVO> getVisibleClasses() {
        LoginUser loginUser = requireLoginUser();
        String gradeName = RoleCode.GRADE_LEADER.equals(loginUser.getRoleCode()) ? loginUser.getGradeName() : null;
        Long classId = RoleCode.TEACHER.equals(loginUser.getRoleCode()) ? loginUser.getClassId() : null;
        List<ClassAccessVO> rows = classInfoMapper.selectClassAccessListByScope(gradeName, classId);
        rows.sort(ClassSortUtil.comparingGradeAndClass(ClassAccessVO::getGradeName, ClassAccessVO::getClassName));
        return rows;
    }

    @Override
    public List<CorrectionRecordVO> getCorrectionRows(LocalDate date, String timeSlot, Long classId) {
        LoginUser loginUser = requireLoginUser();
        String normalizedTimeSlot = normalizeTimeSlot(timeSlot);
        Long scopedClassId = resolveScopedClassId(loginUser, classId);
        String scopedGradeName = resolveScopedGradeName(loginUser);
        List<CorrectionRecordVO> rows = checkInRecordMapper.selectCorrectionRows(date, normalizedTimeSlot, scopedGradeName, scopedClassId);
        rows.sort(ClassSortUtil.comparingGradeAndClass(CorrectionRecordVO::getGradeName, CorrectionRecordVO::getClassName)
                .thenComparing(CorrectionRecordVO::getStudentNo, Comparator.nullsLast(String::compareTo))
                .thenComparing(CorrectionRecordVO::getStudentName, Comparator.nullsLast(String::compareTo)));
        return rows;
    }

    @Override
    public void manualCheckIn(LocalDate date, String timeSlot, Long studentId, String checkinTime) {
        String normalizedTimeSlot = normalizeTimeSlot(timeSlot);
        StudentInfo student = studentInfoMapper.selectById(studentId);
        if (student == null || student.getEnabled() == null || student.getEnabled() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学生不存在");
        }
        ClassInfo classInfo = classInfoMapper.selectById(student.getClassId());
        assertClassPermission(requireLoginUser(), classInfo);
        CheckInRecord existing = checkInRecordMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CheckInRecord>()
                .eq(CheckInRecord::getStudentId, studentId)
                .eq(CheckInRecord::getCheckinDate, date)
                .eq(CheckInRecord::getTimeSlot, normalizedTimeSlot)
                .last("limit 1"));
        if (existing != null) {
            return;
        }
        TimeSlot slot = TimeSlot.valueOf(normalizedTimeSlot);
        LocalTime baseTime = resolveManualCorrectionTime(date, classInfo.getGradeName(), slot, checkinTime);
        CheckInRecord record = new CheckInRecord();
        record.setClassId(classInfo.getId());
        record.setStudentId(studentId);
        record.setCheckinDate(date);
        record.setTimeSlot(normalizedTimeSlot);
        record.setCheckinTime(LocalDateTime.of(date, baseTime));
        record.setCreatedAt(LocalDateTime.now());
        checkInRecordMapper.insert(record);
    }

    @Override
    public void manualRevoke(LocalDate date, String timeSlot, Long studentId) {
        String normalizedTimeSlot = normalizeTimeSlot(timeSlot);
        StudentInfo student = studentInfoMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学生不存在");
        }
        ClassInfo classInfo = classInfoMapper.selectById(student.getClassId());
        assertClassPermission(requireLoginUser(), classInfo);
        checkInRecordMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CheckInRecord>()
                .eq(CheckInRecord::getStudentId, studentId)
                .eq(CheckInRecord::getCheckinDate, date)
                .eq(CheckInRecord::getTimeSlot, normalizedTimeSlot));
    }

    public LocalDate getSemesterStartDate() {
        String value = getSettingValue("semester_start_date");
        if (StringUtils.isBlank(value)) {
            return LocalDate.now();
        }
        return LocalDate.parse(value);
    }

    private LoginUser requireLoginUser() {
        LoginUser loginUser = UserContext.get();
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或登录已失效");
        }
        if (RoleCode.TEACHER.equals(loginUser.getRoleCode()) && loginUser.getClassId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "教师账号未绑定班级");
        }
        if (RoleCode.GRADE_LEADER.equals(loginUser.getRoleCode()) && StringUtils.isBlank(loginUser.getGradeName())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "级长账号未绑定年级");
        }
        return loginUser;
    }

    private String normalizeTimeSlot(String timeSlot) {
        String normalizedTimeSlot = StringUtils.defaultIfBlank(timeSlot, "MORNING").toUpperCase();
        if (!"MORNING".equals(normalizedTimeSlot) && !"EVENING".equals(normalizedTimeSlot)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "timeSlot 仅支持 MORNING 或 EVENING");
        }
        return normalizedTimeSlot;
    }

    private Long resolveScopedClassId(LoginUser loginUser, Long classId) {
        if (RoleCode.TEACHER.equals(loginUser.getRoleCode())) {
            if (classId != null && !classId.equals(loginUser.getClassId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "仅可查看本班数据");
            }
            return loginUser.getClassId();
        }
        return classId;
    }

    private String resolveScopedGradeName(LoginUser loginUser) {
        return RoleCode.GRADE_LEADER.equals(loginUser.getRoleCode()) ? loginUser.getGradeName() : null;
    }

    private void assertClassPermission(LoginUser loginUser, ClassInfo classInfo) {
        if (classInfo == null || classInfo.getEnabled() == null || classInfo.getEnabled() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "班级不存在");
        }
        if (RoleCode.TEACHER.equals(loginUser.getRoleCode()) && !classInfo.getId().equals(loginUser.getClassId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅可修改本班数据");
        }
        if (RoleCode.GRADE_LEADER.equals(loginUser.getRoleCode()) && !StringUtils.equals(classInfo.getGradeName(), loginUser.getGradeName())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅可修改本年级数据");
        }
    }

    private LocalTime resolveManualCorrectionTime(LocalDate date, String gradeName, TimeSlot timeSlot, String checkinTime) {
        if (StringUtils.isBlank(checkinTime)) {
            return checkInTimeUtil.resolveManualCheckinTime(date, gradeName, timeSlot);
        }
        try {
            return LocalTime.parse(checkinTime);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "补录时间格式错误，应为 HH:mm");
        }
    }

    private List<LocalDate> resolveExportDates(String scope, LocalDate anchorDate, LocalDate startDate, LocalDate endDate) {
        if ("daily".equals(scope)) {
            return java.util.Collections.singletonList(anchorDate);
        }
        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            dates.add(current);
            current = current.plusDays(1);
        }
        return dates;
    }

    private void normalizeExportRow(ExportCheckInRow row) {
        if ("MORNING".equalsIgnoreCase(row.getTimeSlot())) {
            row.setTimeSlot("早上");
        } else if ("EVENING".equalsIgnoreCase(row.getTimeSlot())) {
            row.setTimeSlot("晚上");
        }
    }

    private ExportCheckInRow buildExportRow(LocalDate exportDate, ExportStudentScopeVO student, String rawTimeSlot, ExportCheckInRow checkedRow) {
        ExportCheckInRow row = new ExportCheckInRow();
        row.setStudentId(student.getStudentId());
        row.setClassId(student.getClassId());
        row.setCheckinDate(String.valueOf(exportDate));
        row.setGradeName(student.getGradeName());
        row.setClassName(student.getClassName());
        row.setStudentName(student.getStudentName());
        row.setStudentNo(student.getStudentNo());
        row.setTimeSlot("MORNING".equals(rawTimeSlot) ? "早上" : "晚上");
        if (checkedRow != null) {
            row.setCheckinStatus("已打卡");
            row.setCheckinTime(checkedRow.getCheckinTime());
        } else {
            row.setCheckinStatus("未打卡");
            row.setCheckinTime("");
        }
        return row;
    }

    private void applyStudentQualityRanks(List<StudentQualityLeaderboardVO> rows) {
        com.school.checkin.vo.RankSettingVO rankSetting = RankRuleUtil.deserialize(getSettingValue("rank_rule_settings"));
        String mode = RankRuleUtil.normalizeMode(rankSetting.getMode());
        for (int i = 0; i < rows.size(); i++) {
            StudentQualityLeaderboardVO row = rows.get(i);
            RankRuleUtil.RankResult rankResult = RankRuleUtil.MODE_PERCENT.equals(mode)
                    ? RankRuleUtil.resolveByPercent(i, rows.size(), rankSetting)
                    : RankRuleUtil.resolveByCount(row.getTotalCheckinCount() == null ? 0 : row.getTotalCheckinCount(), rankSetting);
            row.setRankTitle(rankResult.getTitle());
        }
    }

    private String getSettingValue(String key) {
        long now = System.currentTimeMillis();
        CachedSettingValue cached = settingCache.get(key);
        if (cached != null && now - cached.loadedAt <= SETTING_CACHE_MILLIS) {
            return cached.value;
        }
        SysSetting setting = sysSettingMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysSetting>()
                .eq(SysSetting::getSettingKey, key)
                .last("limit 1"));
        String value = setting == null ? "" : StringUtils.defaultString(setting.getSettingValue());
        settingCache.put(key, new CachedSettingValue(value, now));
        return value;
    }

    private String buildExportKey(String checkinDate, Long studentId, String timeSlot) {
        return checkinDate + "|" + studentId + "|" + timeSlot;
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

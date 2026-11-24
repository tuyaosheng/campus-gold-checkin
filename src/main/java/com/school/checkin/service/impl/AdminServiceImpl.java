package com.school.checkin.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.checkin.common.ErrorCode;
import com.school.checkin.dto.ChangePasswordRequest;
import com.school.checkin.dto.CheckinDebugRequest;
import com.school.checkin.dto.DailyQuoteSaveRequest;
import com.school.checkin.dto.EveningCheckinRuleRowRequest;
import com.school.checkin.dto.EveningCheckinSettingSaveRequest;
import com.school.checkin.dto.ImportErrorExportRequest;
import com.school.checkin.dto.ImportStudentRosterRow;
import com.school.checkin.dto.ImportUserRosterRow;
import com.school.checkin.dto.LoginRequest;
import com.school.checkin.dto.RankSettingSaveRequest;
import com.school.checkin.dto.ResetPasswordRequest;
import com.school.checkin.dto.UpdateStudentOrderRequest;
import com.school.checkin.dto.UpdateUserAccountRequest;
import com.school.checkin.entity.ClassInfo;
import com.school.checkin.entity.StudentInfo;
import com.school.checkin.entity.SysSetting;
import com.school.checkin.entity.SysUser;
import com.school.checkin.enums.RoleCode;
import com.school.checkin.exception.BusinessException;
import com.school.checkin.mapper.ClassInfoMapper;
import com.school.checkin.mapper.StudentInfoMapper;
import com.school.checkin.mapper.SysSettingMapper;
import com.school.checkin.mapper.SysUserMapper;
import com.school.checkin.security.LoginUser;
import com.school.checkin.security.UserContext;
import com.school.checkin.service.AdminService;
import com.school.checkin.utils.JwtUtil;
import com.school.checkin.utils.NetworkUtil;
import com.school.checkin.utils.EveningCheckinSettingUtil;
import com.school.checkin.utils.RankRuleUtil;
import com.school.checkin.utils.PasswordUtil;
import com.school.checkin.utils.UniqueCodeGenerator;
import com.school.checkin.utils.ClassSortUtil;
import com.school.checkin.vo.ClassAccessVO;
import com.school.checkin.vo.AccessBaseUrlVO;
import com.school.checkin.vo.ClassLinkExportRow;
import com.school.checkin.vo.CheckinDebugVO;
import com.school.checkin.vo.DailyQuoteSettingVO;
import com.school.checkin.vo.EveningCheckinRuleRowVO;
import com.school.checkin.vo.EveningCheckinSettingVO;
import com.school.checkin.vo.ImportErrorVO;
import com.school.checkin.vo.ImportErrorExportRow;
import com.school.checkin.vo.ImportResultVO;
import com.school.checkin.vo.ImportStudentTemplateRow;
import com.school.checkin.vo.ImportUserTemplateRow;
import com.school.checkin.vo.LoginVO;
import com.school.checkin.vo.RankSettingVO;
import com.school.checkin.vo.SemesterSettingVO;
import com.school.checkin.vo.StudentOrderBindingVO;
import com.school.checkin.vo.UserAccountVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminServiceImpl implements AdminService {

    private static final String DEFAULT_PASSWORD = "dlzx";

    private final SysUserMapper sysUserMapper;
    private final ClassInfoMapper classInfoMapper;
    private final StudentInfoMapper studentInfoMapper;
    private final SysSettingMapper sysSettingMapper;
    private final JwtUtil jwtUtil;
    @Value("${server.port:8080}")
    private String serverPort;

    public AdminServiceImpl(SysUserMapper sysUserMapper,
                            ClassInfoMapper classInfoMapper,
                            StudentInfoMapper studentInfoMapper,
                            SysSettingMapper sysSettingMapper,
                            JwtUtil jwtUtil) {
        this.sysUserMapper = sysUserMapper;
        this.classInfoMapper = classInfoMapper;
        this.studentInfoMapper = studentInfoMapper;
        this.sysSettingMapper = sysSettingMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public LoginVO login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername())
                .eq(SysUser::getEnabled, 1)
                .last("limit 1"));
        if (user == null || !user.getPassword().equals(PasswordUtil.md5(request.getPassword()))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        return buildLoginVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO changeOwnPassword(ChangePasswordRequest request) {
        LoginUser loginUser = UserContext.get();
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或登录已失效");
        }
        SysUser user = sysUserMapper.selectById(loginUser.getUserId());
        if (user == null || user.getEnabled() == null || user.getEnabled() != 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "当前账号不可用");
        }
        if (!user.getPassword().equals(PasswordUtil.md5(request.getOldPassword()))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "原密码错误");
        }
        if (StringUtils.length(request.getNewPassword()) < 4) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码长度不能少于 4 位");
        }
        user.setPassword(PasswordUtil.md5(request.getNewPassword()));
        user.setMustChangePassword(0);
        sysUserMapper.updateById(user);
        return buildLoginVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetUserPassword(ResetPasswordRequest request) {
        requireAdmin();
        SysUser user = sysUserMapper.selectById(request.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        if (RoleCode.ADMIN.name().equals(user.getRoleCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不允许在此重置管理员账号");
        }
        user.setPassword(PasswordUtil.md5(DEFAULT_PASSWORD));
        user.setMustChangePassword(1);
        sysUserMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserAccount(UpdateUserAccountRequest request) {
        requireAdmin();
        SysUser user = sysUserMapper.selectById(request.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        if (RoleCode.ADMIN.name().equals(user.getRoleCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不允许在此修改管理员账号");
        }
        RoleCode roleCode;
        try {
            roleCode = RoleCode.valueOf(StringUtils.trimToEmpty(request.getRoleCode()).toUpperCase());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "角色仅支持 TEACHER、GRADE_LEADER、STAFF");
        }
        Long oldClassId = user.getClassId();
        user.setRealName(StringUtils.trimToEmpty(request.getRealName()));
        user.setEnabled(request.getEnabled());
        user.setRoleCode(roleCode.name());
        user.setGradeName(null);
        user.setClassId(null);

        if (RoleCode.TEACHER.equals(roleCode)) {
            if (request.getClassId() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "班主任账号必须绑定班级");
            }
            ClassInfo classInfo = classInfoMapper.selectById(request.getClassId());
            if (classInfo == null || classInfo.getEnabled() == null || classInfo.getEnabled() != 1) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "所选班级不存在或已停用");
            }
            user.setClassId(classInfo.getId());
            user.setGradeName(classInfo.getGradeName());
        } else if (RoleCode.GRADE_LEADER.equals(roleCode)) {
            if (StringUtils.isBlank(request.getGradeName())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "级长账号必须绑定年级");
            }
            user.setGradeName(StringUtils.trimToEmpty(request.getGradeName()));
        }

        sysUserMapper.updateById(user);
        refreshTeacherNameByClassId(oldClassId);
        refreshTeacherNameByClassId(user.getClassId());
    }

    @Override
    public ImportResultVO previewStudentRoster(MultipartFile file) {
        return processStudentRoster(file, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importStudentRoster(MultipartFile file) {
        return processStudentRoster(file, true);
    }

    @Override
    public ImportResultVO previewUserRoster(MultipartFile file) {
        return processUserRoster(file, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importUserRoster(MultipartFile file) {
        return processUserRoster(file, true);
    }

    @Override
    public List<ImportErrorExportRow> buildImportErrorRows(ImportErrorExportRequest request) {
        requireAdmin();
        return request.getErrors().stream().map(item -> {
            ImportErrorExportRow row = new ImportErrorExportRow();
            row.setImportType(request.getImportType());
            row.setRowNumber(item.getRowNumber());
            row.setReason(item.getReason());
            return row;
        }).collect(Collectors.toList());
    }

    private ImportResultVO processStudentRoster(MultipartFile file, boolean persist) {
        requireAdmin();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        List<ImportStudentRosterRow> rows;
        try {
            rows = EasyExcel.read(file.getInputStream())
                    .head(ImportStudentRosterRow.class)
                    .sheet()
                    .headRowNumber(4)
                    .doReadSync();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 读取失败");
        }
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "学生花名册内容为空");
        }

        Map<String, ClassInfo> classCache = new HashMap<>();
        ImportResultVO result = initImportResult(rows.size());
        result.setPreview(!persist);
        for (int i = 0; i < rows.size(); i++) {
            ImportStudentRosterRow row = rows.get(i);
            int rowNumber = i + 5;
            if (StringUtils.isAnyBlank(row.getGradeName(), row.getClassNo(), row.getStudentName())) {
                appendImportError(result, rowNumber, "缺少必填字段：年级、班号、学生姓名");
                result.setSkippedCount(result.getSkippedCount() + 1);
                continue;
            }
            try {
                ClassInfo classInfo = loadOrCreateClass(row, classCache, persist);
                int effect = loadOrCreateStudent(row, classInfo, persist);
                if (effect == 1) {
                    result.setCreateCount(result.getCreateCount() + 1);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } else if (effect == 2) {
                    result.setUpdateCount(result.getUpdateCount() + 1);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                }
            } catch (Exception ex) {
                appendImportError(result, rowNumber, ex.getMessage());
                result.setErrorCount(result.getErrorCount() + 1);
            }
        }

        if (persist) {
            for (ClassInfo classInfo : classCache.values()) {
                if (classInfo.getId() == null) {
                    continue;
                }
                Long studentCount = studentInfoMapper.selectCount(new LambdaQueryWrapper<StudentInfo>()
                        .eq(StudentInfo::getClassId, classInfo.getId())
                        .eq(StudentInfo::getEnabled, 1));
                classInfo.setStudentCount(studentCount == null ? 0 : studentCount.intValue());
                classInfoMapper.updateById(classInfo);
            }
        }
        return result;
    }

    private ImportResultVO processUserRoster(MultipartFile file, boolean persist) {
        requireAdmin();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        List<ImportUserRosterRow> rows;
        try {
            rows = EasyExcel.read(file.getInputStream())
                    .head(ImportUserRosterRow.class)
                    .sheet()
                    .headRowNumber(4)
                    .doReadSync();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 读取失败");
        }
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "教职工账号表内容为空");
        }

        ImportResultVO result = initImportResult(rows.size());
        result.setPreview(!persist);
        for (int i = 0; i < rows.size(); i++) {
            ImportUserRosterRow row = rows.get(i);
            int rowNumber = i + 5;
            if (StringUtils.isAnyBlank(row.getRealName(), row.getUsername(), row.getRoleCode())) {
                appendImportError(result, rowNumber, "缺少必填字段：姓名、登录账号、角色");
                result.setSkippedCount(result.getSkippedCount() + 1);
                continue;
            }
            try {
                int effect = loadOrCreateUser(row, persist);
                if (effect == 1) {
                    result.setCreateCount(result.getCreateCount() + 1);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } else if (effect == 2) {
                    result.setUpdateCount(result.getUpdateCount() + 1);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                }
            } catch (Exception ex) {
                appendImportError(result, rowNumber, ex.getMessage());
                result.setErrorCount(result.getErrorCount() + 1);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSemesterStartDate(LocalDate date) {
        requireAdmin();
        SysSetting setting = sysSettingMapper.selectOne(new LambdaQueryWrapper<SysSetting>()
                .eq(SysSetting::getSettingKey, "semester_start_date")
                .last("limit 1"));
        if (setting == null) {
            setting = new SysSetting();
            setting.setSettingKey("semester_start_date");
            setting.setRemark("学期开始日期");
            setting.setSettingValue(date.toString());
            sysSettingMapper.insert(setting);
            return;
        }
        setting.setSettingValue(date.toString());
        sysSettingMapper.updateById(setting);
    }

    @Override
    public SemesterSettingVO getSemesterSetting() {
        LoginUser loginUser = UserContext.get();
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或登录已失效");
        }
        SysSetting setting = sysSettingMapper.selectOne(new LambdaQueryWrapper<SysSetting>()
                .eq(SysSetting::getSettingKey, "semester_start_date")
                .last("limit 1"));
        LocalDate startDate = setting == null || StringUtils.isBlank(setting.getSettingValue())
                ? LocalDate.now()
                : LocalDate.parse(setting.getSettingValue());
        SemesterSettingVO vo = new SemesterSettingVO();
        vo.setSemesterStartDate(startDate.toString());
        vo.setCurrentWeek(calculateCurrentWeek(startDate));
        return vo;
    }

    @Override
    public DailyQuoteSettingVO getDailyQuoteSetting() {
        requireAdmin();
        DailyQuoteSettingVO vo = new DailyQuoteSettingVO();
        fillQuoteSettingVO(vo, "morning", "daily_quotes_morning", "daily_quotes_morning_start_index", "daily_quotes_morning_start_date");
        fillQuoteSettingVO(vo, "evening", "daily_quotes_evening", "daily_quotes_evening_start_index", "daily_quotes_evening_start_date");
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDailyQuotes(DailyQuoteSaveRequest request) {
        requireAdmin();
        List<String> morningQuotes = parseQuotes(request.getMorningQuotesText());
        List<String> eveningQuotes = parseQuotes(request.getEveningQuotesText());
        if (morningQuotes.isEmpty() && eveningQuotes.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "晨读金句和晚读金句至少需要填写一项");
        }
        saveQuoteGroup("daily_quotes_morning", "daily_quotes_morning_start_index", "daily_quotes_morning_start_date",
                "晨读金句，多行存储", "晨读金句顺序轮换起始序号，从 1 开始", "晨读金句顺序轮换起始日期",
                morningQuotes, request.getMorningStartIndex());
        saveQuoteGroup("daily_quotes_evening", "daily_quotes_evening_start_index", "daily_quotes_evening_start_date",
                "晚读金句，多行存储", "晚读金句顺序轮换起始序号，从 1 开始", "晚读金句顺序轮换起始日期",
                eveningQuotes, request.getEveningStartIndex());
    }

    @Override
    public EveningCheckinSettingVO getEveningCheckinSetting() {
        requireAdmin();
        List<String> grades = classInfoMapper.selectClassAccessList().stream()
                .map(ClassAccessVO::getGradeName)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        return EveningCheckinSettingUtil.deserialize(getSettingValue("evening_checkin_settings"), grades);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveEveningCheckinSetting(EveningCheckinSettingSaveRequest request) {
        requireAdmin();
        EveningCheckinSettingUtil.validate(request);
        EveningCheckinSettingVO settingVO = new EveningCheckinSettingVO();
        settingVO.setDefaultReturnWeekday("SUNDAY");
        settingVO.setOverrideReturnDate(StringUtils.trimToNull(request.getOverrideReturnDate()));
        List<EveningCheckinRuleRowVO> rows = request.getRules().stream().map(this::toEveningRuleVO).collect(Collectors.toList());
        settingVO.setRules(rows);
        saveOrUpdateSetting("evening_checkin_settings",
                EveningCheckinSettingUtil.serialize(settingVO),
                "返校日与平时晚间打卡规则设置");
    }

    @Override
    public RankSettingVO getRankSetting() {
        requireAdmin();
        return RankRuleUtil.deserialize(getSettingValue("rank_rule_settings"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRankSetting(RankSettingSaveRequest request) {
        requireAdmin();
        RankRuleUtil.validate(request);
        RankSettingVO defaults = RankRuleUtil.defaultSettings();
        RankSettingVO vo = new RankSettingVO();
        vo.setMode(RankRuleUtil.normalizeMode(request.getMode()));
        vo.setBronzeCount(valueOrDefault(request.getBronzeCount(), defaults.getBronzeCount()));
        vo.setBronze2Count(valueOrDefault(request.getBronze2Count(), defaults.getBronze2Count()));
        vo.setBronze1Count(valueOrDefault(request.getBronze1Count(), defaults.getBronze1Count()));
        vo.setSilverCount(valueOrDefault(request.getSilverCount(), defaults.getSilverCount()));
        vo.setSilver2Count(valueOrDefault(request.getSilver2Count(), defaults.getSilver2Count()));
        vo.setSilver1Count(valueOrDefault(request.getSilver1Count(), defaults.getSilver1Count()));
        vo.setGoldCount(valueOrDefault(request.getGoldCount(), defaults.getGoldCount()));
        vo.setGold3Count(valueOrDefault(request.getGold3Count(), defaults.getGold3Count()));
        vo.setGold2Count(valueOrDefault(request.getGold2Count(), defaults.getGold2Count()));
        vo.setGold1Count(valueOrDefault(request.getGold1Count(), defaults.getGold1Count()));
        vo.setPlatinumCount(valueOrDefault(request.getPlatinumCount(), defaults.getPlatinumCount()));
        vo.setPlatinum3Count(valueOrDefault(request.getPlatinum3Count(), defaults.getPlatinum3Count()));
        vo.setPlatinum2Count(valueOrDefault(request.getPlatinum2Count(), defaults.getPlatinum2Count()));
        vo.setPlatinum1Count(valueOrDefault(request.getPlatinum1Count(), defaults.getPlatinum1Count()));
        vo.setDiamondCount(valueOrDefault(request.getDiamondCount(), defaults.getDiamondCount()));
        vo.setDiamond4Count(valueOrDefault(request.getDiamond4Count(), defaults.getDiamond4Count()));
        vo.setDiamond3Count(valueOrDefault(request.getDiamond3Count(), defaults.getDiamond3Count()));
        vo.setDiamond2Count(valueOrDefault(request.getDiamond2Count(), defaults.getDiamond2Count()));
        vo.setDiamond1Count(valueOrDefault(request.getDiamond1Count(), defaults.getDiamond1Count()));
        vo.setStarryCount(valueOrDefault(request.getStarryCount(), defaults.getStarryCount()));
        vo.setStarry4Count(valueOrDefault(request.getStarry4Count(), defaults.getStarry4Count()));
        vo.setStarry3Count(valueOrDefault(request.getStarry3Count(), defaults.getStarry3Count()));
        vo.setStarry2Count(valueOrDefault(request.getStarry2Count(), defaults.getStarry2Count()));
        vo.setStarry1Count(valueOrDefault(request.getStarry1Count(), defaults.getStarry1Count()));
        vo.setKingCount(valueOrDefault(request.getKingCount(), defaults.getKingCount()));
        vo.setLegendCount(valueOrDefault(request.getLegendCount(), defaults.getLegendCount()));
        vo.setGloryCount(valueOrDefault(request.getGloryCount(), defaults.getGloryCount()));
        vo.setGloryPercent(valueOrDefault(request.getGloryPercent(), defaults.getGloryPercent()));
        vo.setLegendPercent(valueOrDefault(request.getLegendPercent(), defaults.getLegendPercent()));
        vo.setKingPercent(valueOrDefault(request.getKingPercent(), defaults.getKingPercent()));
        vo.setStarryPercent(valueOrDefault(request.getStarryPercent(), defaults.getStarryPercent()));
        vo.setDiamondPercent(valueOrDefault(request.getDiamondPercent(), defaults.getDiamondPercent()));
        vo.setPlatinumPercent(valueOrDefault(request.getPlatinumPercent(), defaults.getPlatinumPercent()));
        vo.setGoldPercent(valueOrDefault(request.getGoldPercent(), defaults.getGoldPercent()));
        vo.setSilverPercent(valueOrDefault(request.getSilverPercent(), defaults.getSilverPercent()));
        vo.setBronzePercent(valueOrDefault(request.getBronzePercent(), defaults.getBronzePercent()));
        saveOrUpdateSetting("rank_rule_settings", RankRuleUtil.serialize(vo), "战力段位规则设置");
    }

    private Integer valueOrDefault(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }

    @Override
    public CheckinDebugVO getCheckinDebugSetting() {
        requireAdmin();
        SysSetting setting = sysSettingMapper.selectOne(new LambdaQueryWrapper<SysSetting>()
                .eq(SysSetting::getSettingKey, "checkin_debug_enabled")
                .last("limit 1"));
        CheckinDebugVO vo = new CheckinDebugVO();
        vo.setEnabled(setting != null && "1".equals(StringUtils.trimToEmpty(setting.getSettingValue())));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveCheckinDebugSetting(CheckinDebugRequest request) {
        requireAdmin();
        SysSetting setting = sysSettingMapper.selectOne(new LambdaQueryWrapper<SysSetting>()
                .eq(SysSetting::getSettingKey, "checkin_debug_enabled")
                .last("limit 1"));
        String value = Boolean.TRUE.equals(request.getEnabled()) ? "1" : "0";
        if (setting == null) {
            setting = new SysSetting();
            setting.setSettingKey("checkin_debug_enabled");
            setting.setSettingValue(value);
            setting.setRemark("打卡调试开关：1开启 0关闭");
            sysSettingMapper.insert(setting);
            return;
        }
        setting.setSettingValue(value);
        setting.setRemark("打卡调试开关：1开启 0关闭");
        sysSettingMapper.updateById(setting);
    }

    @Override
    public List<ClassAccessVO> listClassAccess() {
        requireAdmin();
        List<ClassAccessVO> rows = classInfoMapper.selectClassAccessList();
        rows.sort(ClassSortUtil.comparingGradeAndClass(ClassAccessVO::getGradeName, ClassAccessVO::getClassName));
        return rows;
    }

    @Override
    public List<UserAccountVO> listUserAccounts() {
        requireAdmin();
        List<UserAccountVO> rows = sysUserMapper.selectManageableUsers();
        rows.sort(Comparator
                .comparingInt(this::roleDisplayOrder)
                .thenComparing(ClassSortUtil.comparingGradeAndClass(UserAccountVO::getGradeName, UserAccountVO::getClassName))
                .thenComparing(UserAccountVO::getUsername, Comparator.nullsLast(String::compareTo)));
        return rows;
    }

    @Override
    public List<StudentOrderBindingVO> listStudentOrderBindings(Long classId) {
        requireAdmin();
        if (classId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择班级");
        }
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null || classInfo.getEnabled() == null || classInfo.getEnabled() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "班级不存在或已停用");
        }
        List<StudentOrderBindingVO> rows = studentInfoMapper.selectStudentOrderBindings(classId);
        rows.forEach(item -> {
            Integer defaultOrderNo = resolveDefaultDisplayOrderNo(item.getStudentNo());
            item.setDefaultOrderNo(defaultOrderNo);
            item.setEffectiveOrderNo(item.getDisplayOrderNo() != null ? item.getDisplayOrderNo() : defaultOrderNo);
        });
        rows.sort(Comparator
                .comparing(StudentOrderBindingVO::getEffectiveOrderNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(StudentOrderBindingVO::getStudentNo, Comparator.nullsLast(String::compareTo))
                .thenComparing(StudentOrderBindingVO::getStudentId, Comparator.nullsLast(Long::compareTo)));
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStudentOrderBinding(UpdateStudentOrderRequest request) {
        requireAdmin();
        if (request == null || request.getStudentId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "学生不能为空");
        }
        if (request.getDisplayOrderNo() != null && request.getDisplayOrderNo() < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "打卡序号不能小于 0");
        }
        StudentInfo student = studentInfoMapper.selectById(request.getStudentId());
        if (student == null || student.getEnabled() == null || student.getEnabled() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学生不存在或已停用");
        }
        Integer targetOrderNo = request.getDisplayOrderNo() != null ? request.getDisplayOrderNo() : resolveDefaultDisplayOrderNo(student.getStudentNo());
        if (targetOrderNo != null) {
            List<StudentOrderBindingVO> rows = studentInfoMapper.selectStudentOrderBindings(student.getClassId());
            for (StudentOrderBindingVO item : rows) {
                if (item.getStudentId() != null && item.getStudentId().equals(student.getId())) {
                    continue;
                }
                Integer currentOrderNo = item.getDisplayOrderNo() != null ? item.getDisplayOrderNo() : resolveDefaultDisplayOrderNo(item.getStudentNo());
                if (currentOrderNo != null && currentOrderNo.equals(targetOrderNo)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "序号 " + targetOrderNo + " 已被学生“" + StringUtils.defaultIfBlank(item.getStudentName(), "未知") + "”占用");
                }
            }
        }
        student.setDisplayOrderNo(request.getDisplayOrderNo());
        studentInfoMapper.updateById(student);
    }

    private int roleDisplayOrder(UserAccountVO row) {
        if (row == null || row.getRoleCode() == null) {
            return Integer.MAX_VALUE;
        }
        switch (row.getRoleCode()) {
            case "STAFF":
                return 1;
            case "GRADE_LEADER":
                return 2;
            case "TEACHER":
                return 3;
            default:
                return 9;
        }
    }

    @Override
    public List<ClassLinkExportRow> buildClassLinkExportRows(String baseUrl) {
        requireAdmin();
        String normalizedBaseUrl = normalizeAccessBaseUrl(baseUrl);
        List<ClassLinkExportRow> rows = classInfoMapper.selectClassAccessList().stream().map(item -> {
            ClassLinkExportRow row = new ClassLinkExportRow();
            row.setGradeName(item.getGradeName());
            row.setClassName(item.getClassName());
            row.setTeacherName(item.getTeacherName());
            row.setTeacherUsername(item.getTeacherUsername());
            row.setStudentCount(item.getStudentCount());
            row.setUniqueCode(item.getUniqueCode());
            row.setCheckinUrl(normalizedBaseUrl + "/checkin.html?code=" + item.getUniqueCode());
            return row;
        }).collect(java.util.stream.Collectors.toList());
        rows.sort(ClassSortUtil.comparingGradeAndClass(ClassLinkExportRow::getGradeName, ClassLinkExportRow::getClassName));
        return rows;
    }

    @Override
    public AccessBaseUrlVO getAccessBaseUrl() {
        AccessBaseUrlVO vo = new AccessBaseUrlVO();
        vo.setAccessBaseUrl(normalizeAccessBaseUrl(null));
        return vo;
    }

    @Override
    public List<ImportStudentTemplateRow> getStudentTemplateRows() {
        requireAdmin();
        ImportStudentTemplateRow row1 = new ImportStudentTemplateRow();
        row1.setGradeName("七年级");
        row1.setClassNo("1");
        row1.setStudentNo("20260001");
        row1.setStudentName("张小明");

        ImportStudentTemplateRow row2 = new ImportStudentTemplateRow();
        row2.setGradeName("七年级");
        row2.setClassNo("1");
        row2.setStudentNo("20260002");
        row2.setStudentName("李小红");
        return Arrays.asList(row1, row2);
    }

    @Override
    public List<ImportUserTemplateRow> getUserTemplateRows() {
        requireAdmin();
        ImportUserTemplateRow row1 = new ImportUserTemplateRow();
        row1.setRealName("张老师");
        row1.setUsername("grade7_class1");
        row1.setRoleCode("班主任");
        row1.setGradeName("七年级");
        row1.setClassNo("1");
        row1.setPassword(DEFAULT_PASSWORD);

        ImportUserTemplateRow row2 = new ImportUserTemplateRow();
        row2.setRealName("李级长");
        row2.setUsername("grade7_leader");
        row2.setRoleCode("级长");
        row2.setGradeName("七年级");
        row2.setClassNo("");
        row2.setPassword(DEFAULT_PASSWORD);

        ImportUserTemplateRow row3 = new ImportUserTemplateRow();
        row3.setRealName("王行政");
        row3.setUsername("office_01");
        row3.setRoleCode("行政");
        row3.setGradeName("");
        row3.setClassNo("");
        row3.setPassword(DEFAULT_PASSWORD);
        return Arrays.asList(row1, row2, row3);
    }

    private ClassInfo loadOrCreateClass(ImportStudentRosterRow row, Map<String, ClassInfo> classCache, boolean persist) {
        String normalizedClassName = normalizeClassName(row.getClassNo());
        String classKey = row.getGradeName() + "_" + normalizedClassName;
        if (classCache.containsKey(classKey)) {
            return classCache.get(classKey);
        }
        ClassInfo classInfo = classInfoMapper.selectOne(new LambdaQueryWrapper<ClassInfo>()
                .eq(ClassInfo::getGradeName, row.getGradeName())
                .eq(ClassInfo::getClassName, normalizedClassName)
                .last("limit 1"));
        if (classInfo == null) {
            classInfo = new ClassInfo();
            classInfo.setGradeName(row.getGradeName());
            classInfo.setClassName(normalizedClassName);
            classInfo.setEnabled(1);
            classInfo.setStudentCount(0);
            classInfo.setUniqueCode(generateAvailableUniqueCode());
            if (persist) {
                classInfoMapper.insert(classInfo);
            }
        }
        classCache.put(classKey, classInfo);
        return classInfo;
    }

    private int loadOrCreateStudent(ImportStudentRosterRow row, ClassInfo classInfo, boolean persist) {
        LambdaQueryWrapper<StudentInfo> wrapper = new LambdaQueryWrapper<StudentInfo>()
                .eq(StudentInfo::getClassId, classInfo.getId())
                .last("limit 1");
        if (StringUtils.isNotBlank(row.getStudentNo())) {
            wrapper.eq(StudentInfo::getStudentNo, row.getStudentNo());
        } else {
            wrapper.eq(StudentInfo::getStudentName, row.getStudentName());
        }
        StudentInfo student = studentInfoMapper.selectOne(wrapper);
        if (student == null) {
            if (persist) {
                student = new StudentInfo();
                student.setClassId(classInfo.getId());
                student.setStudentNo(row.getStudentNo());
                student.setStudentName(row.getStudentName());
                student.setEnabled(1);
                studentInfoMapper.insert(student);
            }
            return 1;
        }
        if (persist) {
            student.setStudentNo(row.getStudentNo());
            student.setStudentName(row.getStudentName());
            student.setEnabled(1);
            studentInfoMapper.updateById(student);
        }
        return 2;
    }

    private int loadOrCreateUser(ImportUserRosterRow row, boolean persist) {
        RoleCode roleCode = parseImportRole(row.getRoleCode());
        ClassInfo classInfo = null;
        if (RoleCode.TEACHER.equals(roleCode)) {
            if (StringUtils.isAnyBlank(row.getGradeName(), row.getClassNo())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "班主任账号必须填写年级和班级");
            }
            classInfo = ensureClassExists(row.getGradeName(), normalizeClassName(row.getClassNo()), persist);
        } else if (RoleCode.GRADE_LEADER.equals(roleCode) && StringUtils.isBlank(row.getGradeName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "级长账号必须绑定年级");
        }

        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, row.getUsername())
                .last("limit 1"));
        String password = PasswordUtil.md5(StringUtils.defaultIfBlank(row.getPassword(), DEFAULT_PASSWORD));
        if (user == null) {
            if (persist) {
                user = new SysUser();
                user.setUsername(row.getUsername());
                user.setPassword(password);
                user.setEnabled(1);
                user.setMustChangePassword(1);
                user.setRealName(row.getRealName());
                applyRoleBinding(user, row, roleCode, classInfo, true);
                sysUserMapper.insert(user);
            }
            return 1;
        }
        if (RoleCode.ADMIN.name().equals(user.getRoleCode())) {
            throw new BusinessException(ErrorCode.CONFLICT, "账号 " + row.getUsername() + " 已被管理员账号占用");
        }
        if (persist) {
            user.setRealName(row.getRealName());
            user.setPassword(password);
            user.setEnabled(1);
            user.setMustChangePassword(1);
            applyRoleBinding(user, row, roleCode, classInfo, false);
            sysUserMapper.updateById(user);
        }
        return 2;
    }

    private void applyRoleBinding(SysUser user, ImportUserRosterRow row, RoleCode roleCode, ClassInfo classInfo, boolean createMode) {
        user.setRoleCode(roleCode.name());
        user.setGradeName(null);
        user.setClassId(null);
        if (RoleCode.TEACHER.equals(roleCode)) {
            user.setClassId(classInfo.getId());
            user.setGradeName(classInfo.getGradeName());
            classInfo.setTeacherName(row.getRealName());
            if (classInfo.getId() != null) {
                classInfoMapper.updateById(classInfo);
            }
            return;
        }
        if (RoleCode.GRADE_LEADER.equals(roleCode)) {
            user.setGradeName(row.getGradeName());
        }
    }

    private ClassInfo ensureClassExists(String gradeName, String className, boolean persist) {
        ClassInfo classInfo = classInfoMapper.selectOne(new LambdaQueryWrapper<ClassInfo>()
                .eq(ClassInfo::getGradeName, gradeName)
                .eq(ClassInfo::getClassName, className)
                .last("limit 1"));
        if (classInfo != null) {
            return classInfo;
        }
        classInfo = new ClassInfo();
        classInfo.setGradeName(gradeName);
        classInfo.setClassName(className);
        classInfo.setEnabled(1);
        classInfo.setStudentCount(0);
        classInfo.setUniqueCode(generateAvailableUniqueCode());
        if (persist) {
            classInfoMapper.insert(classInfo);
        }
        return classInfo;
    }

    private RoleCode parseImportRole(String roleText) {
        String normalized = StringUtils.trimToEmpty(roleText).replace(" ", "");
        String upper = normalized.toUpperCase();
        if ("ADMIN".equals(upper) || "管理员".equals(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "模板不支持导入 ADMIN 账号");
        }
        if ("班主任".equals(normalized) || "TEACHER".equals(upper)) {
            return RoleCode.TEACHER;
        }
        if ("级长".equals(normalized) || "年级主任".equals(normalized) || "GRADE_LEADER".equals(upper)) {
            return RoleCode.GRADE_LEADER;
        }
        if ("行政".equals(normalized) || "STAFF".equals(upper)) {
            return RoleCode.STAFF;
        }
        try {
            return RoleCode.valueOf(upper);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "角色仅支持 班主任、级长、行政 或 TEACHER、GRADE_LEADER、STAFF");
        }
    }

    private String normalizeClassName(String classNoText) {
        String normalized = StringUtils.trimToEmpty(classNoText);
        if (StringUtils.isBlank(normalized)) {
            return normalized;
        }
        if (normalized.endsWith("班")) {
            return normalized;
        }
        return normalized + "班";
    }

    private Integer resolveDefaultDisplayOrderNo(String studentNo) {
        String digits = StringUtils.defaultString(studentNo).replaceAll("\\D", "");
        if (StringUtils.isBlank(digits)) {
            return null;
        }
        if (digits.length() == 1) {
            return Integer.parseInt(digits);
        }
        return Integer.parseInt(digits.substring(digits.length() - 2));
    }

    private String generateAvailableUniqueCode() {
        for (int i = 0; i < 10; i++) {
            String code = UniqueCodeGenerator.generate();
            Long count = classInfoMapper.selectCount(new LambdaQueryWrapper<ClassInfo>()
                    .eq(ClassInfo::getUniqueCode, code));
            if (count == null || count == 0) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "生成班级特征码失败，请重试");
    }

    private void requireAdmin() {
        LoginUser loginUser = UserContext.get();
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或登录已失效");
        }
        if (!RoleCode.ADMIN.equals(loginUser.getRoleCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可执行该操作");
        }
    }

    private int calculateCurrentWeek(LocalDate startDate) {
        LocalDate today = LocalDate.now();
        if (today.isBefore(startDate)) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(startDate, today);
        return (int) (days / 7) + 1;
    }

    private ImportResultVO initImportResult(int totalRows) {
        ImportResultVO result = new ImportResultVO();
        result.setTotalRows(totalRows);
        result.setCreateCount(0);
        result.setUpdateCount(0);
        result.setSuccessCount(0);
        result.setSkippedCount(0);
        result.setErrorCount(0);
        result.setPreview(false);
        return result;
    }

    private void appendImportError(ImportResultVO result, int rowNumber, String reason) {
        ImportErrorVO error = new ImportErrorVO();
        error.setRowNumber(rowNumber);
        error.setReason(reason);
        result.getErrors().add(error);
    }

    private void refreshTeacherNameByClassId(Long classId) {
        if (classId == null) {
            return;
        }
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null) {
            return;
        }
        classInfo.setTeacherName(sysUserMapper.selectTeacherNameByClassId(classId));
        classInfoMapper.updateById(classInfo);
    }

    private List<String> parseQuotes(String content) {
        return Arrays.stream(StringUtils.defaultString(content).split("\\r?\\n"))
                .map(StringUtils::trimToEmpty)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
    }

    private String resolveTodayQuote(List<String> quotes) {
        int todayIndex = calculateTodayQuoteIndex(getQuoteStartDate(),
                normalizeQuoteStartIndex(readQuoteStartIndex(), quotes == null ? 0 : quotes.size()),
                quotes == null ? 0 : quotes.size());
        return resolveQuoteByIndex(quotes, todayIndex);
    }

    private String normalizeAccessBaseUrl(String baseUrl) {
        String normalizedBaseUrl = StringUtils.removeEnd(StringUtils.defaultIfBlank(baseUrl, ""), "/");
        if (StringUtils.isBlank(normalizedBaseUrl) || normalizedBaseUrl.contains("localhost") || normalizedBaseUrl.contains("127.0.0.1")) {
            return "http://" + NetworkUtil.resolveLocalIpv4() + ":" + StringUtils.defaultIfBlank(serverPort, "8080");
        }
        return normalizedBaseUrl;
    }

    private EveningCheckinRuleRowVO toEveningRuleVO(EveningCheckinRuleRowRequest request) {
        EveningCheckinRuleRowVO row = new EveningCheckinRuleRowVO();
        row.setGradeName(request.getGradeName());
        row.setMorningStart(request.getMorningStart());
        row.setMorningEnd(request.getMorningEnd());
        row.setNormalStart(request.getNormalStart());
        row.setNormalEnd(request.getNormalEnd());
        row.setReturnStart(request.getReturnStart());
        row.setReturnEnd(request.getReturnEnd());
        return row;
    }

    private void fillQuoteSettingVO(DailyQuoteSettingVO vo, String period, String quotesKey, String startIndexKey, String startDateKey) {
        List<String> quotes = parseQuotes(getSettingValue(quotesKey));
        LocalDate startDate = getQuoteStartDate(startDateKey);
        int startIndex = normalizeQuoteStartIndex(readQuoteStartIndex(startIndexKey), quotes.size());
        int todayIndex = calculateTodayQuoteIndex(startDate, startIndex, quotes.size());
        String quotesText = quotes.isEmpty() ? "" : String.join(System.lineSeparator(), quotes);
        String todayQuote = resolveQuoteByIndex(quotes, todayIndex);
        if ("morning".equals(period)) {
            vo.setMorningQuotesText(quotesText);
            vo.setMorningQuoteCount(quotes.size());
            vo.setMorningStartIndex(startIndex);
            vo.setMorningTodayIndex(todayIndex);
            vo.setMorningStartDate(startDate.toString());
            vo.setMorningTodayQuote(todayQuote);
            return;
        }
        vo.setEveningQuotesText(quotesText);
        vo.setEveningQuoteCount(quotes.size());
        vo.setEveningStartIndex(startIndex);
        vo.setEveningTodayIndex(todayIndex);
        vo.setEveningStartDate(startDate.toString());
        vo.setEveningTodayQuote(todayQuote);
    }

    private void saveQuoteGroup(String quotesKey, String startIndexKey, String startDateKey,
                                String quotesRemark, String startIndexRemark, String startDateRemark,
                                List<String> quotes, Integer startIndex) {
        if (quotes == null || quotes.isEmpty()) {
            saveOrUpdateSetting(quotesKey, "", quotesRemark);
            saveOrUpdateSetting(startIndexKey, "1", startIndexRemark);
            saveOrUpdateSetting(startDateKey, LocalDate.now().toString(), startDateRemark);
            return;
        }
        int normalizedStartIndex = normalizeQuoteStartIndex(startIndex, quotes.size());
        saveOrUpdateSetting(quotesKey, String.join(System.lineSeparator(), quotes), quotesRemark);
        saveOrUpdateSetting(startIndexKey, String.valueOf(normalizedStartIndex), startIndexRemark);
        saveOrUpdateSetting(startDateKey, LocalDate.now().toString(), startDateRemark);
    }

    private String resolveQuoteByIndex(List<String> quotes, int index) {
        if (quotes == null || quotes.isEmpty()) {
            return "我相信——我的坚持，可以成就更好的自己！";
        }
        int normalized = normalizeQuoteStartIndex(index, quotes.size());
        return quotes.get(normalized - 1);
    }

    private int calculateTodayQuoteIndex(LocalDate startDate, int startIndex, int quoteCount) {
        if (quoteCount <= 0) {
            return 1;
        }
        long offsetDays = ChronoUnit.DAYS.between(startDate, LocalDate.now());
        return (int) Math.floorMod((startIndex - 1) + offsetDays, quoteCount) + 1;
    }

    private int readQuoteStartIndex() {
        return readQuoteStartIndex("daily_quotes_start_index");
    }

    private int readQuoteStartIndex(String settingKey) {
        String value = getSettingValue(settingKey);
        if (StringUtils.isBlank(value)) {
            return 1;
        }
        try {
            return Integer.parseInt(StringUtils.trim(value));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private LocalDate getQuoteStartDate() {
        return getQuoteStartDate("daily_quotes_start_date");
    }

    private LocalDate getQuoteStartDate(String settingKey) {
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

    private int normalizeQuoteStartIndex(Integer startIndex, int quoteCount) {
        if (quoteCount <= 0) {
            return 1;
        }
        if (startIndex == null) {
            return 1;
        }
        return Math.max(1, Math.min(startIndex, quoteCount));
    }

    private String getSettingValue(String key) {
        SysSetting setting = sysSettingMapper.selectOne(new LambdaQueryWrapper<SysSetting>()
                .eq(SysSetting::getSettingKey, key)
                .last("limit 1"));
        return setting == null ? "" : StringUtils.defaultString(setting.getSettingValue());
    }

    private void saveOrUpdateSetting(String key, String value, String remark) {
        SysSetting setting = sysSettingMapper.selectOne(new LambdaQueryWrapper<SysSetting>()
                .eq(SysSetting::getSettingKey, key)
                .last("limit 1"));
        if (setting == null) {
            setting = new SysSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            setting.setRemark(remark);
            sysSettingMapper.insert(setting);
            return;
        }
        setting.setSettingValue(value);
        setting.setRemark(remark);
        sysSettingMapper.updateById(setting);
    }

    private LoginVO buildLoginVO(SysUser user) {
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(jwtUtil.generateToken(user.getId(), user.getUsername(), RoleCode.valueOf(user.getRoleCode()),
                user.getGradeName(), user.getClassId(), user.getMustChangePassword() != null && user.getMustChangePassword() == 1));
        loginVO.setRoleCode(user.getRoleCode());
        loginVO.setGradeName(user.getGradeName());
        loginVO.setClassId(user.getClassId());
        loginVO.setRealName(user.getRealName());
        loginVO.setMustChangePassword(user.getMustChangePassword() != null && user.getMustChangePassword() == 1);
        return loginVO;
    }
}




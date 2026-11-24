package com.school.checkin.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.school.checkin.common.ApiResponse;
import com.school.checkin.dto.CorrectionUpdateRequest;
import com.school.checkin.excel.ExcelStyleFactory;
import com.school.checkin.service.DataQueryService;
import com.school.checkin.utils.RankRuleUtil;
import com.school.checkin.vo.ClassAccessVO;
import com.school.checkin.vo.CorrectionRecordVO;
import com.school.checkin.vo.ExportCheckInRow;
import com.school.checkin.vo.ExportCheckInSummaryRow;
import com.school.checkin.vo.LeaderboardVO;
import com.school.checkin.vo.OverviewStatsVO;
import com.school.checkin.vo.StudentQualityLeaderboardVO;
import com.school.checkin.vo.RankSettingVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/data")
public class DataQueryController {

    private final DataQueryService dataQueryService;
    private final com.school.checkin.mapper.SysSettingMapper sysSettingMapper;

    public DataQueryController(DataQueryService dataQueryService,
                               com.school.checkin.mapper.SysSettingMapper sysSettingMapper) {
        this.dataQueryService = dataQueryService;
        this.sysSettingMapper = sysSettingMapper;
    }

    @GetMapping("/leaderboard")
    public ApiResponse<List<LeaderboardVO>> getLeaderboard(
            @RequestParam(value = "date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(value = "timeSlot", required = false) String timeSlot) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        return ApiResponse.success(dataQueryService.getLeaderboard(targetDate, timeSlot));
    }

    @GetMapping("/overview")
    public ApiResponse<OverviewStatsVO> getOverview() {
        return ApiResponse.success(dataQueryService.getOverviewStats());
    }

    @GetMapping("/leaderboard/student-quality")
    public ApiResponse<List<StudentQualityLeaderboardVO>> getStudentQualityLeaderboard(
            @RequestParam(value = "classId", required = false) Long classId) {
        return ApiResponse.success(dataQueryService.getStudentQualityLeaderboard(classId));
    }

    @GetMapping("/classes")
    public ApiResponse<List<ClassAccessVO>> getVisibleClasses() {
        return ApiResponse.success(dataQueryService.getVisibleClasses());
    }

    @GetMapping("/correction/records")
    public ApiResponse<List<CorrectionRecordVO>> getCorrectionRecords(
            @RequestParam("date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(value = "timeSlot", required = false) String timeSlot,
            @RequestParam(value = "classId", required = false) Long classId) {
        return ApiResponse.success(dataQueryService.getCorrectionRows(date, timeSlot, classId));
    }

    @PostMapping("/correction/check-in")
    public ApiResponse<Void> manualCheckIn(@Valid @RequestBody CorrectionUpdateRequest request) {
        dataQueryService.manualCheckIn(LocalDate.parse(request.getDate()), request.getTimeSlot(), request.getStudentId(), request.getCheckinTime());
        return ApiResponse.success("补卡成功", null);
    }

    @PostMapping("/correction/revoke")
    public ApiResponse<Void> manualRevoke(@Valid @RequestBody CorrectionUpdateRequest request) {
        dataQueryService.manualRevoke(LocalDate.parse(request.getDate()), request.getTimeSlot(), request.getStudentId());
        return ApiResponse.success("撤销成功", null);
    }

    @GetMapping("/export")
    public void exportReport(
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(value = "weekIndex", required = false) Integer weekIndex,
            @RequestParam(value = "month", required = false) String month,
            HttpServletResponse response) throws Exception {
        List<ExportCheckInRow> rows = dataQueryService.getExportRows(scope, date, weekIndex, month);
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        String scopeLabel;
        if ("weekly".equalsIgnoreCase(scope)) {
            scopeLabel = "第" + weekIndex + "周周报";
        } else if ("monthly".equalsIgnoreCase(scope)) {
            scopeLabel = month + "月报";
        } else if ("semester".equalsIgnoreCase(scope)) {
            scopeLabel = "学期总报表";
        } else {
            scopeLabel = "日报";
        }
        String fileName = URLEncoder.encode(buildExportFileName(scope, targetDate, weekIndex, month), StandardCharsets.UTF_8.name());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName);
        if ("daily".equalsIgnoreCase(scope)) {
            EasyExcel.write(response.getOutputStream(), ExportCheckInRow.class)
                    .relativeHeadRowIndex(ExcelStyleFactory.getCompactTitleRows())
                    .registerWriteHandler(ExcelStyleFactory.defaultStyle())
                    .registerWriteHandler(ExcelStyleFactory.autoWidth())
                    .registerWriteHandler(ExcelStyleFactory.buildCompactHeaderHandler(
                            "校园“点亮金牌”打卡数据报表",
                            "报表类型：" + scopeLabel + "    统计基准：" + buildExportBasis(scope, targetDate, weekIndex, month),
                            7))
                    .registerWriteHandler(ExcelStyleFactory.buildZebraHandler(ExcelStyleFactory.getCompactTitleRows()))
                    .sheet("当日明细")
                    .doWrite(rows);
            return;
        }

        ExcelWriter writer = EasyExcel.write(response.getOutputStream()).build();
        try {
            List<ExportCheckInSummaryRow> summaryRows = buildSummaryRows(rows);
            if (summaryRows.isEmpty()) {
                summaryRows = buildEmptySummaryRows();
            }
            WriteSheet summarySheet = EasyExcel.writerSheet(0, "累计统计")
                    .head(ExportCheckInSummaryRow.class)
                    .relativeHeadRowIndex(ExcelStyleFactory.getCompactTitleRows())
                    .registerWriteHandler(ExcelStyleFactory.defaultStyle())
                    .registerWriteHandler(ExcelStyleFactory.autoWidth())
                    .registerWriteHandler(ExcelStyleFactory.buildCompactHeaderHandler(
                            "校园“点亮金牌”累计打卡统计",
                            "报表类型：" + scopeLabel + "    统计基准：" + buildExportBasis(scope, targetDate, weekIndex, month),
                            7))
                    .registerWriteHandler(ExcelStyleFactory.buildZebraHandler(ExcelStyleFactory.getCompactTitleRows()))
                    .build();
            writer.write(summaryRows, summarySheet);

            Map<String, List<ExportCheckInRow>> groupedRows = groupRows(scope, rows);
            if (groupedRows.isEmpty()) {
                groupedRows = buildEmptyDetailGroup(scope);
            }
            int sheetIndex = 1;
            for (Map.Entry<String, List<ExportCheckInRow>> entry : groupedRows.entrySet()) {
                String detailTitle = "monthly".equalsIgnoreCase(scope) ? "校园“点亮金牌”每周打卡明细" : "校园“点亮金牌”每日打卡明细";
                String detailSubtitle = "monthly".equalsIgnoreCase(scope) ? "统计周次：" + entry.getKey() : "统计日期：" + entry.getKey();
                WriteSheet detailSheet = EasyExcel.writerSheet(sheetIndex++, entry.getKey())
                        .head(ExportCheckInRow.class)
                        .relativeHeadRowIndex(ExcelStyleFactory.getCompactTitleRows())
                        .registerWriteHandler(ExcelStyleFactory.defaultStyle())
                        .registerWriteHandler(ExcelStyleFactory.autoWidth())
                        .registerWriteHandler(ExcelStyleFactory.buildCompactHeaderHandler(
                                detailTitle,
                                detailSubtitle,
                                7))
                        .registerWriteHandler(ExcelStyleFactory.buildZebraHandler(ExcelStyleFactory.getCompactTitleRows()))
                        .build();
                writer.write(entry.getValue(), detailSheet);
            }
        } finally {
            writer.finish();
        }
    }

    private String buildExportBasis(String scope, LocalDate targetDate, Integer weekIndex, String month) {
        if ("weekly".equalsIgnoreCase(scope)) {
            return "第 " + weekIndex + " 周";
        }
        if ("monthly".equalsIgnoreCase(scope)) {
            return month;
        }
        if ("semester".equalsIgnoreCase(scope)) {
            return "本学期";
        }
        return String.valueOf(targetDate);
    }

    private String buildExportFileName(String scope, LocalDate targetDate, Integer weekIndex, String month) {
        if ("weekly".equalsIgnoreCase(scope)) {
            return "点亮金牌打卡周报_第" + weekIndex + "周.xlsx";
        }
        if ("monthly".equalsIgnoreCase(scope)) {
            return "点亮金牌打卡月报_" + month + ".xlsx";
        }
        if ("semester".equalsIgnoreCase(scope)) {
            return "点亮金牌打卡学期报表_" + LocalDate.now() + ".xlsx";
        }
        return "点亮金牌打卡日报_" + targetDate + ".xlsx";
    }

    private Map<String, List<ExportCheckInRow>> groupRows(String scope, List<ExportCheckInRow> rows) {
        if ("monthly".equalsIgnoreCase(scope)) {
            return groupRowsByWeek(rows);
        }
        return groupRowsByDate(rows);
    }

    private Map<String, List<ExportCheckInRow>> groupRowsByDate(List<ExportCheckInRow> rows) {
        Map<String, List<ExportCheckInRow>> grouped = new LinkedHashMap<>();
        for (ExportCheckInRow row : rows) {
            grouped.computeIfAbsent(row.getCheckinDate(), key -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    private List<ExportCheckInSummaryRow> buildEmptySummaryRows() {
        ExportCheckInSummaryRow row = new ExportCheckInSummaryRow();
        row.setGradeName("--");
        row.setClassName("--");
        row.setStudentName("暂无数据");
        row.setStudentNo("--");
        row.setMorningCount(0);
        row.setEveningCount(0);
        row.setTotalCount(0);
        row.setRankTitle("--");
        return java.util.Collections.singletonList(row);
    }

    private Map<String, List<ExportCheckInRow>> buildEmptyDetailGroup(String scope) {
        ExportCheckInRow row = new ExportCheckInRow();
        row.setCheckinDate("--");
        row.setGradeName("--");
        row.setClassName("--");
        row.setStudentName("暂无数据");
        row.setStudentNo("--");
        row.setTimeSlot("--");
        row.setCheckinStatus("未查询到数据");
        row.setCheckinTime("--");
        Map<String, List<ExportCheckInRow>> grouped = new LinkedHashMap<>();
        grouped.put("monthly".equalsIgnoreCase(scope) ? "本月无数据" : "本次无数据", java.util.Collections.singletonList(row));
        return grouped;
    }

    private Map<String, List<ExportCheckInRow>> groupRowsByWeek(List<ExportCheckInRow> rows) {
        Map<String, List<ExportCheckInRow>> grouped = new LinkedHashMap<>();
        for (ExportCheckInRow row : rows) {
            LocalDate currentDate = LocalDate.parse(row.getCheckinDate());
            LocalDate weekStart = currentDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate weekEnd = currentDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            String key = weekStart + "至" + weekEnd;
            grouped.computeIfAbsent(key, value -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    private List<ExportCheckInSummaryRow> buildSummaryRows(List<ExportCheckInRow> rows) {
        Map<String, ExportCheckInSummaryRow> grouped = new LinkedHashMap<>();
        for (ExportCheckInRow row : rows) {
            String key = String.join("|",
                    safe(row.getGradeName()),
                    safe(row.getClassName()),
                    safe(row.getStudentNo()),
                    safe(row.getStudentName()));
            ExportCheckInSummaryRow summary = grouped.computeIfAbsent(key, k -> {
                ExportCheckInSummaryRow item = new ExportCheckInSummaryRow();
                item.setGradeName(row.getGradeName());
                item.setClassName(row.getClassName());
                item.setStudentNo(row.getStudentNo());
                item.setStudentName(row.getStudentName());
                item.setMorningCount(0);
                item.setEveningCount(0);
                item.setTotalCount(0);
                return item;
            });
            if ("早上".equals(row.getTimeSlot())) {
                if ("已打卡".equals(row.getCheckinStatus())) {
                    summary.setMorningCount(summary.getMorningCount() + 1);
                }
            } else if ("晚上".equals(row.getTimeSlot())) {
                if ("已打卡".equals(row.getCheckinStatus())) {
                    summary.setEveningCount(summary.getEveningCount() + 1);
                }
            }
            if ("已打卡".equals(row.getCheckinStatus())) {
                summary.setTotalCount(summary.getTotalCount() + 1);
            }
        }
        List<ExportCheckInSummaryRow> summaries = grouped.values().stream()
                .sorted(Comparator
                        .comparing(ExportCheckInSummaryRow::getTotalCount, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ExportCheckInSummaryRow::getMorningCount, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ExportCheckInSummaryRow::getEveningCount, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ExportCheckInSummaryRow::getGradeName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ExportCheckInSummaryRow::getClassName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ExportCheckInSummaryRow::getStudentNo, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
        applySummaryRanks(summaries);
        return summaries;
    }

    private void applySummaryRanks(List<ExportCheckInSummaryRow> rows) {
        RankSettingVO rankSetting = RankRuleUtil.deserialize(loadRankSettingContent());
        String mode = RankRuleUtil.normalizeMode(rankSetting.getMode());
        for (int i = 0; i < rows.size(); i++) {
            ExportCheckInSummaryRow row = rows.get(i);
            RankRuleUtil.RankResult rankResult = RankRuleUtil.MODE_PERCENT.equals(mode)
                    ? RankRuleUtil.resolveByPercent(i, rows.size(), rankSetting)
                    : RankRuleUtil.resolveByCount(row.getTotalCount() == null ? 0 : row.getTotalCount(), rankSetting);
            row.setRankTitle(rankResult.getTitle());
            row.setRankCssCode(rankResult.getCssCode());
        }
    }

    private String loadRankSettingContent() {
        com.school.checkin.entity.SysSetting setting = sysSettingMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.school.checkin.entity.SysSetting>()
                        .eq(com.school.checkin.entity.SysSetting::getSettingKey, "rank_rule_settings")
                        .last("limit 1"));
        return setting == null ? "" : setting.getSettingValue();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}



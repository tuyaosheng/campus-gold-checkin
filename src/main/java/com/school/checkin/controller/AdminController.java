package com.school.checkin.controller;

import com.alibaba.excel.EasyExcel;
import com.school.checkin.common.ApiResponse;
import com.school.checkin.dto.ChangePasswordRequest;
import com.school.checkin.dto.CheckinDebugRequest;
import com.school.checkin.dto.DailyQuoteSaveRequest;
import com.school.checkin.dto.EveningCheckinSettingSaveRequest;
import com.school.checkin.dto.ImportErrorExportRequest;
import com.school.checkin.dto.LoginRequest;
import com.school.checkin.dto.RankSettingSaveRequest;
import com.school.checkin.dto.ResetPasswordRequest;
import com.school.checkin.dto.SemesterStartDateRequest;
import com.school.checkin.dto.UpdateStudentOrderRequest;
import com.school.checkin.dto.UpdateUserAccountRequest;
import com.school.checkin.excel.ExcelStyleFactory;
import com.school.checkin.service.AdminService;
import com.school.checkin.vo.ClassAccessVO;
import com.school.checkin.vo.AccessBaseUrlVO;
import com.school.checkin.vo.ClassLinkExportRow;
import com.school.checkin.vo.CheckinDebugVO;
import com.school.checkin.vo.DailyQuoteSettingVO;
import com.school.checkin.vo.EveningCheckinSettingVO;
import com.school.checkin.vo.ImportErrorExportRow;
import com.school.checkin.vo.ImportStudentTemplateRow;
import com.school.checkin.vo.ImportResultVO;
import com.school.checkin.vo.ImportUserTemplateRow;
import com.school.checkin.vo.LoginVO;
import com.school.checkin.vo.RankSettingVO;
import com.school.checkin.vo.SemesterSettingVO;
import com.school.checkin.vo.StudentOrderBindingVO;
import com.school.checkin.vo.UserAccountVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(adminService.login(request));
    }

    @PostMapping("/password/change")
    public ApiResponse<LoginVO> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.success("密码修改成功", adminService.changeOwnPassword(request));
    }

    @PostMapping("/users/reset-password")
    public ApiResponse<Void> resetUserPassword(@Valid @RequestBody ResetPasswordRequest request) {
        adminService.resetUserPassword(request);
        return ApiResponse.success("密码已重置为 dlzx，且下次登录必须修改密码", null);
    }

    @PostMapping("/users/update")
    public ApiResponse<Void> updateUser(@Valid @RequestBody UpdateUserAccountRequest request) {
        adminService.updateUserAccount(request);
        return ApiResponse.success("账号信息已更新", null);
    }

    @PostMapping("/roster/students/upload")
    public ApiResponse<ImportResultVO> uploadStudentRoster(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(adminService.importStudentRoster(file));
    }

    @PostMapping("/roster/students/preview")
    public ApiResponse<ImportResultVO> previewStudentRoster(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(adminService.previewStudentRoster(file));
    }

    @PostMapping("/roster/users/upload")
    public ApiResponse<ImportResultVO> uploadUserRoster(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(adminService.importUserRoster(file));
    }

    @PostMapping("/roster/users/preview")
    public ApiResponse<ImportResultVO> previewUserRoster(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(adminService.previewUserRoster(file));
    }

    @PostMapping("/roster/errors/export")
    public void exportImportErrors(@Valid @RequestBody ImportErrorExportRequest request,
                                   HttpServletResponse response) throws Exception {
        List<ImportErrorExportRow> rows = adminService.buildImportErrorRows(request);
        String fileName = URLEncoder.encode(request.getImportType() + "导入错误清单.xlsx", StandardCharsets.UTF_8.name());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName);
        EasyExcel.write(response.getOutputStream(), ImportErrorExportRow.class)
                .relativeHeadRowIndex(ExcelStyleFactory.getDefaultTitleRows())
                .registerWriteHandler(ExcelStyleFactory.defaultStyle())
                .registerWriteHandler(ExcelStyleFactory.autoWidth())
                .registerWriteHandler(ExcelStyleFactory.buildHeaderHandler(
                        request.getImportType() + "导入错误清单",
                        "请根据行号修正原始 Excel 后重新执行导入",
                        "本清单仅列出本次上传中识别出的失败项",
                        2))
                .registerWriteHandler(ExcelStyleFactory.buildZebraHandler(ExcelStyleFactory.getDefaultTitleRows()))
                .sheet("错误清单")
                .doWrite(rows);
    }

    @PostMapping("/semester-start")
    public ApiResponse<Void> saveSemesterStartDate(@Valid @RequestBody SemesterStartDateRequest request) {
        adminService.saveSemesterStartDate(LocalDate.parse(request.getDate()));
        return ApiResponse.success("设置成功", null);
    }

    @GetMapping("/semester-start")
    public ApiResponse<SemesterSettingVO> getSemesterStartDate() {
        return ApiResponse.success(adminService.getSemesterSetting());
    }

    @GetMapping("/daily-quotes")
    public ApiResponse<DailyQuoteSettingVO> getDailyQuotes() {
        return ApiResponse.success(adminService.getDailyQuoteSetting());
    }

    @PostMapping("/daily-quotes")
    public ApiResponse<Void> saveDailyQuotes(@Valid @RequestBody DailyQuoteSaveRequest request) {
        adminService.saveDailyQuotes(request);
        return ApiResponse.success("每日金句已保存", null);
    }

    @GetMapping("/evening-checkin-settings")
    public ApiResponse<EveningCheckinSettingVO> getEveningCheckinSettings() {
        return ApiResponse.success(adminService.getEveningCheckinSetting());
    }

    @PostMapping("/evening-checkin-settings")
    public ApiResponse<Void> saveEveningCheckinSettings(@Valid @RequestBody EveningCheckinSettingSaveRequest request) {
        adminService.saveEveningCheckinSetting(request);
        return ApiResponse.success("晚间打卡规则已保存", null);
    }

    @GetMapping("/rank-settings")
    public ApiResponse<RankSettingVO> getRankSettings() {
        return ApiResponse.success(adminService.getRankSetting());
    }

    @PostMapping("/rank-settings")
    public ApiResponse<Void> saveRankSettings(@Valid @RequestBody RankSettingSaveRequest request) {
        adminService.saveRankSetting(request);
        return ApiResponse.success("段位规则已保存", null);
    }

    @GetMapping("/checkin-debug")
    public ApiResponse<CheckinDebugVO> getCheckinDebug() {
        return ApiResponse.success(adminService.getCheckinDebugSetting());
    }

    @PostMapping("/checkin-debug")
    public ApiResponse<Void> saveCheckinDebug(@Valid @RequestBody CheckinDebugRequest request) {
        adminService.saveCheckinDebugSetting(request);
        return ApiResponse.success("调试开关已保存", null);
    }

    @GetMapping("/classes")
    public ApiResponse<List<ClassAccessVO>> listClasses() {
        return ApiResponse.success(adminService.listClassAccess());
    }

    @GetMapping("/users")
    public ApiResponse<List<UserAccountVO>> listUsers() {
        return ApiResponse.success(adminService.listUserAccounts());
    }

    @GetMapping("/student-order-bindings")
    public ApiResponse<List<StudentOrderBindingVO>> listStudentOrderBindings(@org.springframework.web.bind.annotation.RequestParam("classId") Long classId) {
        return ApiResponse.success(adminService.listStudentOrderBindings(classId));
    }

    @PostMapping("/student-order-bindings")
    public ApiResponse<Void> updateStudentOrderBinding(@RequestBody UpdateStudentOrderRequest request) {
        adminService.updateStudentOrderBinding(request);
        return ApiResponse.success("学生打卡序号已保存", null);
    }

    @GetMapping("/access-base-url")
    public ApiResponse<AccessBaseUrlVO> getAccessBaseUrl() {
        return ApiResponse.success(adminService.getAccessBaseUrl());
    }

    @GetMapping("/classes/export")
    public void exportClasses(@org.springframework.web.bind.annotation.RequestParam("baseUrl") String baseUrl,
                              HttpServletResponse response) throws Exception {
        List<ClassLinkExportRow> rows = adminService.buildClassLinkExportRows(baseUrl);
        String fileName = URLEncoder.encode("班级免登录打卡链接表.xlsx", StandardCharsets.UTF_8.name());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName);
        EasyExcel.write(response.getOutputStream(), ClassLinkExportRow.class)
                .relativeHeadRowIndex(ExcelStyleFactory.getDefaultTitleRows())
                .registerWriteHandler(ExcelStyleFactory.defaultStyle())
                .registerWriteHandler(ExcelStyleFactory.autoWidth())
                .registerWriteHandler(ExcelStyleFactory.buildHeaderHandler(
                        "校园“点亮金牌”班级免登录打卡链接表",
                        "可将本表直接发给班主任或在一体机浏览器中收藏使用",
                        "链接已按当前系统访问地址生成，请确认部署域名或 IP 无误",
                        6))
                .registerWriteHandler(ExcelStyleFactory.buildZebraHandler(ExcelStyleFactory.getDefaultTitleRows()))
                .sheet("班级链接")
                .doWrite(rows);
    }

    @GetMapping("/classes/zip")
    public void downloadClassLinkZip(@org.springframework.web.bind.annotation.RequestParam("baseUrl") String baseUrl,
                                     HttpServletResponse response) throws Exception {
        List<ClassLinkExportRow> rows = adminService.buildClassLinkExportRows(baseUrl);
        String fileName = URLEncoder.encode("班级免登录打卡链接快捷方式.zip", StandardCharsets.UTF_8.name());
        response.setContentType("application/zip");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName);
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(response.getOutputStream(), StandardCharsets.UTF_8)) {
            for (ClassLinkExportRow row : rows) {
                String entryName = safeShortcutName((row.getGradeName() == null ? "" : row.getGradeName()) + (row.getClassName() == null ? "" : row.getClassName())) + ".url";
                zipOutputStream.putNextEntry(new ZipEntry(entryName));
                String content = "[InternetShortcut]\r\nURL=" + row.getCheckinUrl() + "\r\n";
                zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }
            zipOutputStream.finish();
        }
    }

    private String safeShortcutName(String rawName) {
        String normalized = rawName == null ? "班级打卡链接" : rawName.trim();
        normalized = normalized.replaceAll("[\\\\/:*?\"<>|]", "_");
        return normalized.isEmpty() ? "班级打卡链接" : normalized;
    }

    @GetMapping("/roster/students/template")
    public void downloadStudentRosterTemplate(HttpServletResponse response) throws Exception {
        List<ImportStudentTemplateRow> rows = adminService.getStudentTemplateRows();
        String fileName = URLEncoder.encode("学生花名册导入模板.xlsx", StandardCharsets.UTF_8.name());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName);
        EasyExcel.write(response.getOutputStream(), ImportStudentTemplateRow.class)
                .relativeHeadRowIndex(ExcelStyleFactory.getDefaultTitleRows())
                .registerWriteHandler(ExcelStyleFactory.defaultStyle())
                .registerWriteHandler(ExcelStyleFactory.fixedColumnWidth(18, 14, 20, 18))
                .registerWriteHandler(ExcelStyleFactory.buildHeaderHandler(
                        "校园“点亮金牌”学生花名册导入模板",
                        "学生信息与教职工账号分开维护，请仅填写学生数据",
                        "班号列只填数字即可，例如 1 代表 1班；导入后会自动更新班级人数与学生名单",
                        3))
                .registerWriteHandler(ExcelStyleFactory.buildZebraHandler(ExcelStyleFactory.getDefaultTitleRows()))
                .sheet("学生花名册")
                .doWrite(rows);
    }

    @GetMapping("/roster/users/template")
    public void downloadUserRosterTemplate(HttpServletResponse response) throws Exception {
        List<ImportUserTemplateRow> rows = adminService.getUserTemplateRows();
        String fileName = URLEncoder.encode("教职工账号导入模板.xlsx", StandardCharsets.UTF_8.name());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName);
        EasyExcel.write(response.getOutputStream(), ImportUserTemplateRow.class)
                .relativeHeadRowIndex(ExcelStyleFactory.getDefaultTitleRows())
                .registerWriteHandler(ExcelStyleFactory.defaultStyle())
                .registerWriteHandler(ExcelStyleFactory.fixedColumnWidth(16, 24, 16, 14, 14, 18))
                .registerWriteHandler(ExcelStyleFactory.buildHeaderHandler(
                        "校园“点亮金牌”教职工账号导入模板",
                        "支持角色中文或英文：班主任/级长/行政，或 TEACHER/GRADE_LEADER/STAFF",
                        "班号列只填数字即可，例如 1 代表 1班；班主任需填年级和班号，级长需填年级",
                        5))
                .registerWriteHandler(ExcelStyleFactory.buildZebraHandler(ExcelStyleFactory.getDefaultTitleRows()))
                .sheet("教职工账号")
                .doWrite(rows);
    }
}



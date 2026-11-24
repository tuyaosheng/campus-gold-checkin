package com.school.checkin.service;

import com.school.checkin.dto.ChangePasswordRequest;
import com.school.checkin.dto.ImportErrorExportRequest;
import com.school.checkin.dto.LoginRequest;
import com.school.checkin.dto.ResetPasswordRequest;
import com.school.checkin.dto.DailyQuoteSaveRequest;
import com.school.checkin.dto.EveningCheckinSettingSaveRequest;
import com.school.checkin.dto.RankSettingSaveRequest;
import com.school.checkin.dto.UpdateStudentOrderRequest;
import com.school.checkin.dto.UpdateUserAccountRequest;
import com.school.checkin.dto.CheckinDebugRequest;
import com.school.checkin.vo.ClassLinkExportRow;
import com.school.checkin.vo.AccessBaseUrlVO;
import com.school.checkin.vo.ClassAccessVO;
import com.school.checkin.vo.CheckinDebugVO;
import com.school.checkin.vo.DailyQuoteSettingVO;
import com.school.checkin.vo.EveningCheckinSettingVO;
import com.school.checkin.vo.ImportErrorExportRow;
import com.school.checkin.vo.ImportStudentTemplateRow;
import com.school.checkin.vo.ImportUserTemplateRow;
import com.school.checkin.vo.ImportResultVO;
import com.school.checkin.vo.LoginVO;
import com.school.checkin.vo.RankSettingVO;
import com.school.checkin.vo.SemesterSettingVO;
import com.school.checkin.vo.StudentOrderBindingVO;
import com.school.checkin.vo.UserAccountVO;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface AdminService {

    LoginVO login(LoginRequest request);

    LoginVO changeOwnPassword(ChangePasswordRequest request);

    void resetUserPassword(ResetPasswordRequest request);

    void updateUserAccount(UpdateUserAccountRequest request);

    ImportResultVO previewStudentRoster(MultipartFile file);

    ImportResultVO importStudentRoster(MultipartFile file);

    ImportResultVO previewUserRoster(MultipartFile file);

    ImportResultVO importUserRoster(MultipartFile file);

    void saveSemesterStartDate(LocalDate date);

    SemesterSettingVO getSemesterSetting();

    DailyQuoteSettingVO getDailyQuoteSetting();

    void saveDailyQuotes(DailyQuoteSaveRequest request);

    EveningCheckinSettingVO getEveningCheckinSetting();

    void saveEveningCheckinSetting(EveningCheckinSettingSaveRequest request);

    RankSettingVO getRankSetting();

    void saveRankSetting(RankSettingSaveRequest request);

    CheckinDebugVO getCheckinDebugSetting();

    void saveCheckinDebugSetting(CheckinDebugRequest request);

    List<ClassAccessVO> listClassAccess();

    List<UserAccountVO> listUserAccounts();

    List<StudentOrderBindingVO> listStudentOrderBindings(Long classId);

    void updateStudentOrderBinding(UpdateStudentOrderRequest request);

    List<ClassLinkExportRow> buildClassLinkExportRows(String baseUrl);

    AccessBaseUrlVO getAccessBaseUrl();

    List<ImportStudentTemplateRow> getStudentTemplateRows();

    List<ImportUserTemplateRow> getUserTemplateRows();

    List<ImportErrorExportRow> buildImportErrorRows(ImportErrorExportRequest request);
}



package com.school.checkin.service;

import com.school.checkin.vo.ClassAccessVO;
import com.school.checkin.vo.CorrectionRecordVO;
import com.school.checkin.vo.ExportCheckInRow;
import com.school.checkin.vo.LeaderboardVO;
import com.school.checkin.vo.OverviewStatsVO;
import com.school.checkin.vo.StudentQualityLeaderboardVO;

import java.time.LocalDate;
import java.util.List;

public interface DataQueryService {

    List<LeaderboardVO> getLeaderboard(LocalDate date, String timeSlot);

    List<StudentQualityLeaderboardVO> getStudentQualityLeaderboard(Long classId);

    List<ExportCheckInRow> getExportRows(String scope, LocalDate date, Integer weekIndex, String month);

    OverviewStatsVO getOverviewStats();

    List<ClassAccessVO> getVisibleClasses();

    List<CorrectionRecordVO> getCorrectionRows(LocalDate date, String timeSlot, Long classId);

    void manualCheckIn(LocalDate date, String timeSlot, Long studentId, String checkinTime);

    void manualRevoke(LocalDate date, String timeSlot, Long studentId);
}

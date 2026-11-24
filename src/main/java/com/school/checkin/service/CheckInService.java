package com.school.checkin.service;

import com.school.checkin.dto.CheckInSubmitRequest;
import com.school.checkin.dto.CheckInRevokeRequest;
import com.school.checkin.vo.CheckInResultVO;
import com.school.checkin.vo.ClassRosterVO;
import com.school.checkin.vo.LeaderboardVO;

import java.time.LocalDate;
import java.util.List;

public interface CheckInService {

    ClassRosterVO getRosterByUniqueCode(String uniqueCode);

    CheckInResultVO submitCheckIn(CheckInSubmitRequest request);

    void revokeCheckIn(CheckInRevokeRequest request);

    List<LeaderboardVO> getPublicLeaderboard(LocalDate date, String timeSlot);
}

package com.school.checkin.controller;

import com.school.checkin.common.ApiResponse;
import com.school.checkin.dto.CheckInRevokeRequest;
import com.school.checkin.dto.CheckInSubmitRequest;
import com.school.checkin.service.CheckInService;
import com.school.checkin.vo.CheckInResultVO;
import com.school.checkin.vo.ClassRosterVO;
import com.school.checkin.vo.LeaderboardVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/public")
public class PublicApiController {

    private final CheckInService checkInService;

    public PublicApiController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    @GetMapping("/roster")
    public ApiResponse<ClassRosterVO> getRoster(@RequestParam("uniqueCode") String uniqueCode) {
        return ApiResponse.success(checkInService.getRosterByUniqueCode(uniqueCode));
    }

    @GetMapping("/leaderboard")
    public ApiResponse<List<LeaderboardVO>> getLeaderboard(
            @RequestParam(value = "date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
            @RequestParam(value = "timeSlot", required = false) String timeSlot) {
        return ApiResponse.success(checkInService.getPublicLeaderboard(date, timeSlot));
    }

    @PostMapping("/check-in")
    public ApiResponse<CheckInResultVO> submitCheckIn(@Valid @RequestBody CheckInSubmitRequest request) {
        return ApiResponse.success("打卡成功", checkInService.submitCheckIn(request));
    }

    @PostMapping("/check-in/revoke")
    public ApiResponse<Void> revokeCheckIn(@Valid @RequestBody CheckInRevokeRequest request) {
        checkInService.revokeCheckIn(request);
        return ApiResponse.success("撤销成功", null);
    }
}

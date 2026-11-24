package com.school.checkin.vo;

import lombok.Data;

@Data
public class StudentWeeklyLeaderboardVO {
    private Long studentId;
    private String studentNo;
    private String studentName;
    private Integer totalCheckinCount;
    private String firstCheckinTime;
    private Double qualityScore;
    private String rankTitle;
    private String rankCssCode;
}

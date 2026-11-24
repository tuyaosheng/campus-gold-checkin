package com.school.checkin.vo;

import lombok.Data;

@Data
public class StudentQualityLeaderboardVO {
    private Long studentId;
    private String gradeName;
    private String className;
    private String studentNo;
    private String studentName;
    private Integer totalCheckinCount;
    private Double qualityScore;
    private String rankTitle;
}

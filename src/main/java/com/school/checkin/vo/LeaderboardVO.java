package com.school.checkin.vo;

import lombok.Data;

@Data
public class LeaderboardVO {
    private Long classId;
    private String gradeName;
    private String className;
    private Integer totalStudents;
    private Integer checkedStudents;
    private Double attendanceRate;
}

package com.school.checkin.vo;

import lombok.Data;

import java.util.List;

@Data
public class ClassRosterVO {
    private Long classId;
    private String className;
    private String gradeName;
    private String teacherName;
    private String uniqueCode;
    private String timeSlot;
    private String timeSlotLabel;
    private Boolean interactionAllowed;
    private String interactionMessage;
    private Integer currentWeek;
    private String dailyQuote;
    private String rankRuleTip;
    private String schoolTopStudentName;
    private String schoolTopStudentClassName;
    private Integer schoolTopStudentCount;
    private List<StudentWeeklyLeaderboardVO> totalLeaderboard;
    private List<StudentWeeklyLeaderboardVO> weekLeaderboard;
    private List<StudentWeeklyLeaderboardVO> monthLeaderboard;
    private List<StudentSimpleVO> students;
}

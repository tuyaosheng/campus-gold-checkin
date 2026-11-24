package com.school.checkin.vo;

import lombok.Data;

@Data
public class ExportStudentScopeVO {
    private Long studentId;
    private Long classId;
    private String gradeName;
    private String className;
    private String studentName;
    private String studentNo;
}

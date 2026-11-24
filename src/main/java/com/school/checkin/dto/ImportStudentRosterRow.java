package com.school.checkin.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class ImportStudentRosterRow {

    @ExcelProperty("年级")
    private String gradeName;

    @ExcelProperty("班号")
    private String classNo;

    @ExcelProperty("学号")
    private String studentNo;

    @ExcelProperty("学生姓名")
    private String studentName;
}

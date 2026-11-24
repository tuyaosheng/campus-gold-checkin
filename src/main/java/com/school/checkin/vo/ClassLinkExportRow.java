package com.school.checkin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

@Data
public class ClassLinkExportRow {

    @ColumnWidth(14)
    @ExcelProperty("年级")
    private String gradeName;

    @ColumnWidth(14)
    @ExcelProperty("班级")
    private String className;

    @ColumnWidth(14)
    @ExcelProperty("班主任")
    private String teacherName;

    @ColumnWidth(18)
    @ExcelProperty("教师账号")
    private String teacherUsername;

    @ColumnWidth(12)
    @ExcelProperty("学生人数")
    private Integer studentCount;

    @ColumnWidth(18)
    @ExcelProperty("特征码")
    private String uniqueCode;

    @ColumnWidth(48)
    @ExcelProperty("免登录打卡链接")
    private String checkinUrl;
}

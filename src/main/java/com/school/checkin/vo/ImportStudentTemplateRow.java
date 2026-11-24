package com.school.checkin.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

@Data
public class ImportStudentTemplateRow {

    @ColumnWidth(20)
    @ExcelProperty("年级")
    private String gradeName;

    @ColumnWidth(14)
    @ExcelProperty("班号")
    private String classNo;

    @ColumnWidth(24)
    @ExcelProperty("学号")
    private String studentNo;

    @ColumnWidth(20)
    @ExcelProperty("学生姓名")
    private String studentName;
}

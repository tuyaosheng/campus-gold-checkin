package com.school.checkin.vo;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentStyle;
import com.alibaba.excel.annotation.write.style.HeadFontStyle;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import com.alibaba.excel.annotation.write.style.HeadStyle;
import com.alibaba.excel.enums.BooleanEnum;
import com.alibaba.excel.enums.poi.FillPatternTypeEnum;
import com.alibaba.excel.enums.poi.HorizontalAlignmentEnum;
import lombok.Data;

@Data
@HeadRowHeight(24)
@HeadStyle(fillForegroundColor = 22, fillPatternType = FillPatternTypeEnum.SOLID_FOREGROUND, horizontalAlignment = HorizontalAlignmentEnum.CENTER)
@HeadFontStyle(fontHeightInPoints = 11, bold = BooleanEnum.TRUE)
@ContentStyle(horizontalAlignment = HorizontalAlignmentEnum.CENTER)
public class ExportCheckInRow {

    @ExcelIgnore
    private Long studentId;

    @ExcelIgnore
    private Long classId;

    @ColumnWidth(14)
    @ExcelProperty("日期")
    private String checkinDate;

    @ColumnWidth(12)
    @ExcelProperty("年级")
    private String gradeName;

    @ColumnWidth(12)
    @ExcelProperty("班级")
    private String className;

    @ColumnWidth(14)
    @ExcelProperty("学生姓名")
    private String studentName;

    @ColumnWidth(16)
    @ExcelProperty("学号")
    private String studentNo;

    @ColumnWidth(12)
    @ExcelProperty("打卡时段")
    private String timeSlot;

    @ColumnWidth(12)
    @ExcelProperty("打卡状态")
    private String checkinStatus;

    @ColumnWidth(22)
    @ExcelProperty("打卡时间")
    private String checkinTime;
}

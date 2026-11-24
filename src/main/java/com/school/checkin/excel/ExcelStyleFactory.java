package com.school.checkin.excel;

import com.alibaba.excel.write.handler.CellWriteHandler;
import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.handler.context.CellWriteHandlerContext;
import com.alibaba.excel.write.handler.context.SheetWriteHandlerContext;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ExcelStyleFactory {

    private static final int DEFAULT_TITLE_ROWS = 3;
    private static final int COMPACT_TITLE_ROWS = 2;

    private ExcelStyleFactory() {
    }

    public static HorizontalCellStyleStrategy defaultStyle() {
        WriteCellStyle headStyle = new WriteCellStyle();
        headStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headStyle.setFillPatternType(FillPatternType.SOLID_FOREGROUND);
        headStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        headStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headStyle.setBorderBottom(BorderStyle.THIN);
        headStyle.setBorderTop(BorderStyle.THIN);
        headStyle.setBorderLeft(BorderStyle.THIN);
        headStyle.setBorderRight(BorderStyle.THIN);
        headStyle.setWrapped(true);

        WriteCellStyle contentStyle = new WriteCellStyle();
        contentStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        contentStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        contentStyle.setBorderBottom(BorderStyle.THIN);
        contentStyle.setBorderTop(BorderStyle.THIN);
        contentStyle.setBorderLeft(BorderStyle.THIN);
        contentStyle.setBorderRight(BorderStyle.THIN);
        contentStyle.setWrapped(true);

        return new HorizontalCellStyleStrategy(headStyle, contentStyle);
    }

    public static LongestMatchColumnWidthStyleStrategy autoWidth() {
        return new LongestMatchColumnWidthStyleStrategy();
    }

    public static SheetWriteHandler fixedColumnWidth(final int... widths) {
        return new SheetWriteHandler() {
            @Override
            public void afterSheetCreate(SheetWriteHandlerContext context) {
                Sheet sheet = context.getWriteSheetHolder().getSheet();
                for (int i = 0; i < widths.length; i++) {
                    int width = Math.max(widths[i], 10);
                    sheet.setColumnWidth(i, Math.min(width * 256, 255 * 256));
                }
            }
        };
    }

    public static SheetWriteHandler buildHeaderHandler(final String title,
                                                       final String subtitle,
                                                       final String extraLine,
                                                       final int lastColumnIndex) {
        return new SheetWriteHandler() {
            @Override
            public void afterSheetCreate(SheetWriteHandlerContext context) {
                Sheet sheet = context.getWriteSheetHolder().getSheet();
                createMergedInfoRow(sheet, 0, title, 26, true, IndexedColors.WHITE.getIndex(), IndexedColors.DARK_BLUE.getIndex(), lastColumnIndex);
                createMergedInfoRow(sheet, 1, subtitle, 18, false, IndexedColors.BLACK.getIndex(), IndexedColors.PALE_BLUE.getIndex(), lastColumnIndex);
                createMergedInfoRow(sheet, 2, extraLine, 16, false, IndexedColors.BLACK.getIndex(), IndexedColors.LEMON_CHIFFON.getIndex(), lastColumnIndex);
                sheet.createFreezePane(0, DEFAULT_TITLE_ROWS + 1);
            }
        };
    }

    public static SheetWriteHandler buildCompactHeaderHandler(final String title,
                                                              final String subtitle,
                                                              final int lastColumnIndex) {
        return new SheetWriteHandler() {
            @Override
            public void afterSheetCreate(SheetWriteHandlerContext context) {
                Sheet sheet = context.getWriteSheetHolder().getSheet();
                createMergedInfoRow(sheet, 0, title, 26, true, IndexedColors.WHITE.getIndex(), IndexedColors.DARK_BLUE.getIndex(), lastColumnIndex);
                createMergedInfoRow(sheet, 1, subtitle, 18, false, IndexedColors.BLACK.getIndex(), IndexedColors.PALE_BLUE.getIndex(), lastColumnIndex);
                sheet.createFreezePane(0, COMPACT_TITLE_ROWS + 1);
            }
        };
    }

    public static CellWriteHandler buildZebraHandler(final int titleRowCount) {
        return new CellWriteHandler() {
            private final Map<Short, CellStyle> zebraStyleCache = new HashMap<>();

            @Override
            public void afterCellDispose(CellWriteHandlerContext context) {
                if (Boolean.TRUE.equals(context.getHead())) {
                    return;
                }
                Integer rowIndex = context.getRowIndex();
                if (rowIndex == null || rowIndex < titleRowCount + 1) {
                    return;
                }
                Cell cell = context.getCell();
                Sheet sheet = cell.getSheet();
                Row row = sheet.getRow(rowIndex);
                if (row != null) {
                    row.setHeightInPoints(22);
                }
                if ((rowIndex - titleRowCount - 1) % 2 != 0) {
                    CellStyle baseStyle = cell.getCellStyle();
                    short styleIndex = baseStyle.getIndex();
                    CellStyle style = zebraStyleCache.get(styleIndex);
                    if (style == null) {
                        style = sheet.getWorkbook().createCellStyle();
                        style.cloneStyleFrom(baseStyle);
                        style.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
                        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                        zebraStyleCache.put(styleIndex, style);
                    }
                    cell.setCellStyle(style);
                }
            }
        };
    }

    public static int getDefaultTitleRows() {
        return DEFAULT_TITLE_ROWS;
    }

    public static int getCompactTitleRows() {
        return COMPACT_TITLE_ROWS;
    }

    public static List<List<String>> buildTemplateNotes() {
        List<List<String>> notes = new ArrayList<>();
        notes.add(single("模板说明：校园点亮金牌花名册导入模板"));
        notes.add(single("必填列：年级、班级、学生姓名。建议每个班级使用唯一教师账号。"));
        notes.add(single("导入后将自动生成班级免登录打卡链接，班主任账号默认密码为 123456。"));
        return notes;
    }

    private static List<String> single(String value) {
        List<String> row = new ArrayList<>();
        row.add(value);
        return row;
    }

    private static void createMergedInfoRow(Sheet sheet,
                                            int rowIndex,
                                            String value,
                                            int fontSize,
                                            boolean bold,
                                            short fontColor,
                                            short backgroundColor,
                                            int lastColumnIndex) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(rowIndex == 0 ? 30 : 22);
        Cell cell = row.createCell(0);
        cell.setCellValue(value);
        cell.setCellStyle(buildInfoStyle(sheet, fontSize, bold, fontColor, backgroundColor));
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, lastColumnIndex));
        for (int i = 1; i <= lastColumnIndex; i++) {
            Cell mergedCell = row.createCell(i);
            mergedCell.setCellStyle(cell.getCellStyle());
        }
    }

    private static CellStyle buildInfoStyle(Sheet sheet,
                                            int fontSize,
                                            boolean bold,
                                            short fontColor,
                                            short backgroundColor) {
        CellStyle style = sheet.getWorkbook().createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(backgroundColor);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        Font font = sheet.getWorkbook().createFont();
        font.setFontName("Microsoft YaHei");
        font.setFontHeightInPoints((short) fontSize);
        font.setBold(bold);
        font.setColor(fontColor);
        style.setFont(font);
        return style;
    }
}

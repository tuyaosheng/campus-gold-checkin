package com.school.checkin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.checkin.entity.StudentInfo;
import com.school.checkin.vo.ExportStudentScopeVO;
import com.school.checkin.vo.StudentOrderBindingVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface StudentInfoMapper extends BaseMapper<StudentInfo> {

    @Select({
            "<script>",
            "SELECT",
            "  s.id AS studentId,",
            "  s.class_id AS classId,",
            "  c.grade_name AS gradeName,",
            "  c.class_name AS className,",
            "  s.student_name AS studentName,",
            "  s.student_no AS studentNo",
            "FROM student_info s",
            "INNER JOIN class_info c ON s.class_id = c.id",
            "WHERE s.enabled = 1",
            "  AND c.enabled = 1",
            "<if test='gradeName != null and gradeName != \"\"'>",
            "  AND c.grade_name = #{gradeName}",
            "</if>",
            "<if test='classId != null'>",
            "  AND c.id = #{classId}",
            "</if>",
            "ORDER BY c.grade_name ASC, c.class_name ASC, COALESCE(s.display_order_no, 9999) ASC, s.student_no ASC, s.id ASC",
            "</script>"
    })
    List<ExportStudentScopeVO> selectExportStudents(@Param("gradeName") String gradeName,
                                                    @Param("classId") Long classId);

    @Select({
            "SELECT",
            "  s.id AS studentId,",
            "  s.student_no AS studentNo,",
            "  s.student_name AS studentName,",
            "  s.display_order_no AS displayOrderNo",
            "FROM student_info s",
            "WHERE s.class_id = #{classId}",
            "  AND s.enabled = 1",
            "ORDER BY COALESCE(s.display_order_no, 9999) ASC, s.student_no ASC, s.id ASC"
    })
    List<StudentOrderBindingVO> selectStudentOrderBindings(@Param("classId") Long classId);
}

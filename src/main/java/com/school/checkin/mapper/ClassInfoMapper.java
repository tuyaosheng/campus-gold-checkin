package com.school.checkin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.checkin.entity.ClassInfo;
import com.school.checkin.vo.ClassAccessVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ClassInfoMapper extends BaseMapper<ClassInfo> {

    @Select({
            "SELECT",
            "  c.id AS classId,",
            "  c.grade_name AS gradeName,",
            "  c.class_name AS className,",
            "  c.teacher_name AS teacherName,",
            "  c.student_count AS studentCount,",
            "  c.unique_code AS uniqueCode,",
            "  u.username AS teacherUsername",
            "FROM class_info c",
            "LEFT JOIN sys_user u ON u.class_id = c.id AND u.role_code = 'TEACHER' AND u.enabled = 1",
            "WHERE c.enabled = 1",
            "ORDER BY c.grade_name ASC, c.class_name ASC, c.id ASC"
    })
    List<ClassAccessVO> selectClassAccessList();

    @Select({
            "<script>",
            "SELECT",
            "  c.id AS classId,",
            "  c.grade_name AS gradeName,",
            "  c.class_name AS className,",
            "  c.teacher_name AS teacherName,",
            "  c.student_count AS studentCount,",
            "  c.unique_code AS uniqueCode,",
            "  u.username AS teacherUsername",
            "FROM class_info c",
            "LEFT JOIN sys_user u ON u.class_id = c.id AND u.role_code = 'TEACHER' AND u.enabled = 1",
            "WHERE c.enabled = 1",
            "<if test='gradeName != null and gradeName != \"\"'>",
            "  AND c.grade_name = #{gradeName}",
            "</if>",
            "<if test='classId != null'>",
            "  AND c.id = #{classId}",
            "</if>",
            "ORDER BY c.grade_name ASC, c.class_name ASC, c.id ASC",
            "</script>"
    })
    List<ClassAccessVO> selectClassAccessListByScope(@Param("gradeName") String gradeName,
                                                     @Param("classId") Long classId);
}

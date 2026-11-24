package com.school.checkin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.checkin.entity.CheckInRecord;
import com.school.checkin.vo.CorrectionRecordVO;
import com.school.checkin.vo.ExportCheckInRow;
import com.school.checkin.vo.LeaderboardVO;
import com.school.checkin.vo.SchoolTopStudentVO;
import com.school.checkin.vo.StudentQualityRecordVO;
import com.school.checkin.vo.StudentWeeklyLeaderboardVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface CheckInRecordMapper extends BaseMapper<CheckInRecord> {

    @Select({
            "<script>",
            "SELECT",
            "  c.id AS classId,",
            "  c.grade_name AS gradeName,",
            "  c.class_name AS className,",
            "  c.student_count AS totalStudents,",
            "  IFNULL(r.checked_students, 0) AS checkedStudents,",
            "  CASE WHEN c.student_count = 0 THEN 0",
            "       ELSE ROUND(IFNULL(r.checked_students, 0) / c.student_count * 100, 2)",
            "  END AS attendanceRate",
            "FROM class_info c",
            "LEFT JOIN (",
            "  SELECT class_id, COUNT(DISTINCT student_id) AS checked_students",
            "  FROM check_in_record",
            "  WHERE checkin_date = #{checkinDate}",
            "  AND time_slot = #{timeSlot}",
            "  GROUP BY class_id",
            ") r ON r.class_id = c.id",
            "WHERE c.enabled = 1",
            "<if test='gradeName != null and gradeName != \"\"'>",
            "  AND c.grade_name = #{gradeName}",
            "</if>",
            "<if test='classId != null'>",
            "  AND c.id = #{classId}",
            "</if>",
            "ORDER BY attendanceRate DESC, checkedStudents DESC, c.id ASC",
            "</script>"
    })
    List<LeaderboardVO> selectTodayLeaderboard(@Param("checkinDate") LocalDate checkinDate,
                                               @Param("timeSlot") String timeSlot,
                                               @Param("gradeName") String gradeName,
                                               @Param("classId") Long classId);

    @Select({
            "<script>",
            "SELECT",
            "  r.class_id AS classId,",
            "  r.student_id AS studentId,",
            "  DATE_FORMAT(r.checkin_date, '%Y-%m-%d') AS checkinDate,",
            "  c.grade_name AS gradeName,",
            "  c.class_name AS className,",
            "  s.student_name AS studentName,",
            "  s.student_no AS studentNo,",
            "  r.time_slot AS timeSlot,",
            "  DATE_FORMAT(r.checkin_time, '%Y-%m-%d %H:%i:%s') AS checkinTime",
            "FROM check_in_record r",
            "INNER JOIN student_info s ON r.student_id = s.id",
            "INNER JOIN class_info c ON r.class_id = c.id",
            "WHERE r.checkin_time &gt;= #{startTime}",
            "  AND r.checkin_time &lt; #{endTime}",
            "<if test='gradeName != null and gradeName != \"\"'>",
            "  AND c.grade_name = #{gradeName}",
            "</if>",
            "<if test='classId != null'>",
            "  AND r.class_id = #{classId}",
            "</if>",
            "ORDER BY r.checkin_time ASC, c.id ASC, s.id ASC",
            "</script>"
    })
    List<ExportCheckInRow> selectExportRows(@Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime,
                                            @Param("gradeName") String gradeName,
                                            @Param("classId") Long classId);

    @Select({
            "<script>",
            "SELECT",
            "  s.id AS studentId,",
            "  s.student_no AS studentNo,",
            "  s.student_name AS studentName,",
            "  c.grade_name AS gradeName,",
            "  c.class_name AS className,",
            "  r.time_slot AS timeSlot,",
            "  DATE_FORMAT(r.checkin_time, '%Y-%m-%d %H:%i:%s') AS checkinTime",
            "FROM check_in_record r",
            "INNER JOIN student_info s ON r.student_id = s.id",
            "INNER JOIN class_info c ON r.class_id = c.id",
            "WHERE r.checkin_time &gt;= #{startTime}",
            "  AND r.checkin_time &lt; #{endTime}",
            "  AND s.enabled = 1",
            "  AND c.enabled = 1",
            "<if test='classId != null'>",
            "  AND c.id = #{classId}",
            "</if>",
            "<if test='gradeName != null and gradeName != \"\"'>",
            "  AND c.grade_name = #{gradeName}",
            "</if>",
            "ORDER BY r.checkin_time ASC, s.id ASC",
            "</script>"
    })
    List<StudentQualityRecordVO> selectStudentQualityRecords(@Param("startTime") LocalDateTime startTime,
                                                             @Param("endTime") LocalDateTime endTime,
                                                             @Param("classId") Long classId,
                                                             @Param("gradeName") String gradeName);

    @Select({
            "<script>",
            "SELECT",
            "  s.id AS studentId,",
            "  s.student_no AS studentNo,",
            "  s.student_name AS studentName,",
            "  COUNT(r.id) AS totalCheckinCount,",
            "  DATE_FORMAT(MIN(r.checkin_time), '%Y-%m-%d %H:%i:%s') AS firstCheckinTime",
            "FROM student_info s",
            "LEFT JOIN check_in_record r ON r.student_id = s.id",
            "  AND r.checkin_time &gt;= #{startTime}",
            "  AND r.checkin_time &lt; #{endTime}",
            "WHERE s.class_id = #{classId}",
            "  AND s.enabled = 1",
            "GROUP BY s.id, s.student_no, s.student_name",
            "ORDER BY totalCheckinCount DESC,",
            "  CASE WHEN MIN(r.checkin_time) IS NULL THEN 1 ELSE 0 END ASC,",
            "  MIN(r.checkin_time) ASC,",
            "  s.id ASC",
            "</script>"
    })
    List<StudentWeeklyLeaderboardVO> selectWeeklyStudentLeaderboard(@Param("classId") Long classId,
                                                                    @Param("startTime") LocalDateTime startTime,
                                                                    @Param("endTime") LocalDateTime endTime);

    @Select({
            "SELECT",
            "  s.id AS studentId,",
            "  s.student_name AS studentName,",
            "  c.grade_name AS gradeName,",
            "  c.class_name AS className,",
            "  COUNT(r.id) AS totalCheckinCount",
            "FROM check_in_record r",
            "INNER JOIN student_info s ON r.student_id = s.id",
            "INNER JOIN class_info c ON r.class_id = c.id",
            "WHERE r.checkin_time >= #{startTime}",
            "  AND r.checkin_time < #{endTime}",
            "GROUP BY s.id, s.student_name, c.grade_name, c.class_name",
            "ORDER BY totalCheckinCount DESC, MIN(r.checkin_time) ASC, s.id ASC",
            "LIMIT 1"
    })
    SchoolTopStudentVO selectSchoolTopStudent(@Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    @Select({
            "SELECT",
            "  s.id AS studentId,",
            "  s.student_name AS studentName,",
            "  c.grade_name AS gradeName,",
            "  c.class_name AS className,",
            "  COUNT(r.id) AS totalCheckinCount",
            "FROM check_in_record r",
            "INNER JOIN student_info s ON r.student_id = s.id",
            "INNER JOIN class_info c ON r.class_id = c.id",
            "WHERE r.checkin_time >= #{startTime}",
            "  AND r.checkin_time < #{endTime}",
            "  AND c.grade_name = #{gradeName}",
            "GROUP BY s.id, s.student_name, c.grade_name, c.class_name",
            "ORDER BY totalCheckinCount DESC, MIN(r.checkin_time) ASC, s.id ASC",
            "LIMIT 1"
    })
    SchoolTopStudentVO selectGradeTopStudent(@Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime,
                                             @Param("gradeName") String gradeName);

    @Select({
            "<script>",
            "SELECT",
            "  c.id AS classId,",
            "  c.grade_name AS gradeName,",
            "  c.class_name AS className,",
            "  s.id AS studentId,",
            "  s.student_no AS studentNo,",
            "  s.student_name AS studentName,",
            "  CASE WHEN r.id IS NULL THEN 0 ELSE 1 END AS checkedIn,",
            "  DATE_FORMAT(r.checkin_time, '%Y-%m-%d %H:%i:%s') AS checkinTime",
            "FROM student_info s",
            "INNER JOIN class_info c ON s.class_id = c.id",
            "LEFT JOIN check_in_record r ON r.student_id = s.id",
            "  AND r.checkin_date = #{checkinDate}",
            "  AND r.time_slot = #{timeSlot}",
            "WHERE s.enabled = 1",
            "  AND c.enabled = 1",
            "<if test='gradeName != null and gradeName != \"\"'>",
            "  AND c.grade_name = #{gradeName}",
            "</if>",
            "<if test='classId != null'>",
            "  AND c.id = #{classId}",
            "</if>",
            "ORDER BY c.grade_name ASC, c.class_name ASC, s.student_no ASC, s.id ASC",
            "</script>"
    })
    List<CorrectionRecordVO> selectCorrectionRows(@Param("checkinDate") LocalDate checkinDate,
                                                  @Param("timeSlot") String timeSlot,
                                                  @Param("gradeName") String gradeName,
                                                  @Param("classId") Long classId);
}

package interview.guide.modules.interviewschedule.repository;

import interview.guide.modules.interviewschedule.model.InterviewScheduleEntity;
import interview.guide.modules.interviewschedule.model.InterviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewScheduleRepository extends JpaRepository<InterviewScheduleEntity, Long> {
    List<InterviewScheduleEntity> findByStatusAndInterviewTimeBefore(InterviewStatus status, LocalDateTime time);

    List<InterviewScheduleEntity> findByStatus(InterviewStatus status);

    List<InterviewScheduleEntity> findByInterviewTimeBetween(LocalDateTime start, LocalDateTime end);

    List<InterviewScheduleEntity> findByUserId(Long userId);

    List<InterviewScheduleEntity> findByUserIdAndStatus(Long userId, InterviewStatus status);

    List<InterviewScheduleEntity> findByUserIdAndInterviewTimeBetween(Long userId, LocalDateTime start, LocalDateTime end);

    Optional<InterviewScheduleEntity> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("UPDATE InterviewScheduleEntity e SET e.status = :newStatus WHERE e.status = :oldStatus AND e.interviewTime < :cutoff")
    int updateStatusByStatusAndInterviewTimeBefore(
        @Param("newStatus") InterviewStatus newStatus,
        @Param("oldStatus") InterviewStatus oldStatus,
        @Param("cutoff") LocalDateTime cutoff);
}

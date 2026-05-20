package sa.nrd.job.execute.repository;

import sa.nrd.job.execute.constant.ExecutionStatus;
import sa.nrd.job.execute.model.manage.JobExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobExecutionStatusRepository extends JpaRepository<JobExecutionStatus, ExecutionStatus> {
    JobExecutionStatus findByStatus(ExecutionStatus status);
}
 
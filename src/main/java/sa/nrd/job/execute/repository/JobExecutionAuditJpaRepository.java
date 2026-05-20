package sa.nrd.job.execute.repository;

import sa.nrd.job.execute.model.manage.JobExecutionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobExecutionAuditJpaRepository extends JpaRepository<JobExecutionAudit, Long> {
}
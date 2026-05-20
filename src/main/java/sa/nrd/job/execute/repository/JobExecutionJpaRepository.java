package sa.nrd.job.execute.repository;

import sa.nrd.job.execute.model.manage.JobExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobExecutionJpaRepository extends JpaRepository<JobExecutionEntity, Long> {
}
package sa.nrd.job.execute.repository;

import sa.nrd.job.execute.model.manage.JobDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobDetailsJpaRepository extends JpaRepository<JobDetails, String> {
}
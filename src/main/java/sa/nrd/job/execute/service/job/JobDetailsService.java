package sa.nrd.job.execute.service.job;

import sa.nrd.job.execute.model.manage.JobDetails;
import sa.nrd.job.execute.repository.JobDetailsJpaRepository;
import org.springframework.stereotype.Service;

@Service
public class JobDetailsService {

    private final JobDetailsJpaRepository jobDetailsRepository;
    /**
     * Creates the service with the required repository dependency.
     * @param jobDetailsRepository repository for job details
     */
    public JobDetailsService(JobDetailsJpaRepository jobDetailsRepository) {
        this.jobDetailsRepository = jobDetailsRepository;
    }

    /**
     * Returns the job details for the given job name.
     * @param jobName job identifier
     * @return job details entity
     */
    public JobDetails getJobRequired(String jobName) {
        return jobDetailsRepository.findById(jobName)
                .orElseThrow(() -> new IllegalArgumentException("Job not found for name: " + jobName));
    }
}
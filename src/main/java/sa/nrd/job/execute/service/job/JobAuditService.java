package sa.nrd.job.execute.service.job;

import sa.nrd.job.execute.constant.ExecutionStatus;
import sa.nrd.job.execute.repository.JobExecutionAuditJpaRepository;
import sa.nrd.job.execute.repository.JobExecutionStatusRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;


@Service
public class JobAuditService {

    private final JobExecutionAuditJpaRepository repository;

private final JobExecutionStatusRepository statusRepository;
    public JobAuditService(JobExecutionAuditJpaRepository repository, JobExecutionStatusRepository statusRepository) {
        this.repository = repository;
        this.statusRepository = statusRepository;
    }

    public void updateStatus(Long auditId, ExecutionStatus status) {
        repository.findById(auditId).ifPresent(audit -> {
            audit.setStatus(statusRepository.findByStatus(status));
            audit.setEndTime(LocalDateTime.now());
            repository.save(audit);
        });
    }
}

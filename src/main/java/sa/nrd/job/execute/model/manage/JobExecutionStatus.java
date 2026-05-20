package sa.nrd.job.execute.model.manage;

import jakarta.persistence.*;
import lombok.Data;
import sa.nrd.job.execute.constant.ExecutionStatus;

@Entity
@Table(name = "JOB_EXECUTION_STATUS", schema = "EXT")
@Data
public class JobExecutionStatus {

    @Id
    @Column(name = "STATUS")
    @Enumerated(EnumType.STRING)
    private ExecutionStatus status;

    @Column(name = "DESCRIPTION")
    private String description;
}
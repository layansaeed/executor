package sa.nrd.job.execute.model.manage;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "JOB_EXECUTION", schema = "EXT")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobExecutionEntity {

    @Id
    @Column(name = "EXECUTION_ID")
    private Long executionId;

    @Column(name = "JOB_NAME")
    private String jobName;

    @Column(name = "AUDIT_ID")
    private Long auditId;

    @Column(name = "SERVER_NAME")
    private String serverName;

    @Column(name = "NIN_RANGE_START")
    private Long ninRangeStart;

    @Column(name = "NIN_RANGE_END")
    private Long ninRangeEnd;
}
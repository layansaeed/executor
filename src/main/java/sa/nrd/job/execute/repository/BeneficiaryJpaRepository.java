package sa.nrd.job.execute.repository;

import sa.nrd.job.execute.model.beneficiary.BeneficiaryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BeneficiaryJpaRepository extends JpaRepository<BeneficiaryEntity, Long> {

    @Query(
            value = "SELECT b FROM BeneficiaryEntity b " +
                    "WHERE b.rowNum BETWEEN :start AND :end " +
                    "ORDER BY b.nin ASC")
    Page<BeneficiaryEntity> findBeneficiaries(
            Pageable pageable,
            @Param("start") Long start,
            @Param("end") Long end
    );
}
package org.metrics.defectlab.dataset.persistence;

import java.util.List;
import java.util.Optional;

import org.metrics.defectlab.dataset.domain.MetricDataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MetricDatasetRepository extends JpaRepository<MetricDataset, Long> {

    @Query("select d from MetricDataset d where d.userId = :userId or d.userId is null "
            + "order by d.createdAt desc")
    List<MetricDataset> findVisibleTo(@Param("userId") Long userId);

    @Query("select d from MetricDataset d where d.id = :id "
            + "and (d.userId = :userId or d.userId is null)")
    Optional<MetricDataset> findVisibleById(@Param("id") Long id,
                                            @Param("userId") Long userId);

    Optional<MetricDataset>
            findByUserIdIsNullAndProjectNameIgnoreCaseAndProjectVersionAndDatasetType(
            String projectName,
            String projectVersion,
            MetricDataset.Type datasetType);

    boolean existsByUserIdAndDatasetFamilyAndProjectNameAndProjectVersionAndDatasetType(
            Long userId,
            MetricDataset.Family datasetFamily,
            String projectName,
            String projectVersion,
            MetricDataset.Type datasetType);

    long countByUserId(Long userId);
}

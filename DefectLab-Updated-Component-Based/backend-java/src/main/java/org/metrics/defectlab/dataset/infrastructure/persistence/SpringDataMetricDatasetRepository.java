package org.metrics.defectlab.dataset.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.metrics.defectlab.dataset.domain.MetricDataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataMetricDatasetRepository extends JpaRepository<MetricDatasetJpaEntity, Long> {

    @Query("select d from MetricDatasetJpaEntity d where d.userId = :userId or d.userId is null "
            + "order by d.createdAt desc")
    List<MetricDatasetJpaEntity> findVisibleTo(@Param("userId") Long userId);

    @Query("select d from MetricDatasetJpaEntity d where d.id = :id "
            + "and (d.userId = :userId or d.userId is null)")
    Optional<MetricDatasetJpaEntity> findVisibleById(@Param("id") Long id,
                                            @Param("userId") Long userId);

    Optional<MetricDatasetJpaEntity>
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
}

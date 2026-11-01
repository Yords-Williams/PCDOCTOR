package com.pcdoctor.repository;

import com.pcdoctor.model.YWCMDiagnostico;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface YWCMDiagnosticoRepository extends JpaRepository<YWCMDiagnostico, Long> {
    List<YWCMDiagnostico> findTop100ByOrderByYWCMfechaDesc();
}

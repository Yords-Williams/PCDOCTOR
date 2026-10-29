package com.pcdoctor.repository;

import com.pcdoctor.model.Diagnostico;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DiagnosticoRepository extends JpaRepository<Diagnostico, Long> {
    List<Diagnostico> findTop100ByOrderByFechaDesc();
}

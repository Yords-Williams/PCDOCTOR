package com.pcdoctor.service;

import com.pcdoctor.model.YWCMDiagnostico;
import com.pcdoctor.model.YWCMProblema;

import java.util.List;

public record YWCMAnalisisCompleto(
        String sistema,
        String almacenamiento,
        List<YWCMDiagnostico> diagnosticos,
        List<YWCMProblema> problemas) {
}

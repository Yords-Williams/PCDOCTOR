package com.pcdoctor.service;

import com.pcdoctor.model.Diagnostico;
import com.pcdoctor.model.Problema;

import java.util.List;

public record AnalisisCompleto(
        String sistema,
        String almacenamiento,
        List<Diagnostico> diagnosticos,
        List<Problema> problemas) {
}

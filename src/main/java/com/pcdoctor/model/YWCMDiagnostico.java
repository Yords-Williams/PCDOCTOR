package com.pcdoctor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "diagnosticos")
public class YWCMDiagnostico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long YWCMid;

    @Column(name = "fecha")
    private LocalDateTime YWCMfecha;

    @Column(name = "categoria")
    private String YWCMcategoria;

    @Column(name = "resultado")
    private String YWCMresultado;

    @Column(name = "detalle")
    private String YWCMdetalle;

    public YWCMDiagnostico() {}

    public YWCMDiagnostico(String YWCMcategoria, String YWCMresultado, String YWCMdetalle) {
        this.YWCMfecha = LocalDateTime.now();
        this.YWCMcategoria = YWCMcategoria;
        this.YWCMresultado = YWCMresultado;
        this.YWCMdetalle = YWCMdetalle;
    }

    public Long YWCMgetId() { return YWCMid; }
    public LocalDateTime YWCMgetFecha() { return YWCMfecha; }
    public String YWCMgetCategoria() { return YWCMcategoria; }
    public String YWCMgetResultado() { return YWCMresultado; }
    public String YWCMgetDetalle() { return YWCMdetalle; }

    public void YWCMsetId(Long YWCMid) { this.YWCMid = YWCMid; }
    public void YWCMsetFecha(LocalDateTime YWCMfecha) { this.YWCMfecha = YWCMfecha; }
    public void YWCMsetCategoria(String YWCMcategoria) { this.YWCMcategoria = YWCMcategoria; }
    public void YWCMsetResultado(String YWCMresultado) { this.YWCMresultado = YWCMresultado; }
    public void YWCMsetDetalle(String YWCMdetalle) { this.YWCMdetalle = YWCMdetalle; }
}

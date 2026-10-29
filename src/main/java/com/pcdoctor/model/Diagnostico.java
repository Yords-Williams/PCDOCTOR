package com.pcdoctor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "diagnosticos")
public class Diagnostico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime fecha;
    private String categoria;
    private String resultado;
    private String detalle;

    public Diagnostico() {}

    public Diagnostico(String categoria, String resultado, String detalle) {
        this.fecha = LocalDateTime.now();
        this.categoria = categoria;
        this.resultado = resultado;
        this.detalle = detalle;
    }

    public Long getId() { return id; }
    public LocalDateTime getFecha() { return fecha; }
    public String getCategoria() { return categoria; }
    public String getResultado() { return resultado; }
    public String getDetalle() { return detalle; }

    public void setId(Long id) { this.id = id; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public void setResultado(String resultado) { this.resultado = resultado; }
    public void setDetalle(String detalle) { this.detalle = detalle; }
}

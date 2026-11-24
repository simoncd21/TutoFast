package com.tutofast.modelo;

import java.math.BigDecimal;

/** Metadatos de un archivo adjunto (embebido). El binario vive en GridFS ({@code gridFsId}). */
public class Archivo {

    public static final BigDecimal TAMANO_MAXIMO_MB = BigDecimal.TEN;

    private String nombre;
    private TipoArchivo tipo;
    private BigDecimal tamanoMB;
    private String gridFsId;

    public Archivo() {
    }

    public Archivo(String nombre, TipoArchivo tipo, BigDecimal tamanoMB, String gridFsId) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.tamanoMB = tamanoMB;
        this.gridFsId = gridFsId;
    }

    public boolean excedeTamanoMaximo() {
        return tamanoMB != null && tamanoMB.compareTo(TAMANO_MAXIMO_MB) > 0;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public TipoArchivo getTipo() { return tipo; }
    public void setTipo(TipoArchivo tipo) { this.tipo = tipo; }
    public BigDecimal getTamanoMB() { return tamanoMB; }
    public void setTamanoMB(BigDecimal tamanoMB) { this.tamanoMB = tamanoMB; }
    public String getGridFsId() { return gridFsId; }
    public void setGridFsId(String gridFsId) { this.gridFsId = gridFsId; }
}

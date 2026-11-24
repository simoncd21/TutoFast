package com.tutofast.integracion.puertos;

/** Puerto de notificaciones: solo se guardan en MongoDB y se consultan en la web (sin correo ni SMTP). */
public interface INotificador {

    void notificar(String usuarioId, TipoNotificacion tipo, String titulo, String mensaje, String enlace);
}

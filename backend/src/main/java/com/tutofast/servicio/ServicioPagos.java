package com.tutofast.servicio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.tutofast.dto.LiquidacionDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.integracion.puertos.IPasarelaPago;
import com.tutofast.integracion.puertos.ResultadoTransaccion;
import com.tutofast.mapper.PagoMapper;
import com.tutofast.modelo.Pago;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.ServicioTutoria;
import com.tutofast.repositorio.RepositorioPago;
import com.tutofast.repositorio.RepositorioServicioTutoria;
import com.tutofast.seguridad.UsuarioAutenticado;

/**
 * Pagos simulados (R11–R12). El cobro pasa por {@link IPasarelaPago} con un token opaco: la plataforma nunca recibe
 * ni guarda datos de tarjeta; del pago solo se conserva la referencia de la pasarela y los montos.
 */
@Service
public class ServicioPagos {

    private static final Logger log = LoggerFactory.getLogger(ServicioPagos.class);

    private final IPasarelaPago pasarela;
    private final RepositorioPago pagos;
    private final RepositorioServicioTutoria servicios;
    private final ServicioAuditoria auditoria;

    public ServicioPagos(IPasarelaPago pasarela, RepositorioPago pagos, RepositorioServicioTutoria servicios,
                         ServicioAuditoria auditoria) {
        this.pasarela = pasarela;
        this.pagos = pagos;
        this.servicios = servicios;
        this.auditoria = auditoria;
    }

    /**
     * Cobra el precio acordado en la pasarela y deja los fondos RETENIDOS. Calcula de una vez la comisión y el neto
     * con la comisión congelada en el servicio (HALF_UP a pesos enteros). Si la pasarela rechaza: 422
     * {@code PAGO_RECHAZADO} y no se guarda nada.
     */
    public Pago retenerFondos(ServicioTutoria servicio, String tokenPago) {
        ResultadoTransaccion resultado = pasarela.procesarPago(tokenPago, servicio.getPrecioFinalCOP());
        if (resultado == null || !resultado.aprobada()) {
            throw new ExcepcionNegocio("PAGO_RECHAZADO",
                    resultado == null || resultado.mensaje() == null ? "El pago fue rechazado" : resultado.mensaje(),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Instant ahora = Instant.now();
        Pago pago = Pago.retener(servicio.getId(), servicio.getPrecioFinalCOP(), resultado.referencia(), ahora);
        pago.calcularLiquidacion(servicio.getComisionPct());
        try {
            pago = pagos.save(pago);
        } catch (DuplicateKeyException e) {
            throw new ExcepcionNegocio("PAGO_DUPLICADO", "El servicio ya tiene un pago registrado", HttpStatus.CONFLICT);
        }
        auditoria.registrarAccion(servicio.getEstudianteId(), "PAGO_RETENIDO", "pago:" + pago.getId(),
                detalle(servicio, pago));
        return pago;
    }

    /**
     * Libera los fondos al tutor al finalizar el servicio: liquida con la comisión congelada del servicio (R12,
     * HALF_UP a pesos enteros) y deja el pago LIBERADO. Acepta un pago RETENIDO o CONGELADO (resolución de un reporte
     * a favor del tutor); en cualquier otro estado, 409. {@code actorId} es nulo cuando actúa el sistema.
     */
    public Pago liberarPago(ServicioTutoria servicio, String actorId, Instant ahora) {
        Pago pago = exigirPago(servicio.getId());
        pago.liberar(servicio.getComisionPct(), ahora);
        pago = pagos.save(pago);
        auditoria.registrarAccion(actorId, "PAGO_LIBERADO", "pago:" + pago.getId(), detalle(servicio, pago));
        return pago;
    }

    /** Congela un pago RETENIDO mientras se revisa un reporte (409 en otro estado). */
    public Pago congelarPago(String servicioId, String actorId, Instant ahora) {
        Pago pago = exigirPago(servicioId);
        pago.congelar(ahora);
        pago = pagos.save(pago);
        auditoria.registrarAccion(actorId, "PAGO_CONGELADO", "pago:" + pago.getId(), mapa("servicioId", servicioId));
        return pago;
    }

    /** Devuelve a RETENIDO un pago CONGELADO cuando el reporte no prosperó o se compensa un fallo (409 en otro estado). */
    public Pago descongelarPago(String servicioId, String actorId, Instant ahora) {
        Pago pago = exigirPago(servicioId);
        pago.descongelar(ahora);
        pago = pagos.save(pago);
        auditoria.registrarAccion(actorId, "PAGO_DESCONGELADO", "pago:" + pago.getId(), mapa("servicioId", servicioId));
        return pago;
    }

    /** Devuelve al estudiante el monto completo, sin comisión, de un pago CONGELADO (409 en otro estado). */
    public Pago reembolsarPago(String servicioId, String actorId, Instant ahora) {
        Pago pago = exigirPago(servicioId);
        pago.reembolsar(ahora);
        pago = pagos.save(pago);
        auditoria.registrarAccion(actorId, "PAGO_REEMBOLSADO", "pago:" + pago.getId(),
                mapa("servicioId", servicioId, "montoCOP", texto(pago.getMontoRetenidoCOP())));
        return pago;
    }

    private Pago exigirPago(String servicioId) {
        return pagos.findByServicioId(servicioId).orElseThrow(() -> new ExcepcionNegocio("PAGO_NO_ENCONTRADO",
                "El servicio no tiene un pago registrado", HttpStatus.CONFLICT));
    }

    private static Map<String, Object> mapa(Object... pares) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pares.length; i += 2) {
            if (pares[i + 1] != null) {
                mapa.put((String) pares[i], pares[i + 1]);
            }
        }
        return mapa;
    }

    /** Elimina un pago ya guardado (compensación si la confirmación falla después de retener). */
    public void descartarPago(Pago pago) {
        try {
            pagos.deleteById(pago.getId());
        } catch (RuntimeException e) {
            log.warn("No se pudo compensar el pago {}", pago.getId(), e);
        }
    }

    /** El pago del servicio o {@code null} si todavía no existe. */
    public Pago buscarPorServicio(String servicioId) {
        return pagos.findByServicioId(servicioId).orElse(null);
    }

    /** Liquidación de un servicio para uno de sus participantes o un administrador. */
    public LiquidacionDTO consultarLiquidacion(UsuarioAutenticado usuario, String servicioId) {
        ServicioTutoria servicio = servicios.findById(servicioId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Servicio no encontrado"));
        if (!servicio.esParticipante(usuario.id()) && !usuario.tieneAlgunRol(Rol.ADMINISTRADOR)) {
            throw new ExcepcionAutorizacion("No tiene acceso al pago de este servicio");
        }
        Pago pago = pagos.findByServicioId(servicioId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("El servicio no tiene un pago registrado"));
        return PagoMapper.aLiquidacionDTO(pago, servicio);
    }

    /** Egresos del estudiante o ingresos del tutor: los pagos de todos los servicios del usuario, recientes primero. */
    public List<LiquidacionDTO> listarMios(String usuarioId) {
        List<ServicioTutoria> propios = servicios.findByEstudianteIdOrTutorIdOrderByFechaConfirmacionDesc(usuarioId, usuarioId);
        if (propios.isEmpty()) {
            return List.of();
        }
        Map<String, Pago> porServicio = pagos.findByServicioIdIn(propios.stream().map(ServicioTutoria::getId).toList())
                .stream().collect(Collectors.toMap(Pago::getServicioId, Function.identity(), (a, b) -> a));
        return propios.stream().filter(s -> porServicio.containsKey(s.getId()))
                .map(s -> PagoMapper.aLiquidacionDTO(porServicio.get(s.getId()), s)).toList();
    }

    private static Map<String, Object> detalle(ServicioTutoria servicio, Pago pago) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("servicioId", servicio.getId());
        mapa.put("montoCOP", texto(pago.getMontoRetenidoCOP()));
        mapa.put("comisionPct", texto(servicio.getComisionPct()));
        mapa.put("comisionCOP", texto(pago.getComisionCOP()));
        mapa.put("montoNetoCOP", texto(pago.getMontoNetoCOP()));
        mapa.put("referencia", pago.getReferenciaPasarela());
        return mapa;
    }

    private static String texto(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }
}

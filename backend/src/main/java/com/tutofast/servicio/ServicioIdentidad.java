package com.tutofast.servicio;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.tutofast.dto.InicioSesionDTO;
import com.tutofast.dto.PerfilCompletoDTO;
import com.tutofast.dto.PerfilEstudianteDTO;
import com.tutofast.dto.PerfilTutorDTO;
import com.tutofast.dto.RegistroDTO;
import com.tutofast.dto.RegistroRespuestaDTO;
import com.tutofast.dto.SesionDTO;
import com.tutofast.dto.ArchivoDTO;
import com.tutofast.dto.SoporteDTO;
import com.tutofast.dto.UsuarioDTO;
import com.tutofast.excepcion.ExcepcionAutorizacion;
import com.tutofast.excepcion.ExcepcionNegocio;
import com.tutofast.excepcion.ExcepcionNoAutenticado;
import com.tutofast.excepcion.ExcepcionNoEncontrado;
import com.tutofast.mapper.UsuarioMapper;
import com.tutofast.modelo.EstadoCuenta;
import com.tutofast.modelo.NivelVerificacion;
import com.tutofast.modelo.PerfilEstudiante;
import com.tutofast.modelo.PerfilTutor;
import com.tutofast.modelo.Rol;
import com.tutofast.modelo.SoporteVerificacion;
import com.tutofast.modelo.TipoSoporte;
import com.tutofast.modelo.Usuario;
import com.tutofast.repositorio.RepositorioUsuario;
import com.tutofast.seguridad.JwtUtil;
import com.tutofast.seguridad.UsuarioAutenticado;

/** Registro, autenticación y perfiles de usuario. */
@Service
public class ServicioIdentidad {

    public static final String MENSAJE_REGISTRO = "Registro exitoso en TutoFast";
    public static final String MENSAJE_CONSENTIMIENTO =
            "Debe aceptar la política de tratamiento de datos conforme a la Ley 1581 de 2012 para continuar";
    /** Tope de soportes pendientes de revisión por tutor (evita inundar la cola del administrador). */
    public static final int MAX_SOPORTES_EN_REVISION = 5;
    static final String ENTIDAD_USUARIO = "Usuario";
    static final String MENSAJE_CREDENCIALES = "Correo o contraseña incorrectos";

    private final RepositorioUsuario repositorio;
    private final PasswordEncoder codificador;
    private final JwtUtil jwtUtil;
    private final ServicioAuditoria auditoria;
    private final ServicioArchivos archivos;
    /** Hash descartable para que un correo inexistente tarde lo mismo que uno con clave errónea. */
    private final String hashFalso;

    public ServicioIdentidad(RepositorioUsuario repositorio, PasswordEncoder codificador, JwtUtil jwtUtil,
                             ServicioAuditoria auditoria, ServicioArchivos archivos) {
        this.repositorio = repositorio;
        this.codificador = codificador;
        this.jwtUtil = jwtUtil;
        this.auditoria = auditoria;
        this.archivos = archivos;
        this.hashFalso = codificador.encode("clave-inexistente-tutofast");
    }

    public RegistroRespuestaDTO registrarUsuario(RegistroDTO dto) {
        if (!Boolean.TRUE.equals(dto.aceptaTratamientoDatos())) {
            throw new ExcepcionNegocio(MENSAJE_CONSENTIMIENTO);
        }
        if (dto.rol() == Rol.ADMINISTRADOR) {
            throw new ExcepcionNegocio("Solo es posible registrarse como ESTUDIANTE o TUTOR");
        }
        String correo = normalizarCorreo(dto.correo());
        if (repositorio.existsByCorreo(correo)) {
            throw duplicado("Ya existe una cuenta registrada con ese correo");
        }
        if (repositorio.existsByDocumento(dto.documento())) {
            throw duplicado("Ya existe una cuenta registrada con ese documento");
        }

        Instant ahora = Instant.now();
        Usuario usuario = new Usuario();
        usuario.setCorreo(correo);
        usuario.setNombre(dto.nombre().trim());
        usuario.setDocumento(dto.documento());
        usuario.setClaveHash(codificador.encode(dto.clave()));
        usuario.setRoles(new LinkedHashSet<>(java.util.List.of(dto.rol())));
        usuario.setEstadoCuenta(EstadoCuenta.ACTIVA);
        usuario.setFechaAutorizacionDatos(ahora);
        usuario.setFechaRegistro(ahora);
        if (dto.rol() == Rol.ESTUDIANTE) {
            PerfilEstudiante perfil = new PerfilEstudiante();
            perfil.setProgramaId(dto.programaId());
            perfil.setSemestre(dto.semestre());
            usuario.setPerfilEstudiante(perfil);
        } else {
            usuario.setPerfilTutor(new PerfilTutor());
        }
        Usuario guardado = repositorio.save(usuario);
        auditoria.registrarAccion(guardado.getId(), "REGISTRO_USUARIO", ENTIDAD_USUARIO,
                detalle("rol", dto.rol().name(), "correo", correo));
        auditoria.registrarAccion(guardado.getId(), "CONSENTIMIENTO_DATOS", ENTIDAD_USUARIO,
                detalle("norma", "Ley 1581 de 2012", "fecha", ahora.toString()));
        return new RegistroRespuestaDTO(MENSAJE_REGISTRO, guardado.getId());
    }

    public SesionDTO iniciarSesion(InicioSesionDTO dto) {
        Usuario usuario = repositorio.findByCorreo(normalizarCorreo(dto.correo())).orElse(null);
        boolean claveCorrecta = codificador.matches(dto.clave(), usuario != null ? usuario.getClaveHash() : hashFalso);
        if (usuario == null || !claveCorrecta) {
            if (usuario != null) {
                auditoria.registrarAccion(usuario.getId(), "INICIO_SESION_FALLIDO", ENTIDAD_USUARIO, null);
            }
            throw new ExcepcionNoAutenticado(MENSAJE_CREDENCIALES);
        }
        exigirCuentaActiva(usuario);
        JwtUtil.TokenEmitido emitido = jwtUtil.generar(usuario.getId(), usuario.getCorreo());
        auditoria.registrarAccion(usuario.getId(), "INICIO_SESION", ENTIDAD_USUARIO, null);
        return new SesionDTO(emitido.token(), emitido.expiraEn(), UsuarioMapper.aUsuarioDTO(usuario));
    }

    /** Usado por el filtro JWT: valida el token y carga el usuario vigente desde la base de datos. */
    public UsuarioAutenticado autenticarToken(String token) {
        String id = jwtUtil.extraerUsuarioId(token);
        Usuario usuario = repositorio.findById(id)
                .orElseThrow(() -> new ExcepcionNoAutenticado("Token inválido o expirado"));
        exigirCuentaActiva(usuario);
        return new UsuarioAutenticado(usuario.getId(), usuario.getCorreo(), usuario.getNombre(),
                new LinkedHashSet<>(usuario.getRoles()));
    }

    public UsuarioDTO obtenerUsuario(String usuarioId) {
        return UsuarioMapper.aUsuarioDTO(buscar(usuarioId));
    }

    public PerfilCompletoDTO obtenerPerfil(String usuarioId) {
        return UsuarioMapper.aPerfilCompletoDTO(buscar(usuarioId));
    }

    public PerfilCompletoDTO actualizarPerfilEstudiante(String usuarioId, PerfilEstudianteDTO dto) {
        Usuario usuario = buscar(usuarioId);
        exigirRol(usuario, Rol.ESTUDIANTE);
        PerfilEstudiante perfil = usuario.getPerfilEstudiante() != null
                ? usuario.getPerfilEstudiante() : new PerfilEstudiante();
        perfil.setProgramaId(dto.programaId());
        perfil.setSemestre(dto.semestre());
        usuario.setPerfilEstudiante(perfil);
        Usuario guardado = repositorio.save(usuario);
        auditoria.registrarAccion(usuarioId, "ACTUALIZAR_PERFIL_ESTUDIANTE", ENTIDAD_USUARIO, null);
        return UsuarioMapper.aPerfilCompletoDTO(guardado);
    }

    public PerfilCompletoDTO actualizarPerfilTutor(String usuarioId, PerfilTutorDTO dto) {
        Usuario usuario = buscar(usuarioId);
        exigirRol(usuario, Rol.TUTOR);
        PerfilTutor perfil = usuario.getPerfilTutor() != null ? usuario.getPerfilTutor() : new PerfilTutor();
        UsuarioMapper.aplicar(dto, perfil);
        usuario.setPerfilTutor(perfil);
        Usuario guardado = repositorio.save(usuario);
        auditoria.registrarAccion(usuarioId, "ACTUALIZAR_PERFIL_TUTOR", ENTIDAD_USUARIO, null);
        return UsuarioMapper.aPerfilCompletoDTO(guardado);
    }

    /**
     * Sube un soporte de verificación del tutor. No es transaccional a propósito: primero se valida y se guarda el
     * archivo en GridFS, luego se registra el soporte en el perfil; si ese guardado falla se elimina el archivo.
     * El nivel pasa a EN_REVISION salvo que el tutor ya esté VERIFICADO.
     */
    public SoporteDTO subirSoporte(String usuarioId, TipoSoporte tipo, MultipartFile archivo) {
        Usuario usuario = buscar(usuarioId);
        exigirRol(usuario, Rol.TUTOR);
        PerfilTutor perfil = usuario.getPerfilTutor() != null ? usuario.getPerfilTutor() : new PerfilTutor();
        long pendientes = perfil.getSoportes().stream()
                .filter(x -> x.getEstado() == com.tutofast.modelo.EstadoSoporte.EN_REVISION).count();
        if (pendientes >= MAX_SOPORTES_EN_REVISION) {
            throw new ExcepcionNegocio("ESTADO_INVALIDO", "Ya tiene " + MAX_SOPORTES_EN_REVISION
                    + " soportes en revisión; espere a que un administrador los revise", HttpStatus.CONFLICT);
        }

        ArchivoDTO guardado = archivos.guardar(archivo);
        SoporteVerificacion soporte = new SoporteVerificacion(tipo, guardado.archivoId(), guardado.nombre(),
                Instant.now());
        try {
            perfil.getSoportes().add(soporte);
            if (perfil.getNivelVerificacion() == NivelVerificacion.NO_VERIFICADO) {
                perfil.setNivelVerificacion(NivelVerificacion.EN_REVISION);
            }
            usuario.setPerfilTutor(perfil);
            repositorio.save(usuario);
        } catch (RuntimeException e) {
            archivos.eliminar(guardado.archivoId());
            throw e;
        }
        auditoria.registrarAccion(usuarioId, "SUBIR_SOPORTE", ENTIDAD_USUARIO,
                detalle("soporteId", soporte.getId(), "tipo", tipo.name()));
        return UsuarioMapper.aSoporteDTO(soporte);
    }

    public List<SoporteDTO> listarSoportes(String usuarioId) {
        Usuario usuario = buscar(usuarioId);
        exigirRol(usuario, Rol.TUTOR);
        if (usuario.getPerfilTutor() == null) {
            return List.of();
        }
        return usuario.getPerfilTutor().getSoportes().stream()
                .map(UsuarioMapper::aSoporteDTO).toList();
    }

    /** Añade el rol ESTUDIANTE o TUTOR a una cuenta existente (un usuario puede tener ambos). */
    public UsuarioDTO activarRol(String usuarioId, Rol rol) {
        if (rol == Rol.ADMINISTRADOR) {
            throw new ExcepcionAutorizacion("El rol ADMINISTRADOR no puede activarse desde la API");
        }
        Usuario usuario = buscar(usuarioId);
        if (usuario.tieneRol(rol)) {
            throw new ExcepcionNegocio("ESTADO_INVALIDO", "El usuario ya tiene el rol " + rol, HttpStatus.CONFLICT);
        }
        usuario.getRoles().add(rol);
        if (rol == Rol.TUTOR && usuario.getPerfilTutor() == null) {
            usuario.setPerfilTutor(new PerfilTutor());
        }
        if (rol == Rol.ESTUDIANTE && usuario.getPerfilEstudiante() == null) {
            usuario.setPerfilEstudiante(new PerfilEstudiante());
        }
        Usuario guardado = repositorio.save(usuario);
        auditoria.registrarAccion(usuarioId, "ACTIVAR_ROL", ENTIDAD_USUARIO, detalle("rol", rol.name()));
        return UsuarioMapper.aUsuarioDTO(guardado);
    }

    /** Seed: crea el administrador inicial si todavía no existe ninguno. Devuelve true si lo creó. */
    public boolean crearAdministradorSiNoExiste(String correo, String clave) {
        if (repositorio.existsByRolesContaining(Rol.ADMINISTRADOR)) {
            return false;
        }
        Instant ahora = Instant.now();
        Usuario admin = new Usuario();
        admin.setCorreo(normalizarCorreo(correo));
        admin.setNombre("Administrador TutoFast");
        admin.setDocumento("ADMIN-SEMILLA");
        admin.setClaveHash(codificador.encode(clave));
        admin.setRoles(new LinkedHashSet<>(java.util.List.of(Rol.ADMINISTRADOR)));
        admin.setFechaAutorizacionDatos(ahora);
        admin.setFechaRegistro(ahora);
        Usuario guardado = repositorio.save(admin);
        auditoria.registrarAccion(null, "SEED_ADMINISTRADOR", ENTIDAD_USUARIO, detalle("usuarioId", guardado.getId()));
        return true;
    }

    private Usuario buscar(String usuarioId) {
        return repositorio.findById(usuarioId)
                .orElseThrow(() -> new ExcepcionNoEncontrado("Usuario no encontrado"));
    }

    private static void exigirRol(Usuario usuario, Rol rol) {
        if (!usuario.tieneRol(rol)) {
            throw new ExcepcionAutorizacion("Necesita el rol " + rol + " para realizar esta acción");
        }
    }

    private static void exigirCuentaActiva(Usuario usuario) {
        if (!usuario.estaActiva()) {
            throw new ExcepcionAutorizacion("Su cuenta está " + usuario.getEstadoCuenta().name().toLowerCase(Locale.ROOT)
                    + " y no puede acceder a la plataforma");
        }
    }

    private static Map<String, Object> detalle(Object... paresClaveValor) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i + 1 < paresClaveValor.length; i += 2) {
            mapa.put(String.valueOf(paresClaveValor[i]), paresClaveValor[i + 1]);
        }
        return mapa;
    }

    private static String normalizarCorreo(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    private static ExcepcionNegocio duplicado(String mensaje) {
        return new ExcepcionNegocio("DUPLICADO", mensaje, HttpStatus.CONFLICT);
    }
}

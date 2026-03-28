package com.example.demostracion.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.demostracion.model.Adjunto;
import com.example.demostracion.model.Mensaje;
import com.example.demostracion.model.Usuario;
import com.example.demostracion.repository.AdjuntoRepository;
import com.example.demostracion.repository.MensajeRepository;
import com.example.demostracion.repository.UsuarioRepository;

@Service
public class MensajeService {

    private final MensajeRepository mensajeRepository;
    private final UsuarioRepository usuarioRepository;
    private final AdjuntoRepository adjuntoRepository;
    private final EmailService emailService;

    @Value("${mail.inbound.attachments-base:uploads/correos}")
    private String attachmentsBase;

    // ✅ CONSTANTES DE CARPETAS
    private static final String INBOX = "inbox";
    private static final String SENT = "sent";
    private static final String TRASH = "trash";

    public MensajeService(MensajeRepository mensajeRepository,
                          UsuarioRepository usuarioRepository,
                          AdjuntoRepository adjuntoRepository,
                          EmailService emailService) {
        this.mensajeRepository = mensajeRepository;
        this.usuarioRepository = usuarioRepository;
        this.adjuntoRepository = adjuntoRepository;
        this.emailService = emailService;
    }

    // =====================================================
    // 📩 ENVIAR MENSAJE
    // =====================================================
    public void enviarMensaje(Long remitenteId,
                              Long destinatarioId,
                              String asunto,
                              String contenido,
                              MultipartFile[] archivos) {

        Usuario remitente = usuarioRepository.findById(remitenteId).orElseThrow();
        Usuario destinatario = usuarioRepository.findById(destinatarioId).orElseThrow();

        LocalDateTime ahora = LocalDateTime.now();

        // copia remitente (ENVIADOS)
        Mensaje mensajeRemitente = new Mensaje();
        mensajeRemitente.setRemitente(remitente);
        mensajeRemitente.setDestinatario(destinatario);
        mensajeRemitente.setAsunto(asunto);
        mensajeRemitente.setContenido(contenido);
        mensajeRemitente.setFechaEnvio(ahora);
        mensajeRemitente.setLeido(true);
        mensajeRemitente.setCarpeta(SENT);
        mensajeRemitente.setEliminado(false);

        mensajeRemitente = mensajeRepository.save(mensajeRemitente);
        guardarAdjuntos(mensajeRemitente, archivos);

        // copia destinatario (INBOX)
        Mensaje mensajeDestinatario = new Mensaje();
        mensajeDestinatario.setRemitente(remitente);
        mensajeDestinatario.setDestinatario(destinatario);
        mensajeDestinatario.setAsunto(asunto);
        mensajeDestinatario.setContenido(contenido);
        mensajeDestinatario.setFechaEnvio(ahora);
        mensajeDestinatario.setLeido(false);
        mensajeDestinatario.setCarpeta(INBOX);
        mensajeDestinatario.setEliminado(false);

        mensajeDestinatario = mensajeRepository.save(mensajeDestinatario);
        guardarAdjuntos(mensajeDestinatario, archivos);
    }

    public void enviarAExterna(Long remitenteId,
                               String destinatarioExterno,
                               String asunto,
                               String contenido,
                               MultipartFile[] archivos) {

        Usuario remitente = usuarioRepository.findById(remitenteId).orElseThrow();

        Mensaje mensaje = new Mensaje();
        mensaje.setRemitente(remitente);
        mensaje.setDestinatario(null);
        mensaje.setDestinatarioExterno(destinatarioExterno);
        mensaje.setAsunto(asunto);
        mensaje.setContenido(contenido);
        mensaje.setFechaEnvio(LocalDateTime.now());
        mensaje.setLeido(true);
        mensaje.setCarpeta(SENT);
        mensaje.setEliminado(false);

        mensaje = mensajeRepository.save(mensaje);
        guardarAdjuntos(mensaje, archivos);

        // Enviar correo externo usando el servicio de correo
        emailService.sendEmail(destinatarioExterno, asunto, contenido, archivos);
    }

    // =====================================================
    // 📥 RECIBIDOS
    // =====================================================
    public List<Mensaje> recibir(Long usuarioId) {

        Usuario u = usuarioRepository.findById(usuarioId).orElseThrow();

        return mensajeRepository
                .findByDestinatarioAndCarpetaAndEliminadoFalseOrderByFechaEnvioDesc(u, INBOX);
    }

    // =====================================================
    // 📤 ENVIADOS
    // =====================================================
    public List<Mensaje> enviados(Long usuarioId) {

        Usuario u = usuarioRepository.findById(usuarioId).orElseThrow();

        return mensajeRepository
                .findByRemitenteAndCarpetaAndEliminadoFalseOrderByFechaEnvioDesc(u, SENT);
    }

    // =====================================================
    // 🔔 CONTAR NO LEÍDOS
    // =====================================================
    public long contarNoLeidos(Long usuarioId) {

        Usuario u = usuarioRepository.findById(usuarioId).orElseThrow();

        return mensajeRepository.countByDestinatarioAndLeidoFalseAndEliminadoFalse(u);
    }

    // =====================================================
    // 🗑️ MOVER CARPETA
    // =====================================================
    public void moverCarpeta(Long id, String carpeta) {

        Optional<Mensaje> optional = mensajeRepository.findById(id);

        if (optional.isPresent()) {

            Mensaje msg = optional.get();

            msg.setCarpeta(carpeta);

            if (TRASH.equalsIgnoreCase(carpeta)) {
                msg.setEliminado(true);
            } else {
                msg.setEliminado(false);
            }

            mensajeRepository.save(msg);
        }
    }

    // =====================================================
    // 🗑️ PAPELERA
    // =====================================================
    public List<Mensaje> papelera(Long usuarioId) {

        Usuario u = usuarioRepository.findById(usuarioId).orElseThrow();

        return mensajeRepository
                .findByDestinatarioAndEliminadoTrueOrderByFechaEnvioDesc(u)
                .stream()
                .filter(m -> !m.isEliminadoPermanente())
                .toList();
    }

    // =====================================================
    // 👁️ MARCAR COMO LEÍDO
    // =====================================================
    public void marcarLeido(Long mensajeId) {

        mensajeRepository.findById(mensajeId).ifPresent(m -> {
            m.setLeido(true);
            mensajeRepository.save(m);
        });
    }

    // =====================================================
    // 💬 RESPONDER MENSAJE
    // =====================================================
    public void responder(Long mensajeOriginalId,
                          Long remitenteId,
                          String contenido,
                          MultipartFile[] archivos) {

        Mensaje original = mensajeRepository.findById(mensajeOriginalId).orElseThrow();
        Usuario remitente = usuarioRepository.findById(remitenteId).orElseThrow();

        Mensaje respuesta = new Mensaje();

        respuesta.setRemitente(remitente);
        respuesta.setDestinatario(original.getRemitente());
        respuesta.setAsunto("Re: " + original.getAsunto());
        respuesta.setContenido(contenido);
        respuesta.setFechaEnvio(LocalDateTime.now());
        respuesta.setCarpeta(INBOX);
        respuesta.setLeido(false);
        respuesta.setIdPadre(original.getId());

        respuesta = mensajeRepository.save(respuesta);
        guardarAdjuntos(respuesta, archivos);
    }

    public void moverAPapelera(Long id) {
        moverCarpeta(id, TRASH);
    }

    public void restaurarDePapelera(Long id) {
        mensajeRepository.findById(id).ifPresent(m -> {
            m.setEliminado(false);
            m.setEliminadoPermanente(false);
            m.setCarpeta(INBOX);
            mensajeRepository.save(m);
        });
    }

    public int vaciarPapelera(Long usuarioId) {
        Usuario u = usuarioRepository.findById(usuarioId).orElseThrow();

        List<Mensaje> papelera = mensajeRepository
                .findByDestinatarioAndEliminadoTrueOrderByFechaEnvioDesc(u);

        int eliminados = 0;
        for (Mensaje m : papelera) {
            if (!m.isEliminadoPermanente()) {
                m.setEliminadoPermanente(true);
                mensajeRepository.save(m);
                eliminados++;
            }
        }
        return eliminados;
    }

    public void eliminarDefinitivo(Long id) {
        mensajeRepository.findById(id).ifPresent(m -> {
            m.setEliminadoPermanente(true);
            mensajeRepository.save(m);
        });
    }

    public void marcarEliminadoPermanente(Long id) {
        eliminarDefinitivo(id);
    }

    // =====================================================
    // 📌 CONSULTAS DE MENSAJES
    // =====================================================
    public List<Mensaje> listarPorCarpeta(String carpeta) {
        return mensajeRepository.findByCarpetaOrderByFechaEnvioDesc(carpeta);
    }

    public Mensaje verMensaje(Long id) {
        return mensajeRepository.findById(id).orElseThrow();
    }

    public void marcarComoLeido(Long mensajeId) {
        marcarLeido(mensajeId);
    }

    public List<Mensaje> getRespuestas(Long mensajeId) {
        return mensajeRepository.findByIdPadreOrderByFechaEnvioAsc(mensajeId);
    }

    // =====================================================
    // 📎 ADJUNTOS
    // =====================================================
    private void guardarAdjuntos(Mensaje mensaje, MultipartFile[] archivos) {
        if (archivos == null || archivos.length == 0) {
            return;
        }

        String basePath = attachmentsBase;
        if (basePath == null || basePath.isBlank()) {
            basePath = "uploads/correos";
        }

        File baseDir = new File(basePath, String.valueOf(mensaje.getId()));
        baseDir.mkdirs();

        for (MultipartFile archivo : archivos) {
            if (archivo == null || archivo.isEmpty()) {
                continue;
            }

            String nombre = archivo.getOriginalFilename();
            if (nombre == null) {
                continue;
            }

            File destino = new File(baseDir, nombre);
            try (FileOutputStream fos = new FileOutputStream(destino)) {
                fos.write(archivo.getBytes());
            } catch (IOException e) {
                // Ignorar errores de adjuntos para no bloquear el envío
                System.err.println("Error guardando adjunto: " + e.getMessage());
                continue;
            }

            Adjunto adjunto = new Adjunto();
            adjunto.setNombreArchivo(nombre);
            adjunto.setRutaArchivo(destino.getAbsolutePath());
            adjunto.setMensaje(mensaje);
            adjuntoRepository.save(adjunto);
        }
    }
}

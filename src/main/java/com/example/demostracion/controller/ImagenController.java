package com.example.demostracion.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import com.example.demostracion.model.Vehiculo;
import com.example.demostracion.repository.VehiculoRepository;

@RestController
public class ImagenController {

    private final VehiculoRepository vehiculoRepository;

    public ImagenController(VehiculoRepository vehiculoRepository) {
        this.vehiculoRepository = vehiculoRepository;
    }

    @GetMapping("/imagenes/{id}")
    public ResponseEntity<byte[]> obtenerImagen(@PathVariable Long id) {

        Vehiculo vehiculo = vehiculoRepository.findById(id).orElse(null);

        if (vehiculo == null || vehiculo.getImagen() == null) {
            return ResponseEntity.notFound().build();
        }

        byte[] data = vehiculo.getImagen();
        String tipo = "application/octet-stream";
        try (java.io.InputStream is = new java.io.ByteArrayInputStream(data)) {
            String guessed = java.net.URLConnection.guessContentTypeFromStream(is);
            if (guessed != null) {
                tipo = guessed;
            }
        } catch (IOException e) {
            // ignore, usaremos el default
        }

        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(tipo))
                .body(data);
    }
}
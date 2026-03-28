package com.example.demostracion.controller;

import java.io.IOException;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.example.demostracion.model.Vehiculo;
import com.example.demostracion.repository.VehiculoRepository;

@Controller
@RequestMapping("/vehiculos")
public class VehiculoController {

    private final VehiculoRepository vehiculoRepository;

    public VehiculoController(VehiculoRepository vehiculoRepository) {
        this.vehiculoRepository = vehiculoRepository;
    }

    @GetMapping
    public String listarVehiculos(Model model) {
        model.addAttribute("vehiculos",
                vehiculoRepository.findByActivoTrue());
        return "vehiculos/index"; // ✅ CORREGIDO
    }

    // soporta ambos caminos porque los templates usaban "/vehiculos/crear" pero el método original
    // estaba en "/nuevo"; dejamos los dos para evitar 404 si alguno cambia.
    @GetMapping({"/nuevo","/crear"})
    public String nuevoVehiculo(Model model) {
        model.addAttribute("vehiculo", new Vehiculo());
        return "vehiculos/crear"; // renderiza crear.html
    }

    @PostMapping("/guardar")
    public String guardarVehiculo(
            @ModelAttribute Vehiculo vehiculo,
            @RequestParam("imagenFile") MultipartFile imagenFile) {

        try {
            if (!imagenFile.isEmpty()) {
                vehiculo.setImagen(imagenFile.getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        vehiculoRepository.save(vehiculo);

        return "redirect:/vehiculos"; // ✅ CORRECTO
    }

    @GetMapping("/editar/{id}")
    public String editarVehiculo(@PathVariable Long id, Model model) {
        Vehiculo vehiculo = vehiculoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Vehículo no encontrado"));
        model.addAttribute("vehiculo", vehiculo);
        return "vehiculos/form"; // para editar
    }

    @GetMapping("/carga-masiva")
    public String cargaMasivaVehiculos() {
        return "vehiculos/carga-masiva";
    }

    @GetMapping("/imagen/{id}")
    public ResponseEntity<byte[]> getImagenVehiculo(@PathVariable Long id) {
        Vehiculo vehiculo = vehiculoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Vehículo no encontrado"));
        if (vehiculo.getImagen() != null) {
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG) // or IMAGE_PNG depending on your images
                    .body(vehiculo.getImagen());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/actualizar/{id}")
    public String actualizarVehiculo(@PathVariable Long id, @ModelAttribute Vehiculo vehiculo, @RequestParam("imagenFile") MultipartFile imagenFile) {
        vehiculo.setIdVehiculo(id);
        try {
            if (!imagenFile.isEmpty()) {
                vehiculo.setImagen(imagenFile.getBytes());
            } else {
                // Keep existing image if no new file uploaded
                Vehiculo existing = vehiculoRepository.findById(id).orElse(null);
                if (existing != null) {
                    vehiculo.setImagen(existing.getImagen());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        vehiculoRepository.save(vehiculo);
        return "redirect:/vehiculos";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminarVehiculo(@PathVariable Long id) {
        Vehiculo vehiculo = vehiculoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Vehículo no encontrado"));
        vehiculo.setActivo(false); // Soft delete
        vehiculoRepository.save(vehiculo);
        return "redirect:/vehiculos";
    }
}
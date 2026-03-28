package com.example.demostracion.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.demostracion.repository.VehiculoRepository;

@Controller
public class HomeController {

    private final VehiculoRepository vehiculoRepository;

    public HomeController(VehiculoRepository vehiculoRepository) {
        this.vehiculoRepository = vehiculoRepository;
    }

    @GetMapping("/")
    public String index(Model model) {
        // Cargar todos los vehículos activos para mostrar en el catálogo
        model.addAttribute("vehiculos", vehiculoRepository.findByActivoTrue());
        return "index";
    }
}

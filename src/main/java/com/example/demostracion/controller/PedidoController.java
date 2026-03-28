package com.example.demostracion.controller;

import com.example.demostracion.model.Pedido;
import com.example.demostracion.repository.PedidoRepository;
import com.example.demostracion.repository.ConductorRepository;
import com.example.demostracion.repository.VehiculoRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/pedidos")
public class PedidoController {

    private final PedidoRepository pedidoRepo;
    private final ConductorRepository conductorRepo;
    private final VehiculoRepository vehiculoRepo;

    public PedidoController(PedidoRepository pedidoRepo, ConductorRepository conductorRepo, VehiculoRepository vehiculoRepo) {
        this.pedidoRepo = pedidoRepo;
        this.conductorRepo = conductorRepo;
        this.vehiculoRepo = vehiculoRepo;
    }

    @GetMapping
    public String listarPedidos(Model model) {
        model.addAttribute("pedidos", pedidoRepo.findAll());
        return "pedidos/listar"; // 👈 thymeleaf
    }

    @GetMapping("/crear")
    public String mostrarForm(Model model) {
        model.addAttribute("pedido", new Pedido());
        model.addAttribute("conductores", conductorRepo.findAll());
        model.addAttribute("vehiculos", vehiculoRepo.findAll());
        return "pedidos/form";
    }

    @PostMapping("/guardar")
    public String guardarPedido(@ModelAttribute Pedido pedido) {
        pedidoRepo.save(pedido);
        return "redirect:/pedidos";
    }

    @GetMapping("/cambiarEstado/{id}")
    public String cambiarEstado(@PathVariable Long id, @RequestParam String estado) {
        Pedido pedido = pedidoRepo.findById(id).orElseThrow();
        pedido.setEstado(estado);
        pedidoRepo.save(pedido);
        return "redirect:/pedidos";
    }
}

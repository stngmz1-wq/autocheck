package com.example.demostracion.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;     
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.demostracion.dto.ResultadoCargaInventarioDTO;
import com.example.demostracion.model.Conductor;
import com.example.demostracion.model.Inventario;
import com.example.demostracion.model.Notificacion;
import com.example.demostracion.model.Novedad;
import com.example.demostracion.model.Pedido;
import com.example.demostracion.model.Usuario;
import com.example.demostracion.model.Vehiculo;
import com.example.demostracion.model.Cliente;
import com.example.demostracion.model.SolicitudPrueba;
import com.example.demostracion.repository.ConductorRepository;
import com.example.demostracion.repository.InventarioRepository;
import com.example.demostracion.repository.NotificacionRepository;
import com.example.demostracion.repository.NovedadRepository;
import com.example.demostracion.repository.PedidoRepository;
import com.example.demostracion.repository.UsuarioRepository;
import com.example.demostracion.repository.VehiculoRepository;
import com.example.demostracion.repository.ClienteRepository;
import com.example.demostracion.repository.SolicitudPruebaRepository;
import com.example.demostracion.service.CargaMasivaInventarioService;
import com.example.demostracion.service.MensajeService;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

@Controller
@RequestMapping("/gerente")
public class GerenteController {

    private static final Set<String> ESTADOS_INTERES = Set.of("pendiente", "contactado", "negociando");
    private static final Set<String> ESTADOS_VENTA = Set.of("vendido", "entregado");
    private static final Locale LOCALE_ES = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FORMATO_FECHA_REPORTE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", LOCALE_ES);

    private final VehiculoRepository vehiculoRepository;
    private final InventarioRepository inventarioRepository;
    private final NotificacionRepository notificacionRepository;
    private final ConductorRepository conductorRepository;
    private final NovedadRepository novedadRepository;
    private final UsuarioRepository usuarioRepository;
    private final PedidoRepository pedidoRepository;
    private final CargaMasivaInventarioService cargaMasivaInventarioService;
    private final MensajeService mensajeService;
    private final ClienteRepository clienteRepository;
    private final SolicitudPruebaRepository solicitudPruebaRepository;

    private static final String UPLOAD_DIR = "uploads/novedades/";

    public GerenteController(VehiculoRepository vehiculoRepository,
                             InventarioRepository inventarioRepository,
                             NotificacionRepository notificacionRepository,
                             ConductorRepository conductorRepository,
                             NovedadRepository novedadRepository,
                             UsuarioRepository usuarioRepository,
                             PedidoRepository pedidoRepository,
                             CargaMasivaInventarioService cargaMasivaInventarioService,
                             MensajeService mensajeService,
                             ClienteRepository clienteRepository,
                             SolicitudPruebaRepository solicitudPruebaRepository) {
        this.vehiculoRepository = vehiculoRepository;
        this.inventarioRepository = inventarioRepository;
        this.notificacionRepository = notificacionRepository;
        this.conductorRepository = conductorRepository;
        this.novedadRepository = novedadRepository;
        this.usuarioRepository = usuarioRepository;
        this.pedidoRepository = pedidoRepository;
        this.cargaMasivaInventarioService = cargaMasivaInventarioService;
        this.mensajeService = mensajeService;
        this.clienteRepository = clienteRepository;
        this.solicitudPruebaRepository = solicitudPruebaRepository;
    }

    // ===============================
    // Dashboard ejecutivo
    // ===============================
    @GetMapping
    public String gerenteHome(Model model, Authentication auth) {
        agregarDatosCorreo(model, auth);

        List<Inventario> inventarios = inventarioRepository.findAll();
        List<Pedido> pedidos = pedidoRepository.findAll();

        long vehiculosActivos = inventarios.stream().filter(Inventario::isActivo).count();
        long clientesInteresados = pedidos.stream()
                .filter(p -> ESTADOS_INTERES.contains(normalizarEstado(p.getEstado())))
                .count();

        YearMonth mesActual = YearMonth.now();

        long ventasTotales = pedidos.stream()
            .filter(p -> ESTADOS_VENTA.contains(normalizarEstado(p.getEstado())))
            .count();

        double ingresosMes = pedidos.stream()
            .filter(p -> p.getFechaCreacion() != null)
            .filter(p -> YearMonth.from(p.getFechaCreacion()).equals(mesActual))
            .mapToDouble(this::calcularIngresoPedido)
            .sum();

        double ingresosTotales = pedidos.stream()
            .mapToDouble(this::calcularIngresoPedido)
            .sum();

        double ticketPromedio = ventasTotales == 0 ? 0.0 : ingresosTotales / ventasTotales;
        List<Map<String, Object>> resumenMensual = construirResumenMensual(pedidos, 6);

        model.addAttribute("vehiculosActivos", vehiculosActivos);
        model.addAttribute("clientesInteresados", clientesInteresados);
        model.addAttribute("ventasTotales", ventasTotales);
        model.addAttribute("ingresosMes", formatearMoneda(ingresosMes));
        model.addAttribute("ingresosTotales", formatearMoneda(ingresosTotales));
        model.addAttribute("ticketPromedio", formatearMoneda(ticketPromedio));
        model.addAttribute("resumenMensual", resumenMensual);
        model.addAttribute("mesesLabels", resumenMensual.stream().map(m -> (String) m.get("mes")).toList());
        model.addAttribute("ventasMensuales", resumenMensual.stream().map(m -> ((Number) m.get("ventas")).longValue()).toList());
        model.addAttribute("ingresosMensuales", resumenMensual.stream().map(m -> ((Number) m.get("ingresos")).doubleValue()).toList());
        model.addAttribute("actualizadoEn", FORMATO_FECHA_REPORTE.format(LocalDateTime.now()));
        model.addAttribute("vehiculosMasSolicitados", topVehiculosSolicitados(pedidos, 5));
        model.addAttribute("rendimientoVendedores", construirRendimientoVendedores(pedidos));

        // Estadísticas adicionales para el dashboard
        model.addAttribute("totalInventario", inventarioRepository.count());
        model.addAttribute("totalVehiculos", vehiculoRepository.count());
        model.addAttribute("novedadesPendientes",
                novedadRepository.findAll().stream()
                .filter(this::esNovedadAbierta)
                        .count());
        model.addAttribute("pedidosTotales", pedidos.size());

        return "gerente/gerente";
    }

    // ===============================
    // Vehiculos
    // ===============================
    @GetMapping("/vehiculos")
    public String listarVehiculos(Model model, Authentication auth) {
        agregarDatosCorreo(model, auth);
        model.addAttribute("vehiculos", vehiculoRepository.findAll());
        return "gerente/vehiculos/listar";
    }

    @GetMapping("/vehiculos/crear")
    public String crearVehiculoForm(Model model, Authentication auth) {
        agregarDatosCorreo(model, auth);
        model.addAttribute("vehiculo", new Vehiculo());
        model.addAttribute("inventarios", inventarioRepository.findAll());
        return "gerente/vehiculos/form";
    }

    @PostMapping("/vehiculos/crear")
    public String guardarVehiculo(@ModelAttribute Vehiculo vehiculo,
                                  @RequestParam(value = "imagenFile", required = false) MultipartFile imagenFile) throws IOException {
        if (vehiculo.getChasis() == null || vehiculo.getChasis().isBlank()) {
            vehiculo.setChasis("AUTO-" + System.currentTimeMillis());
        }
        vehiculo.setActivo(true);
        if (imagenFile != null && !imagenFile.isEmpty()) {
            vehiculo.setImagen(imagenFile.getBytes());
        }
        vehiculoRepository.save(vehiculo);
        return "redirect:/gerente/vehiculos";
    }

    @GetMapping("/vehiculos/editar/{id}")
    public String editarVehiculo(@PathVariable Long id, Model model, Authentication auth) {
        agregarDatosCorreo(model, auth);
        Vehiculo vehiculo = vehiculoRepository.findById(id).orElseThrow();
        model.addAttribute("vehiculo", vehiculo);
        model.addAttribute("inventarios", inventarioRepository.findAll());
        return "gerente/vehiculos/form";
    }

    @PostMapping("/vehiculos/editar/{id}")
    public String actualizarVehiculo(@PathVariable Long id,
                                     @ModelAttribute Vehiculo vehiculo,
                                     @RequestParam(value = "imagenFile", required = false) MultipartFile imagenFile) throws IOException {
        Vehiculo existente = vehiculoRepository.findById(id).orElseThrow();

        existente.setMarca(vehiculo.getMarca());
        existente.setModelo(vehiculo.getModelo());
        existente.setAnio(vehiculo.getAnio());
        existente.setPrecio(vehiculo.getPrecio());
        existente.setCilindrada(vehiculo.getCilindrada());
        existente.setTipoCombustible(vehiculo.getTipoCombustible());
        existente.setTransmision(vehiculo.getTransmision());
        existente.setDescripcion(vehiculo.getDescripcion());
        existente.setChasis(vehiculo.getChasis());

        if (imagenFile != null && !imagenFile.isEmpty()) {
            existente.setImagen(imagenFile.getBytes());
        }

        vehiculoRepository.save(existente);
        return "redirect:/gerente/vehiculos";
    }

    @PostMapping("/vehiculos/eliminar/{id}")
    public String eliminarVehiculo(@PathVariable Long id) {
        vehiculoRepository.deleteById(id);
        return "redirect:/gerente/vehiculos";
    }

    @GetMapping("/vehiculos/imagen/{id}")
    public ResponseEntity<byte[]> obtenerImagenVehiculoGerente(@PathVariable Long id) {
        Vehiculo vehiculo = vehiculoRepository.findById(id).orElse(null);
        if (vehiculo == null || vehiculo.getImagen() == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(vehiculo.getImagen());
    }

    // ===============================
    // Inventario
    // ===============================
    @GetMapping("/inventario")
    public String listarInventario(@RequestParam(required = false, defaultValue = "todos") String estado,
                                   Model model,
                                   Authentication auth) {
        agregarDatosCorreo(model, auth);

        String filtroEstado = estado == null ? "todos" : estado.trim().toLowerCase(Locale.ROOT);
        List<Inventario> inventarios;
        switch (filtroEstado) {
            case "activos" -> inventarios = inventarioRepository.findByActivoTrue();
            case "inactivos" -> inventarios = inventarioRepository.findByActivoFalse();
            default -> {
                filtroEstado = "todos";
                inventarios = inventarioRepository.findAll();
            }
        }

        model.addAttribute("inventarios", inventarios);
        model.addAttribute("filtroEstado", filtroEstado);
        agregarResumenInventario(model, inventarioRepository.findAll());
        return "gerente/inventario/listar";
    }

    @GetMapping("/inventario/inactivos")
    public String listarInventarioInactivo() {
        return "redirect:/gerente/inventario?estado=inactivos";
    }

    @GetMapping("/inventario/crear")
    public String crearInventarioForm(Model model, Authentication auth) {
        agregarDatosCorreo(model, auth);
        model.addAttribute("esAdmin", false);
        model.addAttribute("inventario", new Inventario());
        return "gerente/inventario/form";
    }

    @PostMapping("/inventario/crear")
    public String guardarInventario(@ModelAttribute Inventario inventario) {
        inventario.setActivo(true);
        inventarioRepository.save(inventario);
        sincronizarVehiculoConInventario(inventario);
        return "redirect:/gerente/inventario";
    }

    @GetMapping("/inventario/editar/{id}")
    public String editarInventario(@PathVariable Long id, Model model, Authentication auth) {
        agregarDatosCorreo(model, auth);
        model.addAttribute("esAdmin", false);
        Inventario inventario = inventarioRepository.findById(id).orElseThrow();
        model.addAttribute("inventario", inventario);
        return "gerente/inventario/form";
    }

    @PostMapping("/inventario/editar/{id}")
    public String actualizarInventario(@PathVariable Long id, @ModelAttribute Inventario inventario) {
        inventario.setIdInventario(id);
        inventarioRepository.save(inventario);
        sincronizarVehiculoConInventario(inventario);
        return "redirect:/gerente/inventario";
    }

    @PostMapping("/inventario/cambiar-estado/{id}")
    public String cambiarEstadoInventario(@PathVariable Long id,
                                          @RequestParam(required = false, defaultValue = "todos") String estado) {
        Inventario inventario = inventarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Inventario no encontrado"));
        inventario.setActivo(!inventario.isActivo());
        inventarioRepository.save(inventario);

        vehiculoRepository.findByChasis(inventario.getChasis()).ifPresent(vehiculo -> {
            vehiculo.setActivo(inventario.isActivo());
            vehiculoRepository.save(vehiculo);
        });

        return "redirect:/gerente/inventario?estado=" + estado;
    }

    @GetMapping("/inventario/carga-masiva")
    public String mostrarCargaMasivaGerente(Model model, Authentication auth) {
        agregarDatosCorreo(model, auth);
        return "gerente/inventario/carga-masiva";
    }

    @PostMapping("/inventario/carga-masiva")
    public String procesarCargaMasivaGerente(@RequestParam("archivo") MultipartFile archivo,
                                             RedirectAttributes redirectAttributes) {
        try {
            if (archivo.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Por favor seleccione un archivo");
                return "redirect:/gerente/inventario/carga-masiva";
            }

            ResultadoCargaInventarioDTO resultado = cargaMasivaInventarioService.procesarArchivo(archivo);
            redirectAttributes.addFlashAttribute("resultado", resultado);
            redirectAttributes.addFlashAttribute(resultado.isExitoso() ? "success" : "warning", resultado.getMensaje());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al procesar archivo: " + e.getMessage());
        }

        return "redirect:/gerente/inventario/carga-masiva";
    }

    // ===============================
    // Clientes interesados (CRM)
    // ===============================
    @GetMapping("/clientes/interesados")
    public String listarInteresados(Model model, Authentication auth) {
        agregarDatosCorreo(model, auth);

        List<Pedido> pedidos = pedidoRepository.findAll().stream()
                .sorted(Comparator.comparing(Pedido::getFechaCreacion, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        model.addAttribute("interesados", pedidos);
        model.addAttribute("resumenEstados", resumenEstados(pedidos));
        model.addAttribute("vehiculosMasSolicitados", topVehiculosSolicitados(pedidos, 10));
        model.addAttribute("rendimientoVendedores", construirRendimientoVendedores(pedidos));

        return "gerente/clientes/interesados";
    }

    @PostMapping("/clientes/interesados/{id}/estado")
    public String cambiarEstadoInteresado(@PathVariable Long id, @RequestParam String estado) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Interesado no encontrado"));

        String estadoNormalizado = normalizarEstado(estado);
        if (!Set.of("pendiente", "contactado", "negociando", "vendido", "perdido", "entregado").contains(estadoNormalizado)) {
            throw new IllegalArgumentException("Estado no permitido: " + estado);
        }

        pedido.setEstado(estadoNormalizado);
        pedidoRepository.save(pedido);

        return "redirect:/gerente/clientes/interesados";
    }

    // ===============================
    // Reportes gerenciales
    // ===============================
    @GetMapping("/reportes/pdf")
    public ResponseEntity<byte[]> descargarReporteGerencialPdf() throws IOException {
        List<Inventario> inventarios = inventarioRepository.findAll();
        List<Pedido> pedidos = pedidoRepository.findAll();

        long vehiculosDisponibles = inventarios.stream().filter(Inventario::isActivo).count();
        long interesadosRegistrados = pedidos.stream().filter(p -> ESTADOS_INTERES.contains(normalizarEstado(p.getEstado()))).count();
        long ventasRealizadas = pedidos.stream().filter(p -> ESTADOS_VENTA.contains(normalizarEstado(p.getEstado()))).count();
        double ingresosTotales = pedidos.stream().mapToDouble(this::calcularIngresoPedido).sum();
        List<Map<String, Object>> resumenMensual = construirResumenMensual(pedidos, 12);

        String modeloMasSolicitado = topVehiculosSolicitados(pedidos, 1).stream()
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse("Sin datos");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        DeviceRgb colorMarca = new DeviceRgb(10, 29, 55);
        DeviceRgb colorAcento = new DeviceRgb(245, 166, 35);

        document.add(new Paragraph("REPORTE EJECUTIVO DE OPERACION")
            .setBold()
            .setFontSize(18)
            .setFontColor(colorMarca)
            .setTextAlignment(TextAlignment.CENTER));

        document.add(new Paragraph("Generado: " + FORMATO_FECHA_REPORTE.format(LocalDateTime.now()))
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(ColorConstants.DARK_GRAY)
            .setMarginBottom(15));

        Table kpis = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1})).useAllAvailableWidth();
        kpis.addCell(celdaKpi("Vehiculos disponibles", String.valueOf(vehiculosDisponibles), colorAcento));
        kpis.addCell(celdaKpi("Interesados", String.valueOf(interesadosRegistrados), colorAcento));
        kpis.addCell(celdaKpi("Ventas cerradas", String.valueOf(ventasRealizadas), colorAcento));
        kpis.addCell(celdaKpi("Ingresos", formatearMoneda(ingresosTotales), colorAcento));
        document.add(kpis);

        document.add(new Paragraph("\nModelo mas solicitado: " + modeloMasSolicitado)
            .setBold()
            .setFontColor(colorMarca));

        document.add(new Paragraph("\nResumen mensual de ventas e ingresos")
            .setBold()
            .setFontSize(13)
            .setFontColor(colorMarca));

        Table tablaMensual = new Table(UnitValue.createPercentArray(new float[]{2, 1, 1, 1})).useAllAvailableWidth();
        tablaMensual.addHeaderCell(celdaHeader("Mes", colorMarca));
        tablaMensual.addHeaderCell(celdaHeader("Interesados", colorMarca));
        tablaMensual.addHeaderCell(celdaHeader("Ventas", colorMarca));
        tablaMensual.addHeaderCell(celdaHeader("Ingresos", colorMarca));

        for (Map<String, Object> fila : resumenMensual) {
            tablaMensual.addCell(celdaDato((String) fila.get("mes")));
            tablaMensual.addCell(celdaDato(String.valueOf(((Number) fila.get("interesados")).longValue())));
            tablaMensual.addCell(celdaDato(String.valueOf(((Number) fila.get("ventas")).longValue())));
            tablaMensual.addCell(celdaDato(formatearMoneda(((Number) fila.get("ingresos")).doubleValue())));
        }
        document.add(tablaMensual);

        byte[] chart = generarGraficoVentasIngresos(resumenMensual);
        if (chart.length > 0) {
            Image grafico = new Image(ImageDataFactory.create(chart));
            grafico.setAutoScale(true);
            grafico.setMarginTop(15);
            document.add(grafico);
        }

        document.close();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=informe_gerencial.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(baos.toByteArray());
    }

    @GetMapping("/reportes/excel")
    public ResponseEntity<byte[]> descargarReporteGerencialExcel() throws IOException {
        List<Pedido> pedidos = pedidoRepository.findAll().stream()
                .sorted(Comparator.comparing(Pedido::getFechaCreacion, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<Map<String, Object>> resumenMensual = construirResumenMensual(pedidos, 12);

        Workbook workbook = new XSSFWorkbook();
        CellStyle headerStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        CellStyle moneyStyle = workbook.createCellStyle();
        moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("$#,##0.00"));

        Sheet detalle = workbook.createSheet("Detalle Ventas");
        Row headerDetalle = detalle.createRow(0);
        String[] columnasDetalle = {"Cliente", "Vehiculo", "Estado", "Fecha", "Vendedor", "Ingreso"};
        for (int i = 0; i < columnasDetalle.length; i++) {
            headerDetalle.createCell(i).setCellValue(columnasDetalle[i]);
            headerDetalle.getCell(i).setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (Pedido pedido : pedidos) {
            Row row = detalle.createRow(rowNum++);
            row.createCell(0).setCellValue(extraerCliente(pedido));
            row.createCell(1).setCellValue(extraerVehiculo(pedido));
            row.createCell(2).setCellValue(pedido.getEstado() != null ? pedido.getEstado().toUpperCase(Locale.ROOT) : "PENDIENTE");
            row.createCell(3).setCellValue(pedido.getFechaCreacion() != null ? pedido.getFechaCreacion().toString() : "");
            row.createCell(4).setCellValue(pedido.getConductor() != null ? pedido.getConductor().getNombre() : "Sin asignar");
            var ingresoCell = row.createCell(5);
            ingresoCell.setCellValue(calcularIngresoPedido(pedido));
            ingresoCell.setCellStyle(moneyStyle);
        }

        for (int i = 0; i < columnasDetalle.length; i++) {
            detalle.autoSizeColumn(i);
        }

        Sheet resumen = workbook.createSheet("Resumen Mensual");
        Row headerResumen = resumen.createRow(0);
        String[] columnasResumen = {"Mes", "Interesados", "Ventas", "Ingresos"};
        for (int i = 0; i < columnasResumen.length; i++) {
            headerResumen.createCell(i).setCellValue(columnasResumen[i]);
            headerResumen.getCell(i).setCellStyle(headerStyle);
        }

        int resumenRow = 1;
        for (Map<String, Object> fila : resumenMensual) {
            Row row = resumen.createRow(resumenRow++);
            row.createCell(0).setCellValue((String) fila.get("mes"));
            row.createCell(1).setCellValue(((Number) fila.get("interesados")).longValue());
            row.createCell(2).setCellValue(((Number) fila.get("ventas")).longValue());
            var ingresoCell = row.createCell(3);
            ingresoCell.setCellValue(((Number) fila.get("ingresos")).doubleValue());
            ingresoCell.setCellStyle(moneyStyle);
        }

        for (int i = 0; i < columnasResumen.length; i++) {
            resumen.autoSizeColumn(i);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reporte_interesados.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(baos.toByteArray());
    }

    // ===============================
    // Vendedores
    // ===============================
    @GetMapping("/vendedores")
    public String gestionVendedores(Model model, Authentication auth) {
        agregarDatosCorreo(model, auth);
        model.addAttribute("rendimientoVendedores", construirRendimientoVendedores(pedidoRepository.findAll()));
        model.addAttribute("vendedores", conductorRepository.findAll());
        return "gerente/clientes/interesados";
    }

    // ===============================
    // Notificaciones
    // ===============================
    @GetMapping("/notificaciones")
    public String listarNotificaciones(
            @RequestParam(required = false) Boolean leida,
            @RequestParam(required = false) Long conductorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Model model,
            Authentication auth) {

        agregarDatosCorreo(model, auth);

        List<Notificacion> notificaciones = new ArrayList<>(notificacionRepository.findAll());

        if (leida != null) {
            notificaciones = notificaciones.stream()
                    .filter(n -> n.isLeida() == leida)
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        if (conductorId != null) {
            notificaciones = notificaciones.stream()
                    .filter(n -> n.getConductor() != null && n.getConductor().getIdConductor().equals(conductorId))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        if (desde != null) {
            notificaciones = notificaciones.stream()
                    .filter(n -> n.getFecha() != null && !n.getFecha().toLocalDate().isBefore(desde))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        if (hasta != null) {
            notificaciones = notificaciones.stream()
                    .filter(n -> n.getFecha() != null && !n.getFecha().toLocalDate().isAfter(hasta))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        List<Notificacion> alertasInteligentes = construirAlertasInteligentes();
        notificaciones.addAll(0, alertasInteligentes);

        model.addAttribute("notificaciones", notificaciones);
        model.addAttribute("conductores", conductorRepository.findAll());
        model.addAttribute("alertasInteligentes", alertasInteligentes);
        return "gerente/notificacion/lista";
    }

    @GetMapping("/notificaciones/nuevo")
    public String crearNotificacionForm(Model model, Authentication auth) {
        agregarDatosCorreo(model, auth);
        model.addAttribute("notificacion", new Notificacion());
        model.addAttribute("conductores", conductorRepository.findAll());
        return "gerente/notificacion/form";
    }

    @PostMapping("/notificaciones/guardar")
    public String guardarNotificacion(@ModelAttribute Notificacion notificacion) {
        if (notificacion.getConductor() != null && notificacion.getConductor().getIdConductor() != null) {
            Conductor conductor = conductorRepository.findById(notificacion.getConductor().getIdConductor())
                    .orElseThrow(() -> new IllegalArgumentException("Conductor no encontrado"));
            notificacion.setConductor(conductor);
        }
        notificacionRepository.save(notificacion);
        return "redirect:/gerente/notificaciones";
    }

    @GetMapping("/notificaciones/editar/{id}")
    public String editarNotificacion(@PathVariable Long id, Model model, Authentication auth) {
        agregarDatosCorreo(model, auth);
        Notificacion notificacion = notificacionRepository.findById(id).orElseThrow();
        model.addAttribute("notificacion", notificacion);
        model.addAttribute("conductores", conductorRepository.findAll());
        return "gerente/notificacion/form";
    }

    @PostMapping("/notificaciones/editar/{id}")
    public String actualizarNotificacion(@PathVariable Long id, @ModelAttribute Notificacion notificacion) {
        notificacion.setIdNotificacion(id);

        if (notificacion.getConductor() != null && notificacion.getConductor().getIdConductor() != null) {
            Conductor conductor = conductorRepository.findById(notificacion.getConductor().getIdConductor())
                    .orElseThrow(() -> new IllegalArgumentException("Conductor no encontrado"));
            notificacion.setConductor(conductor);
        }

        notificacionRepository.save(notificacion);
        return "redirect:/gerente/notificaciones";
    }

    @PostMapping("/notificaciones/eliminar/{id}")
    public String eliminarNotificacion(@PathVariable Long id) {
        notificacionRepository.deleteById(id);
        return "redirect:/gerente/notificaciones";
    }

    // ===============================
    // Novedades (CRUD)
    // ===============================
    @GetMapping("/novedades")
    public String listarNovedades(Model model, Authentication auth) {
        agregarDatosCorreo(model, auth);
        List<Novedad> novedades = novedadRepository.findAll().stream()
                .sorted(Comparator
                        .comparing((Novedad n) -> esNovedadAbierta(n) ? 0 : 1)
                        .thenComparing((Novedad n) -> pesoPrioridadNovedad(n.getPrioridad()), Comparator.reverseOrder())
                        .thenComparing(Novedad::getFechaReporte, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        model.addAttribute("novedades", novedades);
        model.addAttribute("novedadesAbiertas", novedades.stream().filter(this::esNovedadAbierta).count());
        model.addAttribute("novedadesEnRevision", novedades.stream()
                .filter(n -> "en_revision".equals(normalizarEstadoNovedad(n.getEstado())))
                .count());
        model.addAttribute("novedadesGarantia", novedades.stream()
                .filter(n -> Boolean.TRUE.equals(n.getAplicaGarantia()))
                .filter(this::esNovedadAbierta)
                .count());
        model.addAttribute("novedadesCriticas", novedades.stream().filter(this::esNovedadCritica).count());
        return "gerente/novedades/listar";
    }

    @GetMapping("/novedades/crear")
    public String crearNovedadForm(Model model, Authentication auth) {
        agregarDatosCorreo(model, auth);
        Novedad novedad = new Novedad();
        novedad.setEstado("pendiente");
        novedad.setPrioridad("media");
        novedad.setOrigenReporte("recepcion_tienda");
        novedad.setAplicaGarantia(Boolean.FALSE);
        prepararFormularioNovedad(model, novedad);
        return "gerente/novedades/form";
    }

    @PostMapping("/novedades/guardar")
    public String guardarNovedad(@ModelAttribute Novedad novedad,
                                 @RequestParam("file") MultipartFile file,
                                 Authentication auth,
                                 RedirectAttributes redirectAttributes) throws IOException {
        novedad.setUsuario(obtenerUsuarioAutenticado(auth));
        novedad.setEstado(normalizarEstadoNovedad(novedad.getEstado()));
        novedad.setPrioridad(normalizarPrioridadNovedad(novedad.getPrioridad()));
        novedad.setFechaReporte(LocalDateTime.now());
        novedad.setFechaGestion(LocalDateTime.now());
        if (novedad.getAplicaGarantia() == null) {
            novedad.setAplicaGarantia(Boolean.FALSE);
        }
        completarDatosVehiculoNovedad(novedad);

        if (!file.isEmpty()) {
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path path = Paths.get(UPLOAD_DIR + fileName);
            Files.createDirectories(path.getParent());
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
            novedad.setEvidencia(fileName);
        }
        novedadRepository.save(novedad);
        redirectAttributes.addFlashAttribute("success", "La novedad fue reportada y quedó disponible para gestión gerencial.");
        return "redirect:/gerente/novedades";
    }

    @GetMapping("/novedades/editar/{id}")
    public String editarNovedadForm(@PathVariable Long id, Model model, Authentication auth) {
        agregarDatosCorreo(model, auth);
        Novedad novedad = novedadRepository.findById(id).orElseThrow();
        prepararFormularioNovedad(model, novedad);
        return "gerente/novedades/form";
    }

    @PostMapping("/novedades/actualizar/{id}")
    public String actualizarNovedad(@PathVariable Long id,
                                    @ModelAttribute Novedad novedad,
                                    @RequestParam("file") MultipartFile file,
                                    RedirectAttributes redirectAttributes) throws IOException {
        Novedad existente = novedadRepository.findById(id).orElseThrow();

        if (!file.isEmpty()) {
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path path = Paths.get(UPLOAD_DIR + fileName);
            Files.createDirectories(path.getParent());
            Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
            existente.setEvidencia(fileName);
        }

        existente.setTipoNovedad(novedad.getTipoNovedad());
        existente.setDescripcion(novedad.getDescripcion());
        existente.setEstado(normalizarEstadoNovedad(novedad.getEstado()));
        existente.setVehiculoChasis(novedad.getVehiculoChasis());
        existente.setOrigenReporte(novedad.getOrigenReporte());
        existente.setPrioridad(normalizarPrioridadNovedad(novedad.getPrioridad()));
        existente.setAplicaGarantia(Boolean.TRUE.equals(novedad.getAplicaGarantia()));
        existente.setAccionRequerida(novedad.getAccionRequerida());
        existente.setObservacionGerente(novedad.getObservacionGerente());
        existente.setFechaGestion(LocalDateTime.now());
        completarDatosVehiculoNovedad(existente);

        novedadRepository.save(existente);
        redirectAttributes.addFlashAttribute("success", "La novedad fue actualizada correctamente.");
        return "redirect:/gerente/novedades";
    }

    @PostMapping("/novedades/{id}/estado")
    public String cambiarEstadoNovedad(@PathVariable Long id,
                                       @RequestParam String estado,
                                       RedirectAttributes redirectAttributes) {
        Novedad novedad = novedadRepository.findById(id).orElseThrow();
        novedad.setEstado(normalizarEstadoNovedad(estado));
        novedad.setFechaGestion(LocalDateTime.now());
        novedadRepository.save(novedad);
        redirectAttributes.addFlashAttribute("success", "El estado de la novedad fue actualizado.");
        return "redirect:/gerente/novedades";
    }

    @PostMapping("/novedades/eliminar/{id}")
    public String eliminarNovedad(@PathVariable Long id) {
        novedadRepository.deleteById(id);
        return "redirect:/gerente/novedades";
    }

    private void agregarDatosCorreo(Model model, Authentication auth) {
        if (auth == null) {
            return;
        }

        usuarioRepository.findByCorreo(auth.getName()).ifPresent(usuario -> {
            model.addAttribute("usuarioId", usuario.getIdUsuario());
            model.addAttribute("usuarioRol", usuario.getRol() != null ? usuario.getRol().getNombre() : "Usuario");
            model.addAttribute("unreadCount", mensajeService.contarNoLeidos(usuario.getIdUsuario()));
        });
    }

    private String normalizarEstado(String estado) {
        return estado == null ? "pendiente" : estado.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizarEstadoNovedad(String estado) {
        if (estado == null || estado.isBlank()) {
            return "pendiente";
        }

        String valor = estado.trim().toLowerCase(Locale.ROOT)
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace(' ', '_');

        return switch (valor) {
            case "en_revision", "revision" -> "en_revision";
            case "gestion_garantia", "garantia" -> "gestion_garantia";
            case "resuelto", "cerrado" -> "resuelto";
            default -> "pendiente";
        };
    }

    private String normalizarPrioridadNovedad(String prioridad) {
        if (prioridad == null || prioridad.isBlank()) {
            return "media";
        }

        String valor = prioridad.trim().toLowerCase(Locale.ROOT)
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u");

        return switch (valor) {
            case "baja", "media", "alta", "critica" -> valor;
            default -> "media";
        };
    }

    private int pesoPrioridadNovedad(String prioridad) {
        return switch (normalizarPrioridadNovedad(prioridad)) {
            case "critica" -> 4;
            case "alta" -> 3;
            case "media" -> 2;
            default -> 1;
        };
    }

    private boolean esNovedadAbierta(Novedad novedad) {
        return !"resuelto".equals(normalizarEstadoNovedad(novedad.getEstado()));
    }

    private boolean esNovedadCritica(Novedad novedad) {
        return esNovedadAbierta(novedad) && pesoPrioridadNovedad(novedad.getPrioridad()) >= 3;
    }

    private void prepararFormularioNovedad(Model model, Novedad novedad) {
        model.addAttribute("novedad", novedad);
        model.addAttribute("inventariosDisponibles", inventarioRepository.findAll().stream()
                .sorted(Comparator.comparing(Inventario::getMarca, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(Inventario::getModelo, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList());
    }

    private Usuario obtenerUsuarioAutenticado(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return null;
        }
        return usuarioRepository.findByCorreo(auth.getName()).orElse(null);
    }

    private void completarDatosVehiculoNovedad(Novedad novedad) {
        if (novedad.getVehiculoChasis() == null || novedad.getVehiculoChasis().isBlank()) {
            novedad.setVehiculoReferencia("Sin vehiculo asociado");
            return;
        }

        inventarioRepository.findByChasis(novedad.getVehiculoChasis()).ifPresentOrElse(inventario -> {
            String marca = inventario.getMarca() != null ? inventario.getMarca() : "";
            String modelo = inventario.getModelo() != null ? inventario.getModelo() : "";
            String referencia = (marca + " " + modelo).trim();
            novedad.setVehiculoReferencia(referencia.isBlank() ? inventario.getChasis() : referencia);
        }, () -> novedad.setVehiculoReferencia(novedad.getVehiculoChasis()));
    }

    private String extraerVehiculo(Pedido pedido) {
        if (pedido.getVehiculo() == null) {
            return "Sin vehiculo";
        }

        String marca = pedido.getVehiculo().getMarca() != null ? pedido.getVehiculo().getMarca() : "";
        String modelo = pedido.getVehiculo().getModelo() != null ? pedido.getVehiculo().getModelo() : "";
        String vehiculo = (marca + " " + modelo).trim();

        return vehiculo.isBlank() ? "Sin vehiculo" : vehiculo;
    }

    private String extraerCliente(Pedido pedido) {
        return pedido.getDescripcion() != null && !pedido.getDescripcion().isBlank()
                ? pedido.getDescripcion()
                : "Cliente sin nombre";
    }

    private List<Map.Entry<String, Long>> topVehiculosSolicitados(List<Pedido> pedidos, int limite) {
        return pedidos.stream()
                .collect(Collectors.groupingBy(this::extraerVehiculo, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limite)
                .toList();
    }

    private Map<String, Long> resumenEstados(List<Pedido> pedidos) {
        return pedidos.stream()
                .collect(Collectors.groupingBy(p -> normalizarEstado(p.getEstado()), Collectors.counting()));
    }

    private List<Map<String, Object>> construirRendimientoVendedores(List<Pedido> pedidos) {
        Map<String, List<Pedido>> porVendedor = pedidos.stream()
                .collect(Collectors.groupingBy(p -> {
                    if (p.getConductor() == null || p.getConductor().getNombre() == null || p.getConductor().getNombre().isBlank()) {
                        return "Sin vendedor";
                    }
                    return p.getConductor().getNombre();
                }));

        return porVendedor.entrySet().stream()
                .map(entry -> {
                    String vendedor = entry.getKey();
                    List<Pedido> pedidosVendedor = entry.getValue();
                    long interesados = pedidosVendedor.stream()
                            .filter(p -> ESTADOS_INTERES.contains(normalizarEstado(p.getEstado())))
                            .count();
                    long ventas = pedidosVendedor.stream()
                            .filter(p -> ESTADOS_VENTA.contains(normalizarEstado(p.getEstado())))
                            .count();

                    Map<String, Object> datos = new HashMap<>();
                    datos.put("vendedor", vendedor);
                    datos.put("interesados", interesados);
                    datos.put("ventas", ventas);
                    datos.put("conversion", interesados == 0 ? 0.0 : (ventas * 100.0) / interesados);
                    return datos;
                })
                .sorted((a, b) -> Long.compare((long) b.get("ventas"), (long) a.get("ventas")))
                .toList();
    }

    private List<Notificacion> construirAlertasInteligentes() {
        List<Notificacion> alertas = new ArrayList<>();
        LocalDateTime ahora = LocalDateTime.now();

        long inventarioBajo = inventarioRepository.findByActivoTrue().size();
        if (inventarioBajo < 5) {
            Notificacion n = new Notificacion();
            n.setTitulo("Inventario bajo");
            n.setMensaje("Solo hay " + inventarioBajo + " vehiculos activos en inventario.");
            n.setFecha(ahora);
            n.setLeida(false);
            alertas.add(n);
        }

        long clientesSinContacto = pedidoRepository.findAll().stream()
                .filter(p -> "pendiente".equals(normalizarEstado(p.getEstado())))
                .filter(p -> p.getFechaCreacion() != null && p.getFechaCreacion().isBefore(ahora.minusDays(5)))
                .count();
        if (clientesSinContacto > 0) {
            Notificacion n = new Notificacion();
            n.setTitulo("Clientes sin contacto");
            n.setMensaje(clientesSinContacto + " clientes llevan mas de 5 dias sin contacto.");
            n.setFecha(ahora);
            n.setLeida(false);
            alertas.add(n);
        }

        Map<Long, Boolean> vendidoPorVehiculo = pedidoRepository.findAll().stream()
                .filter(p -> p.getVehiculo() != null && p.getVehiculo().getIdVehiculo() != null)
                .collect(Collectors.toMap(
                        p -> p.getVehiculo().getIdVehiculo(),
                        p -> ESTADOS_VENTA.contains(normalizarEstado(p.getEstado())),
                        (a, b) -> a || b
                ));

        long vehiculosSinVenta60Dias = vehiculoRepository.findByActivoTrue().stream()
                .filter(v -> v.getFechaCreacion() != null && v.getFechaCreacion().isBefore(ahora.minusDays(60)))
                .filter(v -> !vendidoPorVehiculo.getOrDefault(v.getIdVehiculo(), false))
                .count();

        if (vehiculosSinVenta60Dias > 0) {
            Notificacion n = new Notificacion();
            n.setTitulo("Vehiculos sin rotacion");
            n.setMensaje(vehiculosSinVenta60Dias + " vehiculos llevan mas de 60 dias sin venderse.");
            n.setFecha(ahora);
            n.setLeida(false);
            alertas.add(n);
        }

        return alertas;
    }

    private void sincronizarVehiculoConInventario(Inventario inventario) {
        if (inventario.getChasis() == null || inventario.getChasis().isBlank()) {
            return;
        }

        Vehiculo vehiculo = vehiculoRepository.findByChasis(inventario.getChasis())
                .orElseGet(Vehiculo::new);

        vehiculo.setChasis(inventario.getChasis());
        vehiculo.setMarca(inventario.getMarca());
        vehiculo.setModelo(inventario.getModelo());
        vehiculo.setAnio(inventario.getAnio());
        vehiculo.setActivo(inventario.isActivo());

        if (vehiculo.getTipoCombustible() == null || vehiculo.getTipoCombustible().isBlank()) {
            vehiculo.setTipoCombustible("Gasolina");
        }
        if (vehiculo.getTransmision() == null || vehiculo.getTransmision().isBlank()) {
            vehiculo.setTransmision("Manual");
        }

        vehiculoRepository.save(vehiculo);
    }

    private void agregarResumenInventario(Model model, List<Inventario> inventarios) {
        long activos = inventarios.stream().filter(Inventario::isActivo).count();
        long inactivos = inventarios.stream().filter(i -> !i.isActivo()).count();

        model.addAttribute("totalInventarioVista", inventarios.size());
        model.addAttribute("totalActivosVista", activos);
        model.addAttribute("totalInactivosVista", inactivos);
        model.addAttribute("enBodegaVista", inventarios.stream()
                .filter(i -> i.getEstadoLogistico() != null)
                .filter(i -> i.getEstadoLogistico().toLowerCase(Locale.ROOT).contains("bodega"))
                .count());
    }

    private List<Map<String, Object>> construirResumenMensual(List<Pedido> pedidos, int cantidadMeses) {
        YearMonth actual = YearMonth.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy", LOCALE_ES);

        Map<YearMonth, Map<String, Object>> acumulado = new LinkedHashMap<>();
        for (int i = cantidadMeses - 1; i >= 0; i--) {
            YearMonth mes = actual.minusMonths(i);
            Map<String, Object> fila = new HashMap<>();
            fila.put("mes", mes.format(formatter));
            fila.put("interesados", 0L);
            fila.put("ventas", 0L);
            fila.put("ingresos", 0.0);
            acumulado.put(mes, fila);
        }

        for (Pedido pedido : pedidos) {
            if (pedido.getFechaCreacion() == null) {
                continue;
            }
            YearMonth mes = YearMonth.from(pedido.getFechaCreacion());
            Map<String, Object> fila = acumulado.get(mes);
            if (fila == null) {
                continue;
            }

            long interesados = ((Number) fila.get("interesados")).longValue() + 1;
            fila.put("interesados", interesados);

            if (ESTADOS_VENTA.contains(normalizarEstado(pedido.getEstado()))) {
                long ventas = ((Number) fila.get("ventas")).longValue() + 1;
                double ingresos = ((Number) fila.get("ingresos")).doubleValue() + calcularIngresoPedido(pedido);
                fila.put("ventas", ventas);
                fila.put("ingresos", ingresos);
            }
        }

        return new ArrayList<>(acumulado.values());
    }

    private double calcularIngresoPedido(Pedido pedido) {
        if (!ESTADOS_VENTA.contains(normalizarEstado(pedido.getEstado()))) {
            return 0.0;
        }
        if (pedido.getVehiculo() == null || pedido.getVehiculo().getPrecio() == null) {
            return 0.0;
        }
        return pedido.getVehiculo().getPrecio();
    }

    private String formatearMoneda(double valor) {
        NumberFormat format = NumberFormat.getCurrencyInstance(LOCALE_ES);
        return format.format(valor);
    }

    private Cell celdaKpi(String titulo, String valor, DeviceRgb colorAcento) {
        Paragraph texto = new Paragraph(titulo + "\n" + valor)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(10)
                .setBold();

        return new Cell()
                .add(texto)
            .setBackgroundColor(new DeviceRgb(248, 250, 253))
            .setFontColor(colorAcento)
                .setPadding(10);
    }

    private Cell celdaHeader(String texto, DeviceRgb colorFondo) {
        return new Cell()
                .add(new Paragraph(texto).setBold().setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(colorFondo)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(8);
    }

    private Cell celdaDato(String texto) {
        return new Cell()
                .add(new Paragraph(texto != null ? texto : "-"))
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(6);
    }

    private byte[] generarGraficoVentasIngresos(List<Map<String, Object>> resumenMensual) {
        try {
            DefaultCategoryDataset dataset = new DefaultCategoryDataset();
            for (Map<String, Object> fila : resumenMensual) {
                String mes = (String) fila.get("mes");
                long ventas = ((Number) fila.get("ventas")).longValue();
                double ingresos = ((Number) fila.get("ingresos")).doubleValue();

                dataset.addValue(ventas, "Ventas", mes);
                dataset.addValue(ingresos, "Ingresos", mes);
            }

            JFreeChart chart = ChartFactory.createBarChart(
                    "Comportamiento mensual",
                    "Mes",
                    "Valor",
                    dataset,
                    PlotOrientation.VERTICAL,
                    true,
                    false,
                    false
            );

            CategoryPlot plot = chart.getCategoryPlot();
            plot.setBackgroundPaint(java.awt.Color.WHITE);
            plot.setRangeGridlinePaint(new java.awt.Color(214, 220, 230));

            ByteArrayOutputStream chartOutput = new ByteArrayOutputStream();
            ChartUtils.writeChartAsPNG(chartOutput, chart, 900, 360);
            return chartOutput.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    // ===============================
    // 👥 GESTIÓN DE CLIENTES (NUEVO)
    // ===============================
    @GetMapping("/clientes")
    public String listarClientes(@RequestParam(required = false) String estado, Model model) {
        List<Cliente> clientes;
        if (estado != null && !estado.isEmpty()) {
            clientes = clienteRepository.findByActivoTrueAndEstado(estado);
            model.addAttribute("estadoFiltro", estado);
        } else {
            clientes = clienteRepository.findByActivoTrue();
        }
        model.addAttribute("clientes", clientes);
        model.addAttribute("estadosDisponibles", new String[]{"Nuevo", "Interesado", "Negociando", "Comprador"});
        return "gerente/clientes/listar";
    }

    @GetMapping("/clientes/crear")
    public String crearClienteForm(Model model) {
        model.addAttribute("cliente", new Cliente());
        return "gerente/clientes/form";
    }

    @PostMapping("/clientes/guardar")
    public String guardarCliente(@ModelAttribute Cliente cliente) {
        cliente.setFechaRegistro(LocalDateTime.now());
        cliente.setFechaUltimaInteraccion(LocalDateTime.now());
        clienteRepository.save(cliente);
        return "redirect:/gerente/clientes";
    }

    @GetMapping("/clientes/editar/{id}")
    public String editarClienteForm(@PathVariable Long id, Model model) {
        Cliente cliente = clienteRepository.findById(id).orElseThrow();
        model.addAttribute("cliente", cliente);
        return "gerente/clientes/form";
    }

    @PostMapping("/clientes/editar/{id}")
    public String actualizarCliente(@PathVariable Long id, @ModelAttribute Cliente cliente) {
        Cliente existente = clienteRepository.findById(id).orElseThrow();
        existente.setNombre(cliente.getNombre());
        existente.setCorreo(cliente.getCorreo());
        existente.setTelefono(cliente.getTelefono());
        existente.setCedula(cliente.getCedula());
        existente.setCiudad(cliente.getCiudad());
        existente.setDireccion(cliente.getDireccion());
        existente.setInteresVehiculo(cliente.getInteresVehiculo());
        existente.setPresupuesto(cliente.getPresupuesto());
        existente.setEstado(cliente.getEstado());
        existente.setNotas(cliente.getNotas());
        existente.setFechaUltimaInteraccion(LocalDateTime.now());
        clienteRepository.save(existente);
        return "redirect:/gerente/clientes";
    }

    @PostMapping("/clientes/{id}/cambiar-estado")
    public String cambiarEstadoCliente(@PathVariable Long id, @RequestParam String nuevoEstado) {
        Cliente cliente = clienteRepository.findById(id).orElseThrow();
        cliente.setEstado(nuevoEstado);
        cliente.setFechaUltimaInteraccion(LocalDateTime.now());
        clienteRepository.save(cliente);
        return "redirect:/gerente/clientes";
    }

    @PostMapping("/clientes/desactivar/{id}")
    public String desactivarCliente(@PathVariable Long id) {
        Cliente cliente = clienteRepository.findById(id).orElseThrow();
        cliente.setActivo(false);
        clienteRepository.save(cliente);
        return "redirect:/gerente/clientes";
    }

    // ===============================
    // 🏁 SOLICITUDES DE PRUEBA (NUEVO)
    // ===============================
    @GetMapping("/pruebas")
    public String listarPruebas(@RequestParam(required = false) String estado, Model model) {
        List<SolicitudPrueba> pruebas;
        if (estado != null && !estado.isEmpty()) {
            pruebas = solicitudPruebaRepository.findByEstado(estado);
            model.addAttribute("estadoFiltro", estado);
        } else {
            pruebas = solicitudPruebaRepository.findAll();
        }
        model.addAttribute("pruebas", pruebas);
        model.addAttribute("estadosDisponibles", new String[]{"Pendiente", "Aprobada", "Realizada", "Rechazada"});
        return "gerente/pruebas/listar";
    }

    @GetMapping("/pruebas/cliente/{idCliente}")
    public String listarPruebasPorCliente(@PathVariable Long idCliente, Model model) {
        Cliente cliente = clienteRepository.findById(idCliente).orElseThrow();
        List<SolicitudPrueba> pruebas = solicitudPruebaRepository.findByCliente(cliente);
        model.addAttribute("pruebas", pruebas);
        model.addAttribute("cliente", cliente);
        return "gerente/pruebas/por-cliente";
    }

    @GetMapping("/pruebas/pendientes")
    public String pruebasPendientes(Model model) {
        List<SolicitudPrueba> pruebas = solicitudPruebaRepository.findByEstado("Pendiente");
        model.addAttribute("pruebas", pruebas);
        return "gerente/pruebas/listar";
    }

    @PostMapping("/pruebas/{id}/aprobar")
    public String aprobarPrueba(@PathVariable Long id) {
        SolicitudPrueba solicitud = solicitudPruebaRepository.findById(id).orElseThrow();
        solicitud.setEstado("Aprobada");
        solicitud.setFechaAprobada(LocalDateTime.now());
        solicitudPruebaRepository.save(solicitud);
        return "redirect:/gerente/pruebas";
    }

    @PostMapping("/pruebas/{id}/rechazar")
    public String rechazarPrueba(@PathVariable Long id) {
        SolicitudPrueba solicitud = solicitudPruebaRepository.findById(id).orElseThrow();
        solicitud.setEstado("Rechazada");
        solicitudPruebaRepository.save(solicitud);
        return "redirect:/gerente/pruebas";
    }

    @PostMapping("/pruebas/{id}/marcar-realizada")
    public String marcarPruebaRealizada(
            @PathVariable Long id,
            @RequestParam String resultado,
            @RequestParam(required = false) String notas) {
        SolicitudPrueba solicitud = solicitudPruebaRepository.findById(id).orElseThrow();
        solicitud.setEstado("Realizada");
        solicitud.setFechaRealizacion(LocalDateTime.now());
        solicitud.setResultadoPrueba(resultado);
        if (notas != null) {
            solicitud.setNotas(notas);
        }
        if ("Muy Interesado".equals(resultado)) {
            Cliente cliente = solicitud.getCliente();
            cliente.setEstado("Negociando");
            cliente.setFechaUltimaInteraccion(LocalDateTime.now());
            clienteRepository.save(cliente);
        }
        solicitudPruebaRepository.save(solicitud);
        return "redirect:/gerente/pruebas";
    }
}

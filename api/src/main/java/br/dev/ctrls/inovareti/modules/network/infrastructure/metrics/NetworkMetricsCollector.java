package br.dev.ctrls.inovareti.modules.network.infrastructure.metrics;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Coletor/Adapter de infraestrutura responsável por monitorar ativos de rede
 * (como roteadores/switches MikroTik e pontos de acesso Ubiquiti UniFi).
 * Expõe métricas de tráfego de rede e latência (ping) utilizando o Micrometer.
 */
@Component
@lombok.extern.slf4j.Slf4j
public class NetworkMetricsCollector {

    private final MeterRegistry meterRegistry;
    
    // Contadores para tráfego acumulativo recebido e transmitido (volumetria em bytes)
    private final Counter mikrotikTrafficRx;
    private final Counter mikrotikTrafficTx;
    private final Counter ubiquitiTrafficRx;
    private final Counter ubiquitiTrafficTx;

    // Gauges para monitoramento instantâneo de latência de ping e contagem de clientes Wi-Fi ativos
    private final AtomicLong mikrotikLatencyMs = new AtomicLong(0);
    private final AtomicLong ubiquitiLatencyMs = new AtomicLong(0);
    private final AtomicLong activeWifiClients = new AtomicLong(0);

    /**
     * Construtor que inicializa e registra os contadores e gauges no MeterRegistry do Micrometer.
     *
     * @param meterRegistry O registro de métricas injetado pelo Spring.
     */
    public NetworkMetricsCollector(MeterRegistry meterRegistry) {
        // Inicializa a variável de instância para garantir o gerenciamento do ciclo de vida das métricas
        this.meterRegistry = meterRegistry;

        // Registro de Contadores do Micrometer para volumetria acumulativa de tráfego de rede em bytes.
        // O registro é associado diretamente à instância meterRegistry para garantir que as métricas
        // fiquem disponíveis para coleta via endpoints de telemetria do Actuator.
        this.mikrotikTrafficRx = Counter.builder("network_traffic_bytes_total")
                .description("Tráfego total recebido no MikroTik")
                .tag("device", "mikrotik")
                .tag("direction", "rx")
                .register(this.meterRegistry);

        this.mikrotikTrafficTx = Counter.builder("network_traffic_bytes_total")
                .description("Tráfego total transmitido no MikroTik")
                .tag("device", "mikrotik")
                .tag("direction", "tx")
                .register(this.meterRegistry);

        this.ubiquitiTrafficRx = Counter.builder("network_traffic_bytes_total")
                .description("Tráfego total recebido nos APs Ubiquiti UniFi")
                .tag("device", "ubiquiti")
                .tag("direction", "rx")
                .register(this.meterRegistry);

        this.ubiquitiTrafficTx = Counter.builder("network_traffic_bytes_total")
                .description("Tráfego total transmitido nos APs Ubiquiti UniFi")
                .tag("device", "ubiquiti")
                .tag("direction", "tx")
                .register(this.meterRegistry);

        // Registro de Gauges do Micrometer para acompanhamento instantâneo de latência de ping (MS).
        // Os Gauges observam dinamicamente os valores mantidos em estruturas thread-safe (AtomicLong).
        Gauge.builder("network_device_ping_latency_ms", this.mikrotikLatencyMs, AtomicLong::get)
                .description("Latência de ping do MikroTik principal")
                .tag("device", "mikrotik")
                .register(this.meterRegistry);

        Gauge.builder("network_device_ping_latency_ms", this.ubiquitiLatencyMs, AtomicLong::get)
                .description("Latência de ping do Ubiquiti UniFi Controller")
                .tag("device", "ubiquiti")
                .register(this.meterRegistry);

        // Registro do Gauge para contagem ativa e instantânea de clientes de Wi-Fi.
        Gauge.builder("network_active_wifi_clients_total", this.activeWifiClients, AtomicLong::get)
                .description("Número de clientes ativos conectados na rede Wi-Fi UniFi")
                .register(this.meterRegistry);
    }

    /**
     * Retorna o MeterRegistry associado a este coletor.
     * Utilizado para manipulação ou consulta dinâmica externa das métricas de infraestrutura de rede.
     *
     * @return O meterRegistry.
     */
    public MeterRegistry getMeterRegistry() {
        return this.meterRegistry;
    }

    /**
     * Incrementa o contador de tráfego de recebimento (RX) do MikroTik.
     *
     * @param bytes Quantidade de bytes recebidos.
     */
    public void recordMikrotikRx(long bytes) {
        log.debug("[MÉTRICAS-REDE] Registrando RX MikroTik: {} bytes", bytes);
        this.mikrotikTrafficRx.increment(bytes);
    }

    /**
     * Incrementa o contador de tráfego de transmissão (TX) do MikroTik.
     *
     * @param bytes Quantidade de bytes transmitidos.
     */
    public void recordMikrotikTx(long bytes) {
        log.debug("[MÉTRICAS-REDE] Registrando TX MikroTik: {} bytes", bytes);
        this.mikrotikTrafficTx.increment(bytes);
    }

    /**
     * Incrementa o contador de tráfego de recebimento (RX) dos dispositivos Ubiquiti.
     *
     * @param bytes Quantidade de bytes recebidos.
     */
    public void recordUbiquitiRx(long bytes) {
        log.debug("[MÉTRICAS-REDE] Registrando RX Ubiquiti: {} bytes", bytes);
        this.ubiquitiTrafficRx.increment(bytes);
    }

    /**
     * Incrementa o contador de tráfego de transmissão (TX) dos dispositivos Ubiquiti.
     *
     * @param bytes Quantidade de bytes transmitidos.
     */
    public void recordUbiquitiTx(long bytes) {
        log.debug("[MÉTRICAS-REDE] Registrando TX Ubiquiti: {} bytes", bytes);
        this.ubiquitiTrafficTx.increment(bytes);
    }

    /**
     * Atualiza o valor instantâneo do ping do MikroTik.
     *
     * @param latencyMs Latência em milissegundos.
     */
    public void updateMikrotikPing(long latencyMs) {
        log.debug("[MÉTRICAS-REDE] Atualizando latência MikroTik: {} ms", latencyMs);
        this.mikrotikLatencyMs.set(latencyMs);
    }

    /**
     * Atualiza o valor instantâneo do ping do Ubiquiti Controller.
     *
     * @param latencyMs Latência em milissegundos.
     */
    public void updateUbiquitiPing(long latencyMs) {
        log.debug("[MÉTRICAS-REDE] Atualizando latência Ubiquiti: {} ms", latencyMs);
        this.ubiquitiLatencyMs.set(latencyMs);
    }

    /**
     * Atualiza o número atual de clientes Wi-Fi conectados no controlador UniFi.
     *
     * @param clientsCount Quantidade de clientes Wi-Fi ativos.
     */
    public void updateActiveWifiClients(long clientsCount) {
        log.debug("[MÉTRICAS-REDE] Atualizando clientes Wi-Fi ativos: {}", clientsCount);
        this.activeWifiClients.set(clientsCount);
    }
}

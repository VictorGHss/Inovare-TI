package br.dev.ctrls.inovareti.domain.notification.discord.bot;

import java.text.DecimalFormat;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/**
 * Serviço responsável por coletar métricas de infraestrutura do servidor
 * e do banco de dados para o comando '/ti status' do Discord.
 *
 * <p>Coleta:
 * <ul>
 *   <li>Memória JVM: livre, total e máxima via {@link Runtime}</li>
 *   <li>Processadores disponíveis via {@link Runtime}</li>
 *   <li>Tamanho do banco PostgreSQL via função nativa {@code pg_database_size}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordInfraStatusService {

    private static final int COR_VERDE_TI = 0x2ECC71;
    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");

    private final DataSource dataSource;

    /**
     * Constrói o embed de status de infraestrutura do servidor.
     *
     * @return {@link MessageEmbed} formatado com métricas de RAM, CPU e banco
     */
    @SuppressWarnings("null")
    public MessageEmbed construirEmbedStatus() {
        Runtime runtime = Runtime.getRuntime();

        long memoriaLivreMb  = runtime.freeMemory()  / (1024 * 1024);
        long memoriaTotalMb  = runtime.totalMemory()  / (1024 * 1024);
        long memoriaMaximaMb = runtime.maxMemory()    / (1024 * 1024);
        long memoriaUsadaMb  = memoriaTotalMb - memoriaLivreMb;
        int  processadores   = runtime.availableProcessors();

        double percentualUso = memoriaTotalMb > 0
                ? (double) memoriaUsadaMb / memoriaMaximaMb * 100.0
                : 0.0;

        String tamanhoBanco = consultarTamanhoBanco();

        String memoriaUsadaLabel = memoriaUsadaMb + " MB / " + memoriaMaximaMb + " MB"
                + " (" + DF.format(percentualUso) + "%)";
        String memoriaLivreLabel  = memoriaLivreMb + " MB";
        String memoriaTotalLabel  = memoriaTotalMb + " MB";
        String processadoresLabel = String.valueOf(processadores);

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(COR_VERDE_TI)
                .setTitle("🖥️ Painel de Status — Inovare TI")
                .setDescription("Métricas em tempo real do servidor de aplicação e do banco de dados.")
                .addField("💾 Memória Usada (JVM)", memoriaUsadaLabel, false)
                .addField("🟢 Memória Livre (heap JVM)", memoriaLivreLabel, true)
                .addField("📦 Memória Total Alocada (JVM)", memoriaTotalLabel, true)
                .addField("⚙️ Processadores Disponíveis", processadoresLabel, true)
                .addField("🗄️ Tamanho do Banco de Dados (PostgreSQL)", tamanhoBanco, false)
                .setFooter("Inovare TI • Infraestrutura em Saúde | /ti status");

        return embed.build();
    }

    /**
     * Consulta o tamanho do banco de dados atual usando {@code pg_database_size}.
     * Retorna uma string formatada em MB ou uma mensagem de erro caso a query falhe.
     */
    private String consultarTamanhoBanco() {
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Long bytes = jdbc.queryForObject(
                    "SELECT pg_database_size(current_database())",
                    Long.class);

            if (bytes == null) return "N/D";

            double mb = bytes / (1024.0 * 1024.0);
            return DF.format(mb) + " MB";
        } catch (Exception ex) {
            log.warn("[DISCORD][/ti status] Falha ao consultar tamanho do banco PostgreSQL: {}", ex.getMessage());
            return "Indisponível (erro ao consultar)";
        }
    }
}

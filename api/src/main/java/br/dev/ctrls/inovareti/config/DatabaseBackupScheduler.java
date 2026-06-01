package br.dev.ctrls.inovareti.config;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.finance.domain.model.SystemAlert;
import br.dev.ctrls.inovareti.modules.finance.domain.port.SystemAlertRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ServiÃƒÂ§o agendador responsÃƒÂ¡vel por executar o backup automÃƒÂ¡tico do banco de dados (PostgreSQL)
 * diariamente ÃƒÂ s 3h da manhÃƒÂ£, compactando o arquivo SQL gerado em formato ZIP e enviando por e-mail.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseBackupScheduler {

    private final JavaMailSender mailSender;
    private final SystemAlertRepository systemAlertRepository;

    @Value("${app.backup.enabled:false}")
    private boolean backupEnabled;

    @Value("${app.backup.destination-email}")
    private String destinationEmail;

    @Value("${app.backup.pg-dump-binary:}")
    private String pgDumpBinary;

    @Value("${app.backup.temp-dir}")
    private String tempDir;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${spring.mail.username}")
    private String smtpUsername;

    @Value("${app.backup.zip-password:}")
    private String zipPassword;

    /**
     * Executa a rotina de backup todos os dias ÃƒÂ s 3h da manhÃƒÂ£.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void executeBackup() {
        executeBackupInternal(false);
    }

    public void executeBackupManual() {
        executeBackupInternal(true);
    }

    private void executeBackupInternal(boolean isManual) {
        if (!backupEnabled && !isManual) {
            log.info("Backup automÃƒÂ¡tico do banco de dados ignorado pois 'app.backup.enabled' estÃƒÂ¡ desabilitado.");
            return;
        }

        log.info("Iniciando rotina de backup do banco de dados. Manual={}", isManual);

        // ValidaÃƒÂ§ÃƒÂ£o antecipada: binÃƒÂ¡rio pg_dump deve estar configurado
        if (pgDumpBinary == null || pgDumpBinary.isBlank()) {
            String timestamp0 = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            log.error("CRITICAL - pg_dump binary nÃƒÂ£o configurado. Defina a variÃƒÂ¡vel de ambiente APP_BACKUP_PG_DUMP_BINARY com o caminho completo do binÃƒÂ¡rio pg_dump.");
            saveAlert("CRITICAL", "pg_dump binary nÃƒÂ£o configurado",
                    "A propriedade 'app.backup.pg-dump-binary' estÃƒÂ¡ em branco ou nula. Configure APP_BACKUP_PG_DUMP_BINARY com o caminho completo do binÃƒÂ¡rio pg_dump (ex: /usr/bin/pg_dump ou via docker exec).",
                    timestamp0, 0);
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File tempFolder = new File(tempDir);
        
        // Garante a existÃƒÂªncia do diretÃƒÂ³rio temporÃƒÂ¡rio no HDD configurado
        if (!tempFolder.exists()) {
            if (tempFolder.mkdirs()) {
                log.info("DiretÃƒÂ³rio temporÃƒÂ¡rio de backups criado em: {}", tempFolder.getAbsolutePath());
            }
        }

        File sqlFile = new File(tempFolder, "backup_" + timestamp + ".sql");
        File zipFile = new File(tempFolder, "backup_" + timestamp + ".zip");

        try {
            // 1. ExtraÃƒÂ§ÃƒÂ£o dinÃƒÂ¢mica das informaÃƒÂ§ÃƒÂµes de conexÃƒÂ£o a partir do JDBC URL
            String cleanUrl = dbUrl.replace("jdbc:postgresql://", "");
            String[] parts = cleanUrl.split("/");
            String hostPort = parts[0];
            String dbName = parts[1];
            if (dbName.contains("?")) {
                dbName = dbName.split("\\?")[0];
            }

            String host;
            String port = "5432";
            if (hostPort.contains(":")) {
                String[] hp = hostPort.split(":");
                host = hp[0];
                port = hp[1];
            } else {
                host = hostPort;
            }

            log.info("Executando dump do banco de dados '{}' no servidor '{}:{}'", dbName, host, port);

            // 2. Disparar comando nativo pg_dump de forma segura via ProcessBuilder
            ProcessBuilder pb = new ProcessBuilder(
                    pgDumpBinary,
                    "-h", host,
                    "-p", port,
                    "-U", dbUser,
                    "-f", sqlFile.getAbsolutePath(),
                    dbName
            );
            
            // Passa a senha via variÃƒÂ¡vel de ambiente PGPASSWORD para execuÃƒÂ§ÃƒÂ£o totalmente nÃƒÂ£o-interativa
            pb.environment().put("PGPASSWORD", dbPassword);
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                throw new IOException("O utilitÃƒÂ¡rio pg_dump falhou e retornou cÃƒÂ³digo de saÃƒÂ­da: " + exitCode);
            }

            log.info("Dump PostgreSQL gerado com sucesso em: {}", sqlFile.getAbsolutePath());

            // 3. CompactaÃƒÂ§ÃƒÂ£o do dump SQL em formato ZIP
            zipFile(sqlFile, zipFile);
            log.info("Arquivo compactado com sucesso em formato ZIP: {}", zipFile.getAbsolutePath());

            // 4. Utilizar JavaMailSender para enviar por e-mail
            try {
                sendBackupEmail(zipFile, timestamp);
            } catch (MessagingException emailEx) {
                log.warn("Falha ao enviar backup por e-mail, mas o arquivo fÃƒÂ­sico foi preservado: {}", emailEx.getMessage());
            }

            // 5. Registrar sucesso no SystemAlertRepository e Logs
            saveAlert("INFO", "Backup do banco de dados realizado com sucesso", 
                    "O backup foi executado e salvo em disco com sucesso. Origem: " + (isManual ? "Manual" : "Agendado"), timestamp, zipFile.length());
            
            log.info("Rotina de backup finalizada com sucesso. Arquivo ZIP salvo em: {}", zipFile.getAbsolutePath());

        } catch (IOException | InterruptedException e) {
            log.error("Falha crÃƒÂ­tica durante a execuÃƒÂ§ÃƒÂ£o do backup do banco de dados: {}", e.getMessage(), e);
            saveAlert("CRITICAL", "Falha na rotina de backup do banco de dados", 
                    "Ocorreu um erro ao processar ou salvar o backup: " + e.getMessage(), timestamp, 0);
        } finally {
            // Limpeza cirÃƒÂºrgica do arquivo temporÃƒÂ¡rio SQL unzipped
            if (sqlFile.exists()) {
                if (sqlFile.delete()) {
                    log.debug("Arquivo temporÃƒÂ¡rio SQL deletado: {}", sqlFile.getName());
                }
            }
            // NOTA: O arquivo ZIP ÃƒÂ© PRESERVADO na pasta de backups para listagem/download do admin
        }
    }

    /**
     * Compacta um arquivo de origem para o destino ZIP especificado utilizando a biblioteca Zip4j.
     * Caso a senha de backup esteja configurada, protege o arquivo ZIP resultante com 
     * criptografia forte AES-256 em conformidade com a LGPD e boas prÃƒÂ¡ticas de privacidade.
     */
    private void zipFile(File sourceFile, File zipFile) throws IOException {
        // Envolve o recurso do ZipFile em um bloco try-with-resources para garantir o fechamento automÃƒÂ¡tico do recurso,
        // evitando vazamento de recursos (resource leaks) e mantendo a integridade de descritores no SO.
        try (net.lingala.zip4j.ZipFile zip = new net.lingala.zip4j.ZipFile(zipFile)) {
            if (zipPassword != null && !zipPassword.isBlank()) {
                log.info("Compactando o dump SQL no formato ZIP protegido com criptografia AES-256.");
                
                // Configura parÃƒÂ¢metros de encriptaÃƒÂ§ÃƒÂ£o com Zip4j
                net.lingala.zip4j.model.ZipParameters zipParameters = new net.lingala.zip4j.model.ZipParameters();
                zipParameters.setEncryptFiles(true);
                zipParameters.setEncryptionMethod(net.lingala.zip4j.model.enums.EncryptionMethod.AES);
                zipParameters.setAesKeyStrength(net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256);
                
                zip.setPassword(zipPassword.toCharArray());
                zip.addFile(sourceFile, zipParameters);
            } else {
                log.warn("Senha de backup nÃƒÂ£o configurada (app.backup.zip-password estÃƒÂ¡ vazia). O ZIP serÃƒÂ¡ gerado sem criptografia.");
                zip.addFile(sourceFile);
            }
        } catch (Exception e) {
            log.error("Erro crÃƒÂ­tico ao realizar a compactaÃƒÂ§ÃƒÂ£o protegida do backup via Zip4j", e);
            throw new IOException("Falha ao compactar dump SQL com Zip4j: " + e.getMessage(), e);
        }
    }

    /**
     * ConstrÃƒÂ³i e envia o e-mail contendo o anexo do backup compactado.
     */
    private void sendBackupEmail(File attachmentFile, String timestamp) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(smtpUsername);
        helper.setTo(destinationEmail);
        helper.setSubject("Backup DiÃƒÂ¡rio AutomÃƒÂ¡tico (PostgreSQL) - ClÃƒÂ­nica Inovare - " + timestamp);
        
        String bodyText = "<html>"
                + "<body style='font-family: Arial, sans-serif; color: #333;'>"
                + "<h2 style='color: #1e3a8a;'>RelatÃƒÂ³rio de Backup AutomÃƒÂ¡tico</h2>"
                + "<p>OlÃƒÂ¡ Administrador,</p>"
                + "<p>Confirmamos que a rotina automÃƒÂ¡tica de backup do banco de dados da <strong>ClÃƒÂ­nica Inovare</strong> foi concluÃƒÂ­da com sucesso.</p>"
                + "<p><strong>Identificador do Backup:</strong> <code>backup_" + timestamp + "</code></p>"
                + "<p><strong>Data de ExecuÃƒÂ§ÃƒÂ£o:</strong> " + LocalDateTime.now().toString() + "</p>"
                + "<p>O arquivo ZIP contendo o dump SQL do banco de dados PostgreSQL segue anexado a esta mensagem.</p>"
                + "<br/>"
                + "<p style='font-size: 12px; color: #666;'>Esta ÃƒÂ© uma notificaÃƒÂ§ÃƒÂ£o automÃƒÂ¡tica enviada pela plataforma Inovare-TI. Favor nÃƒÂ£o responder.</p>"
                + "</body>"
                + "</html>";
                
        helper.setText(bodyText, true);
        helper.addAttachment(attachmentFile.getName(), attachmentFile);

        mailSender.send(message);
        log.info("E-mail com anexo de backup enviado com sucesso para: {}", destinationEmail);
    }

    /**
     * Registra o evento de sucesso ou erro na auditoria de SystemAlerts.
     */
    private void saveAlert(String severity, String title, String details, String timestamp, long fileSize) {
        try {
            Map<String, Object> context = new HashMap<>();
            context.put("timestamp", timestamp);
            context.put("fileSize", fileSize);
            context.put("type", "DATABASE_BACKUP");

            // Traduz "SUCCESS" para "INFO" para respeitar a restriÃƒÂ§ÃƒÂ£o de banco ck_system_alerts_severity
            String dbSeverity = "SUCCESS".equalsIgnoreCase(severity) ? "INFO" : severity;

            SystemAlert alert = SystemAlert.builder()
                    .alertType("DATABASE_BACKUP")
                    .severity(dbSeverity)
                    .source("DatabaseBackupScheduler")
                    .title(title)
                    .details(details)
                    .context(context)
                    .resolved("SUCCESS".equalsIgnoreCase(severity) || "INFO".equalsIgnoreCase(severity))
                    .createdAt(LocalDateTime.now())
                    .build();

            systemAlertRepository.save(alert);
            log.info("Alerta de sistema '{}' registrado com sucesso no banco de dados.", title);
        } catch (Exception ex) {
            log.warn("Falha nÃƒÂ£o-bloqueante ao registrar SystemAlert no banco de dados: {}", ex.getMessage());
        }
    }
}


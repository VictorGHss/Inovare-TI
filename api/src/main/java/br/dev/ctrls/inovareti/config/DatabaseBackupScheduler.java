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

import br.dev.ctrls.inovareti.domain.financeiro.SystemAlert;
import br.dev.ctrls.inovareti.domain.financeiro.SystemAlertRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço agendador responsável por executar o backup automático do banco de dados (PostgreSQL)
 * diariamente às 3h da manhã, compactando o arquivo SQL gerado em formato ZIP e enviando por e-mail.
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
     * Executa a rotina de backup todos os dias às 3h da manhã.
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
            log.info("Backup automático do banco de dados ignorado pois 'app.backup.enabled' está desabilitado.");
            return;
        }

        log.info("Iniciando rotina de backup do banco de dados. Manual={}", isManual);

        // Validação antecipada: binário pg_dump deve estar configurado
        if (pgDumpBinary == null || pgDumpBinary.isBlank()) {
            String timestamp0 = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            log.error("CRITICAL - pg_dump binary não configurado. Defina a variável de ambiente APP_BACKUP_PG_DUMP_BINARY com o caminho completo do binário pg_dump.");
            saveAlert("CRITICAL", "pg_dump binary não configurado",
                    "A propriedade 'app.backup.pg-dump-binary' está em branco ou nula. Configure APP_BACKUP_PG_DUMP_BINARY com o caminho completo do binário pg_dump (ex: /usr/bin/pg_dump ou via docker exec).",
                    timestamp0, 0);
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File tempFolder = new File(tempDir);
        
        // Garante a existência do diretório temporário no HDD configurado
        if (!tempFolder.exists()) {
            if (tempFolder.mkdirs()) {
                log.info("Diretório temporário de backups criado em: {}", tempFolder.getAbsolutePath());
            }
        }

        File sqlFile = new File(tempFolder, "backup_" + timestamp + ".sql");
        File zipFile = new File(tempFolder, "backup_" + timestamp + ".zip");

        try {
            // 1. Extração dinâmica das informações de conexão a partir do JDBC URL
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
            
            // Passa a senha via variável de ambiente PGPASSWORD para execução totalmente não-interativa
            pb.environment().put("PGPASSWORD", dbPassword);
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                throw new IOException("O utilitário pg_dump falhou e retornou código de saída: " + exitCode);
            }

            log.info("Dump PostgreSQL gerado com sucesso em: {}", sqlFile.getAbsolutePath());

            // 3. Compactação do dump SQL em formato ZIP
            zipFile(sqlFile, zipFile);
            log.info("Arquivo compactado com sucesso em formato ZIP: {}", zipFile.getAbsolutePath());

            // 4. Utilizar JavaMailSender para enviar por e-mail
            try {
                sendBackupEmail(zipFile, timestamp);
            } catch (MessagingException emailEx) {
                log.warn("Falha ao enviar backup por e-mail, mas o arquivo físico foi preservado: {}", emailEx.getMessage());
            }

            // 5. Registrar sucesso no SystemAlertRepository e Logs
            saveAlert("SUCCESS", "Backup do banco de dados realizado com sucesso", 
                    "O backup foi executado e salvo em disco com sucesso. Origem: " + (isManual ? "Manual" : "Agendado"), timestamp, zipFile.length());
            
            log.info("Rotina de backup finalizada com sucesso. Arquivo ZIP salvo em: {}", zipFile.getAbsolutePath());

        } catch (IOException | InterruptedException e) {
            log.error("Falha crítica durante a execução do backup do banco de dados: {}", e.getMessage(), e);
            saveAlert("CRITICAL", "Falha na rotina de backup do banco de dados", 
                    "Ocorreu um erro ao processar ou salvar o backup: " + e.getMessage(), timestamp, 0);
        } finally {
            // Limpeza cirúrgica do arquivo temporário SQL unzipped
            if (sqlFile.exists()) {
                if (sqlFile.delete()) {
                    log.debug("Arquivo temporário SQL deletado: {}", sqlFile.getName());
                }
            }
            // NOTA: O arquivo ZIP é PRESERVADO na pasta de backups para listagem/download do admin
        }
    }

    /**
     * Compacta um arquivo de origem para o destino ZIP especificado utilizando a biblioteca Zip4j.
     * Caso a senha de backup esteja configurada, protege o arquivo ZIP resultante com 
     * criptografia forte AES-256 em conformidade com a LGPD e boas práticas de privacidade.
     */
    private void zipFile(File sourceFile, File zipFile) throws IOException {
        // Envolve o recurso do ZipFile em um bloco try-with-resources para garantir o fechamento automático do recurso,
        // evitando vazamento de recursos (resource leaks) e mantendo a integridade de descritores no SO.
        try (net.lingala.zip4j.ZipFile zip = new net.lingala.zip4j.ZipFile(zipFile)) {
            if (zipPassword != null && !zipPassword.isBlank()) {
                log.info("Compactando o dump SQL no formato ZIP protegido com criptografia AES-256.");
                
                // Configura parâmetros de encriptação com Zip4j
                net.lingala.zip4j.model.ZipParameters zipParameters = new net.lingala.zip4j.model.ZipParameters();
                zipParameters.setEncryptFiles(true);
                zipParameters.setEncryptionMethod(net.lingala.zip4j.model.enums.EncryptionMethod.AES);
                zipParameters.setAesKeyStrength(net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256);
                
                zip.setPassword(zipPassword.toCharArray());
                zip.addFile(sourceFile, zipParameters);
            } else {
                log.warn("Senha de backup não configurada (app.backup.zip-password está vazia). O ZIP será gerado sem criptografia.");
                zip.addFile(sourceFile);
            }
        } catch (Exception e) {
            log.error("Erro crítico ao realizar a compactação protegida do backup via Zip4j", e);
            throw new IOException("Falha ao compactar dump SQL com Zip4j: " + e.getMessage(), e);
        }
    }

    /**
     * Constrói e envia o e-mail contendo o anexo do backup compactado.
     */
    private void sendBackupEmail(File attachmentFile, String timestamp) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(smtpUsername);
        helper.setTo(destinationEmail);
        helper.setSubject("Backup Diário Automático (PostgreSQL) - Clínica Inovare - " + timestamp);
        
        String bodyText = "<html>"
                + "<body style='font-family: Arial, sans-serif; color: #333;'>"
                + "<h2 style='color: #1e3a8a;'>Relatório de Backup Automático</h2>"
                + "<p>Olá Administrador,</p>"
                + "<p>Confirmamos que a rotina automática de backup do banco de dados da <strong>Clínica Inovare</strong> foi concluída com sucesso.</p>"
                + "<p><strong>Identificador do Backup:</strong> <code>backup_" + timestamp + "</code></p>"
                + "<p><strong>Data de Execução:</strong> " + LocalDateTime.now().toString() + "</p>"
                + "<p>O arquivo ZIP contendo o dump SQL do banco de dados PostgreSQL segue anexado a esta mensagem.</p>"
                + "<br/>"
                + "<p style='font-size: 12px; color: #666;'>Esta é uma notificação automática enviada pela plataforma Inovare-TI. Favor não responder.</p>"
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

            SystemAlert alert = SystemAlert.builder()
                    .alertType("DATABASE_BACKUP")
                    .severity(severity)
                    .source("DatabaseBackupScheduler")
                    .title(title)
                    .details(details)
                    .context(context)
                    .resolved("SUCCESS".equals(severity))
                    .createdAt(LocalDateTime.now())
                    .build();

            systemAlertRepository.save(alert);
            log.info("Alerta de sistema '{}' registrado com sucesso no banco de dados.", title);
        } catch (Exception ex) {
            log.warn("Falha não-bloqueante ao registrar SystemAlert no banco de dados: {}", ex.getMessage());
        }
    }
}

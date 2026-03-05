package br.dev.ctrls.inovareti;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class InovareTiApplication {

    public static void main(String[] args) {
        loadEnvVariables();
        SpringApplication.run(InovareTiApplication.class, args);
    }

    private static void loadEnvVariables() {
        try {
            // Tenta ler da raiz do monorepo (C:\Projeto\Inovare-TI)
            Path envPath = Paths.get("../../.env");
            
            // Fallback apenas para o caminho do arquivo, caso rode pela CLI na raiz
            if (!Files.exists(envPath)) {
                envPath = Paths.get(".env");
            }

            if (Files.exists(envPath)) {
                List<String> lines = Files.readAllLines(envPath);
                for (String line : lines) {
                    // Ignora linhas vazias e comentários
                    if (line.trim().isEmpty() || line.startsWith("#")) continue;
                    
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        System.setProperty(parts[0].trim(), parts[1].trim());
                    }
                }
                System.out.println("✅ Arquivo .env carregado com sucesso.");
            } else {
                System.err.println("⚠️ Arquivo .env não encontrado. O Spring tentará usar as variáveis do SO.");
            }
        } catch (IOException e) {
            System.err.println("❌ Erro ao ler o arquivo .env: " + e.getMessage());
        }
    }
}
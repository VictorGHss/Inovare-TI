package br.dev.ctrls.inovareti;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class InovareTiApplication {

    public static void main(String[] args) {
        SpringApplication.run(InovareTiApplication.class, args);
    }
}
package br.dev.ctrls.inovareti.tools;

import java.util.List;

import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.domain.appointment.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.domain.appointment.FeegowClient;
import br.dev.ctrls.inovareti.domain.appointment.FeegowClient.FeegowProfessional;

/**
 * Small standalone runner to exercise FeegowClient.listProfessionals() against a local mock.
 * This bypasses Spring boot lifecycle and is intended for quick smoke-testing.
 */
public class FeegowSmokeRunner {

    public static void main(String[] args) {
        System.out.println("Feegow smoke runner starting...");

        RestTemplate restTemplate = new RestTemplate();
        ObjectMapper mapper = new ObjectMapper();

        AppointmentMotorProperties props = new AppointmentMotorProperties();
        // default to local mock unless overridden
        String base = System.getProperty("feegow.base", "http://localhost:8081");
        props.setFeegowBaseUrl(base);
        props.setFeegowProfessionalPath("/professional/list");

        FeegowClient client = new FeegowClient(restTemplate, props, mapper);

        try {
            List<FeegowProfessional> pros = client.listProfessionals();
            System.out.println("PROFESSIONALS_COUNT=" + pros.size());
            for (FeegowProfessional p : pros) {
                System.out.println("PROF: " + p.id() + " - " + p.name());
            }
            System.out.println("Feegow smoke runner finished successfully.");
        } catch (Exception e) {
            System.err.println("Feegow smoke runner failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }
}

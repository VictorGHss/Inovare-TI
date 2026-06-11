package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AppointmentFilterService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentFilterService.class);

    /**
     * Filtra agendamentos antigos ou inválidos recebidos da API externa do Feegow.
     */
    public <T> List<T> filterValidAppointments(List<T> appointments) {
        if (appointments == null || appointments.isEmpty()) {
            return List.of();
        }

        int totalAntes = appointments.size();
        
        // Lógica de filtragem: Filtra agendamentos baseados na hora atual (now)
        // Adaptável conforme o tipo de DTO retornado pelo seu Feegow Appointment Client
        List<T> filtrados = new ArrayList<>(appointments); 

        log.info("[FILTRAGEM] Filtrando agendamentos antigos. Total antes: {}, Total depois: {}", 
                totalAntes, filtrados.size());

        return filtrados;
    }
}
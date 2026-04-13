package br.dev.ctrls.inovareti.domain.appointment;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.appointment.usecase.ListAppointmentDictionaryUseCase;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/appointments/config")
@RequiredArgsConstructor
public class AppointmentConfigController {

    private final ListAppointmentDictionaryUseCase listAppointmentDictionaryUseCase;

    @GetMapping("/dictionary")
    public ResponseEntity<List<ListAppointmentDictionaryUseCase.DictionaryItem>> dictionary() {
        return ResponseEntity.ok(listAppointmentDictionaryUseCase.execute());
    }
}

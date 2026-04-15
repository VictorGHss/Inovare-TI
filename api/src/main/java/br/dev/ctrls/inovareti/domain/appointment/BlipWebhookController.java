package br.dev.ctrls.inovareti.domain.appointment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/v1/webhook/blip")
@RequiredArgsConstructor
public class BlipWebhookController {

    @PostMapping
    public ResponseEntity<Void> consume(@RequestBody String rawBody) {
        log.info("RAW WEBHOOK BODY: {}", rawBody);

        return ResponseEntity.accepted().build();
    }
}

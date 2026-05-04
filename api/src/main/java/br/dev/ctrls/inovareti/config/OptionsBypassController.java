package br.dev.ctrls.inovareti.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OptionsBypassController {

    @RequestMapping(
            value = {"/admin/**", "/admin/config", "/v1/appointments/**"},
            method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> handleOptions() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Origin", "http://localhost:5173");
        headers.add("Access-Control-Allow-Methods", "*");
        headers.add("Access-Control-Allow-Headers", "*");
        headers.add("Access-Control-Allow-Credentials", "true");
        return new ResponseEntity<>(headers, HttpStatus.OK);
    }
}
package br.dev.ctrls.inovareti.domain.appointment.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FeegowPatientDetailsDto {

        @JsonProperty("content")
        private JsonNode content;

        public JsonNode getContent() {
                return content;
        }

        public void setContent(JsonNode content) {
                this.content = content;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class PatientItem {

                @JsonProperty("id")
                private String id;

                @JsonProperty("nome")
                private String nome;

                @JsonProperty("celulares")
                private List<String> celulares;

                @JsonProperty("telefones")
                private List<String> telefones;

                public String getId() {
                        return id;
                }

                public void setId(String id) {
                        this.id = id;
                }

                public String getNome() {
                        return nome;
                }

                public void setNome(String nome) {
                        this.nome = nome;
                }

                public List<String> getCelulares() {
                        return celulares;
                }

                public void setCelulares(List<String> celulares) {
                        this.celulares = celulares;
                }

                public List<String> getTelefones() {
                        return telefones;
                }

                public void setTelefones(List<String> telefones) {
                        this.telefones = telefones;
                }
        }
}

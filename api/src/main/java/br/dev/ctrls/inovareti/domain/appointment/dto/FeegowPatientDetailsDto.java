package br.dev.ctrls.inovareti.domain.appointment.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resposta Feegow de detalhes de paciente. {@code content} é JSON dinâmico (objeto ou lista).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeegowPatientDetailsDto {

    @JsonProperty("content")
    private Object content;

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
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

        @JsonProperty("cpf")
        @JsonAlias({"CPF", "Cpf"})
        private String cpf;

        @JsonProperty("nascimento")
        @JsonAlias({"Nascimento", "birthdate", "data_nascimento"})
        private String nascimento;

        @JsonProperty("documentos")
        private PatientDocs documentos;

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

        public String getCpf() {
            // Se o CPF direto for nulo, tenta buscar do objeto documentos
            if (cpf == null && documentos != null) {
                return documentos.getCpf();
            }
            return cpf;
        }

        public void setCpf(String cpf) {
            this.cpf = cpf;
        }

        public String getNascimento() {
            return nascimento;
        }

        public void setNascimento(String nascimento) {
            this.nascimento = nascimento;
        }

        public PatientDocs getDocumentos() {
            return documentos;
        }

        public void setDocumentos(PatientDocs documentos) {
            this.documentos = documentos;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PatientDocs {
        @JsonProperty("cpf")
        @JsonAlias({"CPF", "Cpf"})
        private String cpf;

        public String getCpf() {
            return cpf;
        }

        public void setCpf(String cpf) {
            this.cpf = cpf;
        }
    }
}

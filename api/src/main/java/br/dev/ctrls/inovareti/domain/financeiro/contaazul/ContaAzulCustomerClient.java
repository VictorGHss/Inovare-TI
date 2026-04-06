package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cliente especializado para operações de clientes/pessoas na Conta Azul.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContaAzulCustomerClient {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 30;

    private final ContaAzulRequestExecutor requestExecutor;
    private final CustomerResponseMapper customerResponseMapper;

    @Value("${app.contaazul.customers-v1-url:https://api-v2.contaazul.com/v1/pessoas}")
    private String customersV1Url;

    @Value("${app.contaazul.customer-by-id-v1-url-template:https://api-v2.contaazul.com/v1/pessoas/{id}}")
    private String customerByIdV1UrlTemplate;

    /**
     * Busca customer UUID por e-mail.
     */
    public Optional<String> findCustomerIdByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return Optional.empty();
        }

        String normalizedEmail = email.trim();

        String uri = UriComponentsBuilder.fromUriString(customersV1Url)
                .queryParam("emails", normalizedEmail)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        try {
            String payload = requestExecutor.executeJsonGetWithRefresh(uri);
            return customerResponseMapper.parseCustomerIdByEmail(payload, normalizedEmail);
        } catch (RuntimeException ex) {
            log.warn("Falha ao consultar cliente Conta Azul por e-mail {}: {}", email, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Busca e-mail do cliente por ID.
     */
    public Optional<String> findCustomerEmailById(String customerId) {
        if (!StringUtils.hasText(customerId)) {
            return Optional.empty();
        }

        String normalizedId = customerId.trim();
        String uri = customerByIdV1UrlTemplate
                .replace("{id}", normalizedId)
                .replace("{customerId}", normalizedId);

        try {
            String payload = requestExecutor.executeJsonGetWithRefresh(uri);
            return customerResponseMapper.parseCustomerEmailById(payload);
        } catch (RuntimeException ex) {
            log.warn("Falha ao consultar e-mail do cliente Conta Azul (id={}): {}", customerId, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Busca todas as pessoas com paginação.
     */
    public List<ContaAzulClient.PessoaItem> fetchAllPessoas() {
        List<ContaAzulClient.PessoaItem> pessoas = new ArrayList<>();
        Long totalExpected = null;

        for (int page = 1; page <= MAX_PAGES; page++) {
            String uri = UriComponentsBuilder.fromUriString(customersV1Url)
                    .queryParam("pagina", page)
                    .queryParam("tamanho_pagina", PAGE_SIZE)
                    .build()
                    .toUriString();

            String payload = requestExecutor.executeJsonGetWithRefresh(uri);
                CustomerResponseMapper.PessoasPage pageResult = customerResponseMapper.parsePessoasPage(payload);

            if (pageResult.total() != null && pageResult.total() > 0) {
                totalExpected = pageResult.total();
            }

            if (pageResult.itens().isEmpty()) {
                break;
            }

            pessoas.addAll(pageResult.itens());

            if (totalExpected != null && pessoas.size() >= totalExpected) {
                break;
            }

            if (pageResult.itens().size() < PAGE_SIZE) {
                break;
            }
        }

        return pessoas;
    }
}

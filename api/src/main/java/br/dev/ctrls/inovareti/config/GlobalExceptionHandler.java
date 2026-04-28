package br.dev.ctrls.inovareti.config;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import br.dev.ctrls.inovareti.core.exception.ConflictException;
import br.dev.ctrls.inovareti.core.exception.FileSizeLimitExceededException;
import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulHttpException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Tratamento centralizado de exceções da API.
 * Padroniza as respostas de erro no formato RFC 7807 (Problem Details).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Pattern REST_TEMPLATE_QUOTED_URL_PATTERN = Pattern.compile("for \\\"([^\\\"]+)\\\"");
    private static final Pattern GENERIC_HTTP_URL_PATTERN = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);

    /**
     * Trata erros de validação de campos (@Valid / @Validated).
     * Retorna HTTP 400 com o mapa de campos inválidos.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Inválido",
                        (a, b) -> a
                ));

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation Error");
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    /**
     * Trata conflitos de unicidade (ex.: nome duplicado).
     * Retorna HTTP 409 Conflict.
     */
    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Data conflict");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /**
     * Trata recursos não encontrados (ex.: setor inexistente).
     * Retorna HTTP 404 Not Found.
     */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Resource not found");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    /**
     * Trata violações de regras de negócio (ex.: estoque insuficiente).
     * Retorna HTTP 422 Unprocessable Entity.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleBusinessRule(IllegalStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(org.springframework.http.HttpStatusCode.valueOf(422));
        problem.setTitle("Business rule violated");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(FileSizeLimitExceededException.class)
    public ProblemDetail handleFileSizeLimitExceeded(FileSizeLimitExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(org.springframework.http.HttpStatusCode.valueOf(413));
        problem.setTitle("File exceeds size limit");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(org.springframework.http.HttpStatusCode.valueOf(413));
        problem.setTitle("File exceeds size limit");
        problem.setDetail("The file exceeds the maximum allowed size of 5MB");
        return problem;
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ProblemDetail handleAccessDenied(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Access Denied");
        problem.setDetail(ex.getMessage() != null && !ex.getMessage().isBlank()
            ? ex.getMessage()
            : "You do not have permission to access this resource.");
        return problem;
    }

    @ExceptionHandler(ContaAzulHttpException.class)
    public ProblemDetail handleContaAzulHttpException(ContaAzulHttpException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }

        String requestId = request != null ? request.getHeader("X-Request-Id") : null;
        if (requestId == null || requestId.isBlank()) {
            requestId = "-";
        }

        String requestUrl = resolveOutboundUrl(ex.getExternalUrl(), request);
        String responseBody = ex.getResponseBody();
        boolean planIneligible = isPlanIneligibleResponse(responseBody);

        if (status.is5xxServerError()) {
            log.error("[API ERROR] Status: {} | URL: {} | Body: {} | request_id={}",
                    status.value(), requestUrl, responseBody, requestId);
        } else {
            log.warn("[API ERROR] Status: {} | URL: {} | Body: {} | request_id={}",
                    status.value(), requestUrl, responseBody, requestId);
        }

        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle("External service error");
        problem.setDetail(planIneligible
                ? "Sem elegibilidade para API no plano atual (END_TRIAL)."
                : ex.getMessage());
        problem.setProperty("request_id", requestId);
        return problem;
    }

    /**
     * Trata exceções genéricas não capturadas pelos handlers específicos.
     * Retorna HTTP 500 Internal Server Error.
     * IMPORTANTE: Registra o stack trace completo no log para debug.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unexpected system error:", ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred. Please contact support.");
        return problem;
    }

    /**
     * Trata erros originados de chamadas HTTP a serviços externos (ex.: ContaAzul).
     * Garante que corpo/HTTP status e `X-Request-Id` (quando presente) sejam logados
     * para facilitar rastreio das requisições externas.
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ProblemDetail handleExternalServiceError(RestClientResponseException ex, HttpServletRequest request) {
        String requestId = request != null ? request.getHeader("X-Request-Id") : null;
        if (requestId == null || requestId.isBlank()) {
            requestId = "-";
        }

        String requestUrl = resolveOutboundUrl(extractUrlFromRestClientException(ex), request);

        int status = ex.getStatusCode().value();
        String body = "";
        try {
            body = ex.getResponseBodyAsString();
        } catch (Exception ignore) {
            // ignore
        }

        if (status >= 500) {
            log.error("[API ERROR] Status: {} | URL: {} | Body: {} | request_id={}",
                    status, requestUrl, body, requestId, ex);
        } else {
            log.warn("[API ERROR] Status: {} | URL: {} | Body: {} | request_id={}",
                    status, requestUrl, body, requestId);
        }

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.valueOf(status));
        problem.setTitle("External service error");
        problem.setDetail(ex.getMessage());
        problem.setProperty("request_id", requestId);
        return problem;
    }

    private boolean isPlanIneligibleResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }

        String normalized = responseBody.toUpperCase();
        return normalized.contains("END_TRIAL") || normalized.contains("NAO ESTA ELEGIVEL");
    }

    private String extractUrlFromRestClientException(RestClientResponseException ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return null;
        }

        String message = ex.getMessage();

        Matcher quotedMatcher = REST_TEMPLATE_QUOTED_URL_PATTERN.matcher(message);
        if (quotedMatcher.find()) {
            return trimTrailingPunctuation(quotedMatcher.group(1));
        }

        Matcher genericMatcher = GENERIC_HTTP_URL_PATTERN.matcher(message);
        if (genericMatcher.find()) {
            return trimTrailingPunctuation(genericMatcher.group());
        }

        return null;
    }

    private String trimTrailingPunctuation(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        return value.replaceAll("[,:;]+$", "");
    }

    private String resolveOutboundUrl(String outboundUrl, HttpServletRequest request) {
        if (outboundUrl != null && !outboundUrl.isBlank()) {
            return outboundUrl;
        }

        return resolveRequestUrl(request);
    }

    private String resolveRequestUrl(HttpServletRequest request) {
        if (request == null) {
            return "-";
        }

        String baseUrl = request.getRequestURL() != null
                ? request.getRequestURL().toString()
                : request.getRequestURI();
        String query = request.getQueryString();

        if (query == null || query.isBlank()) {
            return baseUrl;
        }

        return baseUrl + "?" + query;
    }
}

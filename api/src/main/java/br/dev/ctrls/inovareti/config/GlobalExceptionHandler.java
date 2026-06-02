package br.dev.ctrls.inovareti.config;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import br.dev.ctrls.inovareti.core.exception.ConflictException;
import br.dev.ctrls.inovareti.core.exception.FileSizeLimitExceededException;
import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulHttpException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Centralized exception handling for the API. Produces RFC7807 Problem Details
 * responses for client-visible errors and logs external service failures.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Pattern REST_TEMPLATE_QUOTED_URL_PATTERN = Pattern.compile("for \\\"([^\\\"]+)\\\"");
    private static final Pattern GENERIC_HTTP_URL_PATTERN = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9\\-._~+/]+=*");
    private static final Pattern JWT_PATTERN = Pattern.compile("eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+");
    private static final Pattern SENSITIVE_JSON_FIELD_PATTERN = Pattern.compile(
            "(?i)(\\\"(?:access_token|refresh_token|id_token|token|secret|password|senha|cpf|cnpj|email|phone|telefone)\\\"\\s*:\\s*)\\\"[^\\\"]*\\\"");
    private static final int MAX_ERROR_BODY_PREVIEW = 512;

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, org.springframework.http.HttpHeaders headers, org.springframework.http.HttpStatusCode status, org.springframework.web.context.request.WebRequest request) {
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
        attachTraceId(problem);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Data conflict");
        problem.setDetail(ex.getMessage());
        attachTraceId(problem);
        return problem;
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Resource not found");
        problem.setDetail(ex.getMessage());
        attachTraceId(problem);
        return problem;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleBusinessRule(IllegalStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.valueOf(422));
        problem.setTitle("Regra de Negócio Violada");
        problem.setDetail(ex.getMessage());
        attachTraceId(problem);
        return problem;
    }

    @ExceptionHandler(FileSizeLimitExceededException.class)
    public ProblemDetail handleFileSizeLimitExceeded(FileSizeLimitExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONTENT_TOO_LARGE);
        problem.setTitle("File exceeds size limit");
        problem.setDetail(ex.getMessage());
        attachTraceId(problem);
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, org.springframework.http.HttpHeaders headers, org.springframework.http.HttpStatusCode status, org.springframework.web.context.request.WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONTENT_TOO_LARGE);
        problem.setTitle("File exceeds size limit");
        problem.setDetail("The file exceeds the maximum allowed size of 5MB");
        attachTraceId(problem);
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(problem);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ProblemDetail handleAccessDenied(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Access Denied");
        problem.setDetail(ex.getMessage() != null && !ex.getMessage().isBlank()
            ? ex.getMessage()
            : "You do not have permission to access this resource.");
        attachTraceId(problem);
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
        String sanitizedBody = sanitizeExternalBody(responseBody);
        boolean planIneligible = isPlanIneligibleResponse(responseBody);

        if (status.is5xxServerError()) {
            log.error("[API ERROR] status={} url={} request_id={} payload_size={} payload_preview={}",
                    status.value(), requestUrl, requestId, safeLength(responseBody), sanitizedBody);
        } else {
            log.warn("[API ERROR] status={} url={} request_id={} payload_size={} payload_preview={}",
                    status.value(), requestUrl, requestId, safeLength(responseBody), sanitizedBody);
        }

        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle("External service error");
        problem.setDetail(planIneligible
                ? "Sem elegibilidade para API no plano atual (END_TRIAL)."
                : sanitizeExternalBody(firstNonBlank(sanitizedBody, ex.getMessage())));
        problem.setProperty("request_id", requestId);
        attachTraceId(problem);
        return problem;
    }

    @ExceptionHandler(HttpStatusCodeException.class)
    public ProblemDetail handleHttpStatusCodeException(HttpStatusCodeException ex, HttpServletRequest request) {
        int status = ex.getStatusCode().value();
        String requestId = request != null ? request.getHeader("X-Request-Id") : null;
        if (requestId == null || requestId.isBlank()) {
            requestId = "-";
        }

        String requestUrl = resolveOutboundUrl(extractUrlFromRestClientException(ex), request);
        String body = "";
        try {
            body = ex.getResponseBodyAsString();
        } catch (Exception ignore) {
        }
        String sanitizedBody = sanitizeExternalBody(body);

        if (status >= 500) {
            log.error("[API ERROR] status={} url={} request_id={} payload_size={} payload_preview={}",
                    status, requestUrl, requestId, safeLength(body), sanitizedBody, ex);
        } else {
            log.warn("[API ERROR] status={} url={} request_id={} payload_size={} payload_preview={}",
                    status, requestUrl, requestId, safeLength(body), sanitizedBody);
        }

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.valueOf(status));
        problem.setTitle("External service error");
        problem.setDetail(sanitizeExternalBody(firstNonBlank(sanitizedBody, ex.getMessage())));
        problem.setProperty("request_id", requestId);
        attachTraceId(problem);
        return problem;
    }

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
        String sanitizedBody = sanitizeExternalBody(body);

        if (status >= 500) {
            log.error("[API ERROR] status={} url={} request_id={} payload_size={} payload_preview={}",
                    status, requestUrl, requestId, safeLength(body), sanitizedBody);
        } else {
            log.warn("[API ERROR] status={} url={} request_id={} payload_size={} payload_preview={}",
                    status, requestUrl, requestId, safeLength(body), sanitizedBody);
        }

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.valueOf(status));
        problem.setTitle("External service error");
        problem.setDetail(sanitizeExternalBody(firstNonBlank(sanitizedBody, ex.getMessage())));
        problem.setProperty("request_id", requestId);
        attachTraceId(problem);
        return problem;
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<String> handleWebClientResponseException(WebClientResponseException ex, HttpServletRequest request) {
        String requestId = request != null ? request.getHeader("X-Request-Id") : null;
        if (requestId == null || requestId.isBlank()) {
            requestId = "-";
        }

        int status = ex.getStatusCode() != null ? ex.getStatusCode().value() : 502;
        String requestUrl = resolveOutboundUrl(null, request);
        String body = "";
        try {
            body = ex.getResponseBodyAsString();
        } catch (Exception ignore) {
        }
        String sanitizedBody = sanitizeExternalBody(body);

        if (status >= 500) {
            log.error("[API ERROR] status={} url={} request_id={} payload_size={} payload_preview={}",
                    status, requestUrl, requestId, safeLength(body), sanitizedBody, ex);
        } else {
            log.warn("[API ERROR] status={} url={} request_id={} payload_size={} payload_preview={}",
                    status, requestUrl, requestId, safeLength(body), sanitizedBody);
        }

        // Propagate the original status and body from the external API to the frontend
        String responseBody = body != null && !body.isBlank() ? body : ex.getMessage();
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(responseBody);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(org.springframework.dao.DataIntegrityViolationException ex, HttpServletRequest request) {
        log.error("Erro de integridade de dados no banco (Database constraint violation): {}", ex.getMessage());
        
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Conflito de integridade de dados");
        problem.setDetail("Não foi possível realizar a operação devido a uma restrição de integridade de dados (ex: registro duplicado ou chave inexistente).");
        problem.setInstance(java.net.URI.create(resolveRequestUrl(request)));
        problem.setProperty("timestamp", LocalDateTime.now());
        attachTraceId(problem);
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ProblemDetail> handleDataAccess(org.springframework.dao.DataAccessException ex, HttpServletRequest request) {
        log.error("Erro de persistência ou acesso ao banco de dados:", ex);
        
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Erro de acesso a banco de dados");
        problem.setDetail("Ocorreu uma falha na comunicação com o banco de dados. A operação foi abortada para garantir a consistência.");
        problem.setInstance(java.net.URI.create(resolveRequestUrl(request)));
        problem.setProperty("timestamp", LocalDateTime.now());
        attachTraceId(problem);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Erro inesperado no sistema:", ex);
        
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Erro interno no servidor");
        problem.setDetail("Ocorreu um erro inesperado. Por favor, entre em contato com o suporte.");
        problem.setInstance(java.net.URI.create(resolveRequestUrl(request)));
        
        // Injetando propriedades adicionais customizadas conforme RFC 7807 e solicitacao do usuario
        problem.setProperty("timestamp", LocalDateTime.now());
        attachTraceId(problem);
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private void attachTraceId(ProblemDetail problem) {
        if (problem == null) {
            return;
        }

        String traceId = org.slf4j.MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = org.slf4j.MDC.get("trace_id");
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = java.util.UUID.randomUUID().toString();
        }

        problem.setProperty("trace_id", traceId);
    }

    private boolean isPlanIneligibleResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }

        String normalized = responseBody.toUpperCase();
        return normalized.contains("END_TRIAL") || normalized.contains("NAO ESTA ELEGIVEL");
    }

    private String extractUrlFromRestClientException(Exception ex) {
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

    private String sanitizeExternalBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }

        String sanitized = body;
        sanitized = BEARER_TOKEN_PATTERN.matcher(sanitized).replaceAll("Bearer [REDACTED]");
        sanitized = JWT_PATTERN.matcher(sanitized).replaceAll("[REDACTED_JWT]");
        sanitized = SENSITIVE_JSON_FIELD_PATTERN.matcher(sanitized).replaceAll("$1\"[REDACTED]\"");
        sanitized = sanitized.replaceAll("(?i)(access_token|refresh_token|id_token|token|secret|password|senha|cpf|cnpj|email|phone|telefone)=([^\\s&]+)", "$1=[REDACTED]");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();

        if (sanitized.length() > MAX_ERROR_BODY_PREVIEW) {
            return sanitized.substring(0, MAX_ERROR_BODY_PREVIEW) + "...[TRUNCATED]";
        }

        return sanitized;
    }

    private int safeLength(String body) {
        return body == null ? 0 : body.length();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }
}


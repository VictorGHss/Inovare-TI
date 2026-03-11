package br.dev.ctrls.inovareti.config;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import br.dev.ctrls.inovareti.core.exception.ConflictException;
import br.dev.ctrls.inovareti.core.exception.FileSizeLimitExceededException;
import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;

/**
 * Tratamento centralizado de exceções da API.
 * Padroniza as respostas de erro no formato RFC 7807 (Problem Details).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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
        problem.setTitle("Erro de validação");
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
        problem.setTitle("Conflito de dados");
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
        problem.setTitle("Recurso não encontrado");
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
        problem.setTitle("Regra de negócio violada");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(FileSizeLimitExceededException.class)
    public ProblemDetail handleFileSizeLimitExceeded(FileSizeLimitExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(org.springframework.http.HttpStatusCode.valueOf(413));
        problem.setTitle("Arquivo excede limite de tamanho");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(org.springframework.http.HttpStatusCode.valueOf(413));
        problem.setTitle("Arquivo excede limite de tamanho");
        problem.setDetail("O arquivo excede o limite máximo de 5MB");
        return problem;
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ProblemDetail handleAccessDenied(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Acesso Negado");
        problem.setDetail(ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "Você não possui permissão para acessar este recurso.");
        return problem;
    }

    /**
     * Trata exceções genéricas não capturadas pelos handlers específicos.
     * Retorna HTTP 500 Internal Server Error.
     * IMPORTANTE: Registra o stack trace completo no log para debug.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unexpected system error: ", ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Erro interno do servidor");
        problem.setDetail("Ocorreu um erro inesperado. Por favor, contate o suporte.");
        return problem;
    }
}

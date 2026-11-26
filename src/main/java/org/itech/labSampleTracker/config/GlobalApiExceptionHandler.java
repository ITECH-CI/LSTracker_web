package org.itech.labSampleTracker.config;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.itech.labSampleTracker.exception.OperationFailedException;
import org.itech.labSampleTracker.exception.ResourceAlreadyExistsException;
import org.itech.labSampleTracker.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Centralized exception handler for REST API endpoints (/api/** and /api_v2/**).
 * Logs structured stacktraces and returns consistent ErrorMessage payloads.
 */
@RestControllerAdvice(basePackages = { "org.itech.labSampleTracker.api" })
public class GlobalApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ErrorMessage> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
		log.warn("ResourceNotFound on {} {} : {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
	}

	@ExceptionHandler(ResourceAlreadyExistsException.class)
	public ResponseEntity<ErrorMessage> handleConflict(ResourceAlreadyExistsException ex, HttpServletRequest req) {
		log.warn("ResourceAlreadyExists on {} {} : {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
		return build(HttpStatus.CONFLICT, ex.getMessage(), req);
	}

	@ExceptionHandler({ OptimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class })
	public ResponseEntity<ErrorMessage> handleOptimisticLock(Exception ex, HttpServletRequest req) {
		log.warn("OptimisticLockingFailure on {} {} : {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
		return build(HttpStatus.CONFLICT,
				"Conflit de version : la ressource a été modifiée par ailleurs. Rechargez et réessayez.", req);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorMessage> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
		List<ErrorMessage.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> ErrorMessage.FieldError.builder()
						.field(fe.getField())
						.rejectedValue(fe.getRejectedValue())
						.message(fe.getDefaultMessage())
						.build())
				.collect(Collectors.toList());
		log.info("Validation failed on {} {} : {} field(s) in error", req.getMethod(), req.getRequestURI(), fields.size());
		ErrorMessage body = baseBuilder(HttpStatus.BAD_REQUEST, "Données invalides", req)
				.fieldErrors(fields)
				.build();
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorMessage> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
		log.info("ConstraintViolation on {} {} : {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
		return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorMessage> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
		log.info("Unreadable body on {} {} : {}", req.getMethod(), req.getRequestURI(), ex.getMostSpecificCause().getMessage());
		return build(HttpStatus.BAD_REQUEST, "Corps de requête invalide ou mal formé", req);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorMessage> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
		log.info("TypeMismatch on {} {} : param '{}' rejected value '{}'", req.getMethod(), req.getRequestURI(),
				ex.getName(), ex.getValue());
		return build(HttpStatus.BAD_REQUEST,
				"Paramètre invalide : " + ex.getName(), req);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorMessage> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
		log.warn("DataIntegrityViolation on {} {} : {}", req.getMethod(), req.getRequestURI(),
				ex.getMostSpecificCause().getMessage());
		return build(HttpStatus.CONFLICT, "Violation d'intégrité de données", req);
	}

	/** Connexion mobile refusée : message de tentatives restantes ou de blocage, comme sur le web. */
	@ExceptionHandler(org.itech.labSampleTracker.api.auth.LoginRefusedException.class)
	public ResponseEntity<ErrorMessage> handleLoginRefused(org.itech.labSampleTracker.api.auth.LoginRefusedException ex,
			HttpServletRequest req) {
		log.info("Login refused on {} {}", req.getMethod(), req.getRequestURI());
		return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<ErrorMessage> handleBadCredentials(BadCredentialsException ex, HttpServletRequest req) {
		log.info("BadCredentials on {} {}", req.getMethod(), req.getRequestURI());
		return build(HttpStatus.UNAUTHORIZED, "Identifiants invalides", req);
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ErrorMessage> handleAuth(AuthenticationException ex, HttpServletRequest req) {
		log.info("AuthenticationFailure on {} {} : {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
		return build(HttpStatus.UNAUTHORIZED, "Authentification requise", req);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorMessage> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
		log.warn("AccessDenied on {} {} : {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
		return build(HttpStatus.FORBIDDEN, "Accès refusé", req);
	}

	@ExceptionHandler(OperationFailedException.class)
	public ResponseEntity<ErrorMessage> handleOperationFailed(OperationFailedException ex, HttpServletRequest req) {
		log.error("OperationFailed on {} {} : {}", req.getMethod(), req.getRequestURI(), ex.getMessage(), ex);
		return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorMessage> handleAny(Exception ex, HttpServletRequest req, WebRequest webReq) {
		log.error("Unhandled exception on {} {} : {}", req.getMethod(), req.getRequestURI(), ex.getMessage(), ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne. Veuillez réessayer plus tard.", req);
	}

	private ResponseEntity<ErrorMessage> build(HttpStatus status, String message, HttpServletRequest req) {
		return ResponseEntity.status(status).body(baseBuilder(status, message, req).build());
	}

	private ErrorMessage.ErrorMessageBuilder baseBuilder(HttpStatus status, String message, HttpServletRequest req) {
		return ErrorMessage.builder()
				.statusCode(status.value())
				.error(status.getReasonPhrase())
				.message(message)
				.timestamp(OffsetDateTime.now())
				.path(req != null ? req.getRequestURI() : null)
				.requestId(MDC.get("requestId"));
	}
}

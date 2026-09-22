package et.ut.einvoice.platform.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Centralized Production-Safe Error Boundary.
 * Strictly prevents framework internals, SQL queries, Hibernate traces, and class names
 * from leaking to API clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorEnvelope> handleBusinessException(BusinessException ex, HttpServletRequest request, HttpServletResponse response) {
        String correlationId = resolveCorrelationId(request, response);
        log.warn("Business exception [{}] on request {} [{}]: {}", ex.getCode(), correlationId, request.getRequestURI(), ex.getMessage());

        ErrorEnvelope envelope = ErrorEnvelope.of(
                ex.getHttpStatus().value(),
                ex.getCode(),
                ex.getMessage(),
                ex.getLocalizedMessageText(),
                correlationId,
                request.getRequestURI()
        );
        return new ResponseEntity<>(envelope, ex.getHttpStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request, HttpServletResponse response) {
        String correlationId = resolveCorrelationId(request, response);
        List<ErrorEnvelope.ValidationErrorDetail> details = new ArrayList<>();

        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            details.add(new ErrorEnvelope.ValidationErrorDetail(
                    fe.getField(),
                    fe.getDefaultMessage(),
                    fe.getRejectedValue()
            ));
        }

        log.warn("Validation failure on request {} [{}]: {} field error(s)", correlationId, request.getRequestURI(), details.size());

        ErrorEnvelope envelope = ErrorEnvelope.of(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Request payload validation failed.",
                "የቀረበው የግብይት መረጃ የተሳሳተ ነው፤ እባክዎ እንደገና ያረጋግጡ።",
                correlationId,
                request.getRequestURI(),
                details
        );
        return new ResponseEntity<>(envelope, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorEnvelope> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request, HttpServletResponse response) {
        String correlationId = resolveCorrelationId(request, response);
        List<ErrorEnvelope.ValidationErrorDetail> details = new ArrayList<>();

        for (ConstraintViolation<?> cv : ex.getConstraintViolations()) {
            String propertyPath = cv.getPropertyPath() != null ? cv.getPropertyPath().toString() : "parameter";
            details.add(new ErrorEnvelope.ValidationErrorDetail(
                    propertyPath,
                    cv.getMessage(),
                    cv.getInvalidValue()
            ));
        }

        log.warn("Constraint violation on request {} [{}]: {}", correlationId, request.getRequestURI(), ex.getMessage());

        ErrorEnvelope envelope = ErrorEnvelope.of(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Request parameter constraint violation.",
                "የቀረበው መለኪያ የተሳሳተ ነው፤ እባክዎ እንደገና ያረጋግጡ።",
                correlationId,
                request.getRequestURI(),
                details
        );
        return new ResponseEntity<>(envelope, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorEnvelope> handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request, HttpServletResponse response) {
        String correlationId = resolveCorrelationId(request, response);
        log.warn("Malformed JSON/Payload on request {} [{}]: {}", correlationId, request.getRequestURI(), ex.getMessage());

        ErrorEnvelope envelope = ErrorEnvelope.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_REQUEST",
                "Malformed or unparseable JSON request payload.",
                "የቀረበው የJSON መረጃ ቅርጸት የተሳሳተ ነው።",
                correlationId,
                request.getRequestURI()
        );
        return new ResponseEntity<>(envelope, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorEnvelope> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request, HttpServletResponse response) {
        String correlationId = resolveCorrelationId(request, response);
        String paramName = ex.getName() != null ? ex.getName() : "parameter";
        log.warn("Type mismatch on parameter '{}' on request {} [{}]", paramName, correlationId, request.getRequestURI());

        ErrorEnvelope envelope = ErrorEnvelope.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_REQUEST",
                "Invalid value provided for parameter: " + paramName,
                "ለመለኪያው የቀረበው እሴት ትክክል አይደለም።",
                correlationId,
                request.getRequestURI()
        );
        return new ResponseEntity<>(envelope, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorEnvelope> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request, HttpServletResponse response) {
        String correlationId = resolveCorrelationId(request, response);
        log.warn("Illegal argument on request {} [{}]: {}", correlationId, request.getRequestURI(), ex.getMessage());

        ErrorEnvelope envelope = ErrorEnvelope.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_REQUEST",
                ex.getMessage() != null ? ex.getMessage() : "Invalid argument provided.",
                "የቀረበው መረጃ ትክክል አይደለም።",
                correlationId,
                request.getRequestURI()
        );
        return new ResponseEntity<>(envelope, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ErrorEnvelope> handleMissingParams(Exception ex, HttpServletRequest request, HttpServletResponse response) {
        String correlationId = resolveCorrelationId(request, response);
        log.warn("Missing required parameter/header on request {} [{}]: {}", correlationId, request.getRequestURI(), ex.getMessage());

        ErrorEnvelope envelope = ErrorEnvelope.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_REQUEST",
                "A required request parameter or header is missing.",
                "አስፈላጊ የመረጃ መለኪያ ወይም ራስጌ አልተገኘም።",
                correlationId,
                request.getRequestURI()
        );
        return new ResponseEntity<>(envelope, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorEnvelope> handlePropertyReference(PropertyReferenceException ex, HttpServletRequest request, HttpServletResponse response) {
        String correlationId = resolveCorrelationId(request, response);
        log.warn("Invalid property sort reference on request {} [{}]: {}", correlationId, request.getRequestURI(), ex.getMessage());

        ErrorEnvelope envelope = ErrorEnvelope.of(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_REQUEST",
                "Invalid sort field or property reference.",
                "የተጠየቀው የመደርደሪያ መስክ ትክክል አይደለም።",
                correlationId,
                request.getRequestURI()
        );
        return new ResponseEntity<>(envelope, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request, HttpServletResponse response) {
        String correlationId = resolveCorrelationId(request, response);
        log.warn("HTTP method not allowed on request {} [{}]: {}", correlationId, request.getRequestURI(), ex.getMethod());

        ErrorEnvelope envelope = ErrorEnvelope.of(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                "INVALID_REQUEST",
                "HTTP method '" + ex.getMethod() + "' is not supported for this endpoint.",
                "የቀረበው የHTTP ዘዴ ለዚህ መዳረሻ አይፈቀድም።",
                correlationId,
                request.getRequestURI()
        );
        return new ResponseEntity<>(envelope, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorEnvelope> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex, HttpServletRequest request, HttpServletResponse response) {
        String correlationId = resolveCorrelationId(request, response);
        log.warn("Not acceptable media type on request {} [{}]: {}", correlationId, request.getRequestURI(), ex.getMessage());

        ErrorEnvelope envelope = ErrorEnvelope.of(
                HttpStatus.NOT_ACCEPTABLE.value(),
                "NOT_ACCEPTABLE",
                "Requested media type is not acceptable for this resource.",
                "የተጠየቀው የሚዲያ ዓይነት ለዚህ መረጃ ተቀባይነት የለውም።",
                correlationId,
                request.getRequestURI()
        );
        return new ResponseEntity<>(envelope, HttpStatus.NOT_ACCEPTABLE);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request, HttpServletResponse response) {
        String correlationId = resolveCorrelationId(request, response);
        log.warn("Unsupported media type on request {} [{}]: {}", correlationId, request.getRequestURI(), ex.getContentType());

        ErrorEnvelope envelope = ErrorEnvelope.of(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                "UNSUPPORTED_MEDIA_TYPE",
                "Content-Type '" + ex.getContentType() + "' is not supported.",
                "የቀረበው የሚዲያ ዓይነት አይደገፍም።",
                correlationId,
                request.getRequestURI()
        );
        return new ResponseEntity<>(envelope, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorEnvelope> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request, HttpServletResponse response) {
        String correlationId = resolveCorrelationId(request, response);
        log.warn("Request size exceeded limit on request {} [{}]", correlationId, request.getRequestURI());

        ErrorEnvelope envelope = ErrorEnvelope.of(
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "REQUEST_TOO_LARGE",
                "Request payload exceeds the maximum permitted size limit.",
                "የቀረበው የመረጃ መጠን ከተፈቀደው ገደብ በላይ ነው።",
                correlationId,
                request.getRequestURI()
        );
        return new ResponseEntity<>(envelope, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request, HttpServletResponse response) {
        String correlationId = resolveCorrelationId(request, response);
        log.warn("Access denied on request {} [{}]: {}", correlationId, request.getRequestURI(), ex.getMessage());

        ErrorEnvelope envelope = ErrorEnvelope.of(
                HttpStatus.FORBIDDEN.value(),
                "AUTHORIZATION_DENIED",
                "Authenticated principal lacks the required permissions or scopes for this operation.",
                "ይህን ተግባር ለማከናወን የሚያስፈልገው ፈቃድ (Scope) የለዎትም።",
                correlationId,
                request.getRequestURI()
        );
        return new ResponseEntity<>(envelope, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorEnvelope> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request, HttpServletResponse response) {
        String correlationId = resolveCorrelationId(request, response);
        log.warn("Authentication failure on request {} [{}]: {}", correlationId, request.getRequestURI(), ex.getMessage());

        ErrorEnvelope envelope = ErrorEnvelope.of(
                HttpStatus.UNAUTHORIZED.value(),
                "AUTHENTICATION_REQUIRED",
                "Authentication credentials are missing or invalid.",
                "የማረጋገጫ መረጃ አልቀረበም ወይም ትክክል አይደለም።",
                correlationId,
                request.getRequestURI()
        );
        return new ResponseEntity<>(envelope, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorEnvelope> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request, HttpServletResponse response) {
        String correlationId = resolveCorrelationId(request, response);
        log.error("Database data integrity violation on request {} [{}]: {}", correlationId, request.getRequestURI(), ex.getMessage(), ex);

        // Mask all SQL/constraint details from client
        ErrorEnvelope envelope = ErrorEnvelope.of(
                HttpStatus.CONFLICT.value(),
                "DUPLICATE_RESOURCE",
                "A resource conflict or duplicate record occurred.",
                "ተመሳሳይ መረጃ ቀድሞ ተመዝግቧል ወይም ግጭት ተፈጥሯል።",
                correlationId,
                request.getRequestURI()
        );
        return new ResponseEntity<>(envelope, HttpStatus.CONFLICT);
    }

    @ExceptionHandler({SQLException.class, DataAccessException.class})
    public ResponseEntity<ErrorEnvelope> handleDatabaseException(Exception ex, HttpServletRequest request, HttpServletResponse response) {
        String correlationId = resolveCorrelationId(request, response);
        log.error("Database error on request {} [{}]: {}", correlationId, request.getRequestURI(), ex.getMessage(), ex);

        // Mask all database internals from client
        ErrorEnvelope envelope = ErrorEnvelope.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "An internal persistence error occurred. Please contact technical support with correlation ID.",
                "የመረጃ ቋት ችግር አጋጥሟል፤ እባክዎ የቴክኒክ ድጋፍ ሰጪውን ያነጋግሩ።",
                correlationId,
                request.getRequestURI()
        );
        return new ResponseEntity<>(envelope, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex, HttpServletRequest request, HttpServletResponse response) {
        String correlationId = resolveCorrelationId(request, response);
        log.warn("Resource not found on request {} [{}]: {}", correlationId, request.getRequestURI(), ex.getMessage());
        ErrorEnvelope envelope = ErrorEnvelope.of(
                HttpStatus.NOT_FOUND.value(),
                "RESOURCE_NOT_FOUND",
                "The requested resource was not found.",
                "የተጠየቀው መረጃ አልተገኘም።",
                correlationId,
                request.getRequestURI()
        );
        return new ResponseEntity<>(envelope, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleGenericException(Exception ex, HttpServletRequest request, HttpServletResponse response) {
        String correlationId = resolveCorrelationId(request, response);
        log.error("Unhandled internal server error on request {} [{}]", correlationId, request.getRequestURI(), ex);

        ErrorEnvelope envelope = ErrorEnvelope.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "An unexpected internal error occurred. Please contact technical support with correlation ID.",
                "ያልተጠበቀ የስርዓት ችግር አጋጥሟል፤ እባክዎ የቴክኒክ ድጋፍ ሰጪውን ያነጋግሩ።",
                correlationId,
                request.getRequestURI()
        );
        return new ResponseEntity<>(envelope, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String resolveCorrelationId(HttpServletRequest request, HttpServletResponse response) {
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        if (response != null) {
            response.setHeader("X-Correlation-ID", correlationId);
        }
        return correlationId;
    }
}

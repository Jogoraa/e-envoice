package et.ut.einvoice.platform.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final String code;
    private final String localizedMessage;
    private final HttpStatus httpStatus;

    public BusinessException(String code, String message) {
        this(code, message, message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(String code, String message, HttpStatus httpStatus) {
        this(code, message, message, httpStatus);
    }

    public BusinessException(String code, String message, String localizedMessage, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.localizedMessage = localizedMessage;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getLocalizedMessageText() {
        return localizedMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}

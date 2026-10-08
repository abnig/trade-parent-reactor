package com.trading.upload;

import java.util.List;
import com.trading.model.coin.CoinValidationException.Problem;
import org.springframework.http.HttpStatus;

/** Safe HTTP diagnostics. Never retain a JDBC/file exception cause that might disclose source records. */
public final class CoinUploadException extends RuntimeException {
    private final HttpStatus status;
    private final List<Problem> problems;
    public CoinUploadException(HttpStatus status, String message, List<Problem> problems) {
        super(message);
        this.status = status;
        this.problems = List.copyOf(problems);
    }
    public HttpStatus status() { return status; }
    public List<Problem> problems() { return problems; }
}

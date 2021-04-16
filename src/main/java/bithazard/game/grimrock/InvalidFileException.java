package bithazard.game.grimrock;

public class InvalidFileException extends RuntimeException {
    public InvalidFileException(String message) {
        super(message);
    }

    public InvalidFileException(Throwable cause) {
        super(cause);
    }

    public InvalidFileException(String message, Throwable cause) {
        super(message, cause);
    }
}

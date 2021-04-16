package bithazard.game.grimrock.model;

public class InvalidByteSizeException extends RuntimeException {
    public InvalidByteSizeException(String message) {
        super(message);
    }

    public InvalidByteSizeException(Throwable cause) {
        super(cause);
    }

    public InvalidByteSizeException(String message, Throwable cause) {
        super(message, cause);
    }
}

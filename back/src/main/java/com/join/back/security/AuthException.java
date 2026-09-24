package com.join.back.security;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AuthException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public AuthException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static AuthException emailInvalid()      { return new AuthException(HttpStatus.BAD_REQUEST, "EMAIL_INVALID",      "Некорректный email"); }
    public static AuthException passwordTooShort()  { return new AuthException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_SHORT", "Пароль должен быть не короче 8 символов"); }
    public static AuthException badCredentials()    { return new AuthException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS",   "Неверный email или пароль"); }
    public static AuthException emailTaken()        { return new AuthException(HttpStatus.CONFLICT,     "EMAIL_TAKEN",       "Email уже используется"); }
    public static AuthException alreadyLinked()     { return new AuthException(HttpStatus.CONFLICT,     "ALREADY_LINKED",    "К этому аккаунту уже привязан email"); }
    public static AuthException maxTaken()     { return new AuthException(HttpStatus.CONFLICT,     "MAX_TAKEN",    "Этот аккаунт MAX уже привязан к другому аккаунту JOIN"); }
    public static AuthException messengerTaken(Messenger m) {
        return m == Messenger.MAX ? maxTaken()
                : new AuthException(HttpStatus.CONFLICT, m.name() + "_TAKEN", "Этот аккаунт " + m.displayName() + " уже привязан к другому аккаунту JOIN");
    }
    public static AuthException messengerMismatch(Messenger m) {
        return m == Messenger.MAX ? maxMismatch()
                : new AuthException(HttpStatus.CONFLICT, m.name() + "_MISMATCH", "К этому аккаунту уже привязан другой аккаунт " + m.displayName());
    }
    public static AuthException maxMismatch()  { return new AuthException(HttpStatus.CONFLICT,     "MAX_MISMATCH", "К этому аккаунту уже привязан другой аккаунт MAX"); }
}

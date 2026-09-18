package me.pfzh.hibernatelite.exception;

import java.io.Serial;

/**
 * Hibernate-Lite 库的统一异常根类。
 *
 * <p>所有库内部抛出的异常都继承自此类，用户只需 catch 这一个异常类型。
 * 继承 {@link RuntimeException}，不强制用户处理受检异常。</p>
 */
public class HibernateLiteException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public HibernateLiteException(){
    }

    public HibernateLiteException(String message) {
        super(message);
    }

    public HibernateLiteException(String message, Throwable cause) {
        super(message, cause);
    }

    public HibernateLiteException(Throwable cause) {
        super(cause);
    }

}

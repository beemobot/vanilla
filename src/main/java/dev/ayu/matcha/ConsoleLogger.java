package dev.ayu.matcha;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

public class ConsoleLogger {

    private final SimpleDateFormat LOG_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");

    public ConsoleLogger() {
    }

    public void info(String message) {
        System.out.println(LOG_FORMAT.format(Date.from(Instant.now())) + " INFO: " + message);
    }

    public void warn(String message) {
        System.out.println(LOG_FORMAT.format(Date.from(Instant.now())) + " WARN: " + message);
    }

    public Void error(Throwable error) {
        return error(error.getMessage(), error);
    }

    public Void error(String message, Throwable error) {
        System.out.println(LOG_FORMAT.format(Date.from(Instant.now())) + " ERROR: " + message);
        error.printStackTrace();
        return null;
    }

    public Void uncaughtError(Throwable error) {
        return uncaughtError(error.getMessage(), error);
    }

    public Void uncaughtError(String message, Throwable error) {
        System.out.println(LOG_FORMAT.format(Date.from(Instant.now())) + " UNCAUGHT ERROR: " + message);
        error.printStackTrace();
        return null;
    }

}

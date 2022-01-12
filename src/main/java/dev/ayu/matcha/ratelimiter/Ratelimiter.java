package dev.ayu.matcha.ratelimiter;

import java.time.Duration;

public class Ratelimiter {

    private volatile long lastNanoTime = System.nanoTime();
    private final long pauseNanos;

    public Ratelimiter(int permits, Duration perDuration) {
        this.pauseNanos = perDuration.toNanos() / permits
                + perDuration.toNanos() % permits;
    }

    public synchronized void requestQuota() {
        while (System.nanoTime() < lastNanoTime + pauseNanos) {
            try {
                Thread.sleep(Duration.ofNanos(lastNanoTime + pauseNanos - System.nanoTime())
                        .toMillis());
            } catch (InterruptedException e) {
                break;
            }
        }
        lastNanoTime = System.nanoTime();
    }

    public long getRemainingPauseNanos() {
        final long remainingPauseNanos = pauseNanos - System.nanoTime();
        return remainingPauseNanos > 0
                ? remainingPauseNanos
                : 0;
    }

    public long getRemainingPermits() {
        // We make it so that it works on a single-permit basis
        return getRemainingPauseNanos() > 0
                ? 0
                : 1;
    }

}

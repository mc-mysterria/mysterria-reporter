package net.mysterria.reporter.domain.profile.model.source;

public enum PushOutcome {
    SENT,
    RETRY,
    REJECTED,
    EPOCH_CLOSED;

    public static PushOutcome of(int status) {
        if (status >= 200 && status < 300) return SENT;
        if (status == 410) return EPOCH_CLOSED;
        if (status == 400 || status == 413 || status == 422) return REJECTED;
        return RETRY;
    }
}

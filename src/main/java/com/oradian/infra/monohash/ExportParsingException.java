package com.oradian.infra.monohash;

@SuppressWarnings("serial")
public final class ExportParsingException extends Exception {
    public ExportParsingException(final String msg) {
        super(msg);
    }

    public ExportParsingException(final String msg, final Exception cause) {
        super(msg, cause);
    }
}

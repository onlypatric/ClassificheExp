package it.patric.classificheexp.crossserver.protocol;

public enum BridgeOp {
    GET_SCORE,
    GET_TOP,
    ADD_SCORE,
    REMOVE_SCORE,
    SET_SCORE,
    RESULT,
    ERROR,
    PING,
    PONG;

    public boolean isResponse() {
        return this == RESULT || this == ERROR || this == PONG;
    }

    public boolean isRequest() {
        return !isResponse();
    }
}

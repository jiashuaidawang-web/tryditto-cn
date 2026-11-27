package cn.aiditto.domain.recall.v4;

/** Where a recall subject came from. The future production path is a contract only; it reads no user database. */
public enum RecallIngress {
    SYNTHETIC_FIXTURE,
    FUTURE_PRODUCTION
}

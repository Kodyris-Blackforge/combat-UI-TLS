package com.kodyris.combathud.contract;

/** What the client is told about a contract. remainingSeconds is -1 when there is no timer. */
public record ContractView(String id, String type, String description, int progress, int target, int reward,
                           int remainingSeconds) {
}

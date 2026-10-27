package com.kodyris.combathud.client;

import com.kodyris.combathud.contract.ContractView;

import java.util.List;

/** The contract list last sent by the server, with local countdown between syncs. */
final class ClientContracts {
    private ClientContracts() {}

    static List<ContractView> list = List.of();
    private static long receivedNanos;

    static void set(List<ContractView> l) {
        list = l;
        receivedNanos = System.nanoTime();
    }

    /** Seconds left, or -1 when the contract has no timer. */
    static int remaining(ContractView c) {
        if (c.remainingSeconds() < 0) return -1;
        long elapsed = (System.nanoTime() - receivedNanos) / 1_000_000_000L;
        return (int) Math.max(0L, c.remainingSeconds() - elapsed);
    }
}

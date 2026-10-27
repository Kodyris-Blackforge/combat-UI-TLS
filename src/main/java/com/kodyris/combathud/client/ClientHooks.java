package com.kodyris.combathud.client;

import com.kodyris.combathud.contract.ContractView;

import java.util.List;

/** Entry points for packets. Only ever called on the client (via DistExecutor). */
public final class ClientHooks {
    private ClientHooks() {}

    public static void contracts(List<ContractView> list) {
        ClientContracts.set(list);
    }

    public static void title(String label, String name, String style) {
        TitleBanner.show(label, name, style);
    }
}

package io.forgeflow.controlplane.webhook.application.port.out;

import java.util.function.Supplier;

/** All operations in the callback must commit together or roll back together. */
public interface TransactionBoundary { <T> T required(Supplier<T> work); }

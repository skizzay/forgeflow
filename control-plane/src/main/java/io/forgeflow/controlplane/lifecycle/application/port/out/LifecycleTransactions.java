package io.forgeflow.controlplane.lifecycle.application.port.out;

import java.util.function.Supplier;
public interface LifecycleTransactions { <T> T required(Supplier<T> work); }

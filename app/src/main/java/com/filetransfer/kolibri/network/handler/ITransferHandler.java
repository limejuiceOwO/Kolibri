package com.filetransfer.kolibri.network.handler;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

public interface ITransferHandler {
    boolean register(Selector selector);
    boolean onSelected(SelectionKey key);
    // it is guaranteed that onSelected() and abort() will always be called AFTER register() is called
    void abort();
}

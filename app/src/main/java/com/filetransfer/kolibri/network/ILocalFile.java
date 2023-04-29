package com.filetransfer.kolibri.network;

import java.io.FileInputStream;

public interface ILocalFile {
    String name();
    long size();
    FileInputStream stream();
    void close();
}

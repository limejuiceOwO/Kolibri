package com.filetransfer.kolibri.network;

public class NetProtocol {

    public static final int PORT = 19810;
    public static final int FILE_PORT = 19811;

    public static final short MAX_CHAT_LEN = 1024;
    public static final int FILE_BLK_SIZE = 4 * 1024;

    public static final byte[] HANDSHAKE_HEADER = { 0x11, 0x45, 0x14 };

    public static final byte CMD_CHAT = 0x03;
    public static final byte CMD_VIBRATE = 0x04;
}

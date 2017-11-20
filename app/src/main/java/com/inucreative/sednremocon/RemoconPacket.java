package com.inucreative.sednremocon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * Created by Jskim on 2016-06-23.
 */
public class RemoconPacket {
    String[] sendCmd = {"SednKeyCmd", "SednInputStr", "SednVolume"};

    public static final int SEND_KEYCODE = 0;
    public static final int SEND_STRING = 1;
    public static final int SEND_VOLUME = 2;

    int mType;
    Object mData;

    public RemoconPacket(int type, Object data) {
        mType = type;
        mData = data;
    }

    public byte[] getByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(sendCmd[mType]);
            oos.writeObject(mData);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return baos.toByteArray();
    }
}

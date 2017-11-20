package com.inucreative.sednremocon;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * Created by Jskim on 2016-07-30.
 */
public class FeedbackListener extends Thread {
    static final int REMOCON_FEEDBACK_PORT = 7079;

    Context mContext;
    DatagramSocket serverSocket;
    DatagramPacket receivedPacket;

    public FeedbackListener(Context context) {
        mContext = context;

        try {
            serverSocket = new DatagramSocket(REMOCON_FEEDBACK_PORT);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            byte[] buf = new byte[128];
            while(true) {
                receivedPacket = new DatagramPacket(buf, buf.length);
                serverSocket.receive(receivedPacket);

                ByteArrayInputStream baos = new ByteArrayInputStream(buf);
                ObjectInputStream oos = new ObjectInputStream(baos);
                String cmd = (String)oos.readObject();
                LogUtil.d(cmd);

                if(cmd.equals("SednText")) {
                    final String stbText = (String)oos.readObject();
                    ((MainActivity)mContext).mStbText = stbText;
                    LogUtil.d("Text :  " + stbText);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) {
        System.out.println("=================================");
        System.out.println("   AUTORSKI BOT AFK 1.21.4      ");
        System.out.println("=================================");
        
        String host = "anarchia.gg";
        int port = 25565;
        String username = "Bot_AFK_" + (int)(Math.random() * 8999 + 1000);

        while (true) {
            try {
                System.out.println("[BOT] Proba polaczenia z " + host + " jako: " + username);
                Socket socket = new Socket(host, port);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());

                // Handshake (1.21.4 - protocol 768)
                ByteArrayOutputStream handshakeBytes = new ByteArrayOutputStream();
                DataOutputStream handshakeOut = new DataOutputStream(handshakeBytes);
                writeVarInt(handshakeOut, 0x00);
                writeVarInt(handshakeOut, 768);
                writeString(handshakeOut, host);
                handshakeOut.writeShort(port);
                writeVarInt(handshakeOut, 2);
                sendPacket(out, handshakeBytes.toByteArray());

                // Login Start
                ByteArrayOutputStream loginBytes = new ByteArrayOutputStream();
                DataOutputStream loginOut = new DataOutputStream(loginBytes);
                writeVarInt(loginOut, 0x00);
                writeString(loginOut, username);
                loginOut.writeLong(0);
                loginOut.writeLong(0);
                sendPacket(out, loginBytes.toByteArray());

                System.out.println("[BOT] Polaczono pomyslnie z " + host + "!");
                System.out.println("[BOT] Utrzymuje polaczenie AFK...");

                while (!socket.isClosed()) {
                    int length = readVarInt(in);
                    if (length < 0) break;
                    byte[] packetData = new byte[length];
                    in.readFully(packetData);
                    Thread.sleep(50);
                }
            } catch (Exception e) {
                System.out.println("[BOT] Blad: " + e.getMessage() + ". Ponowna proba za 10 sek...");
            }

            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) {}
        }
    }

    private static void sendPacket(DataOutputStream out, byte[] data) throws IOException {
        writeVarInt(out, data.length);
        out.write(data);
        out.flush();
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & -128) != 0) {
            out.write(value & 127 | 128);
            value >>>= 7;
        }
        out.write(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = in.readByte();
            int value = (read & 127);
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) throw new RuntimeException("VarInt za duzy");
        } while ((read & 128) != 0);
        return result;
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }
}

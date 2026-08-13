import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

public class Main {
    public static void main(String[] args) {
        System.out.println("=================================");
        System.out.println("   AUTORSKI BOT AFK 1.21.4      ");
        System.out.println("=================================");
        
        String inputHost = "anarchia.gg";
        String username = "jankoder2"; // Twój nick

        while (true) {
            try {
                // Odczytanie SRV (zwraca tablice: [0] = host, [1] = port)
                String[] srv = resolveSrv(inputHost);
                String targetHost = srv[0];
                int targetPort = Integer.parseInt(srv[1]);

                System.out.println("[BOT] Lacznie z: " + targetHost + ":" + targetPort + " jako nick: " + username + "...");

                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(targetHost, targetPort), 10000);
                
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());

                // Handshake Packet (Minecraft 1.21.4 = protocol 768)
                ByteArrayOutputStream handshakeBytes = new ByteArrayOutputStream();
                DataOutputStream handshakeOut = new DataOutputStream(handshakeBytes);
                writeVarInt(handshakeOut, 0x00);
                writeVarInt(handshakeOut, 768);
                writeString(handshakeOut, inputHost);
                handshakeOut.writeShort(targetPort);
                writeVarInt(handshakeOut, 2); // State: Login
                sendPacket(out, handshakeBytes.toByteArray());

                // Login Start Packet
                ByteArrayOutputStream loginBytes = new ByteArrayOutputStream();
                DataOutputStream loginOut = new DataOutputStream(loginBytes);
                writeVarInt(loginOut, 0x00);
                writeString(loginOut, username);
                loginOut.writeLong(0);
                loginOut.writeLong(0);
                sendPacket(out, loginBytes.toByteArray());

                System.out.println("[BOT] [OK] Polaczono pomyslnie z serwerem!");
                System.out.println("[BOT] Utrzymuje polaczenie AFK...");

                // Petla czytajaca dane z serwera
                while (!socket.isClosed()) {
                    int length = readVarInt(in);
                    if (length < 0) {
                        System.out.println("[BOT] Serwer zamknal polaczenie (kick/rozlaczenie).");
                        break;
                    }
                    byte[] packetData = new byte[length];
                    in.readFully(packetData);
                    Thread.sleep(50);
                }
            } catch (SocketTimeoutException e) {
                System.err.println("[BOT] [BLAD TIMEOUT] Serwer nie odpowiedzial.");
            } catch (UnknownHostException e) {
                System.err.println("[BOT] [BLAD DOMENY] Nie odnaleziono IP dla " + inputHost);
            } catch (ConnectException e) {
                System.err.println("[BOT] [BLAD POLACZENIA] Odmowa polaczenia na wybranym porcie.");
            } catch (Exception e) {
                System.err.println("[BOT] [SZCZEGOLY BLADU]: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                e.printStackTrace(System.err);
            }

            System.out.println("[BOT] Odczekam 10 sekund przed kolejna proba...");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) {}
        }
    }

    // Rekordy SRV bez uzywania dodatkowych klas
    private static String[] resolveSrv(String host) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes("_minecraft._tcp." + host, new String[]{"SRV"});
            if (attrs != null && attrs.get("SRV") != null) {
                String[] srvData = attrs.get("SRV").get().toString().split(" ");
                if (srvData.length >= 4) {
                    String targetHost = srvData[3];
                    if (targetHost.endsWith(".")) {
                        targetHost = targetHost.substring(0, targetHost.length() - 1);
                    }
                    String targetPort = srvData[2];
                    System.out.println("[DNS] Znaleziono rekord SRV: " + targetHost + ":" + targetPort);
                    return new String[]{targetHost, targetPort};
                }
            }
        } catch (Exception ignored) {}
        return new String[]{host, "25565"};
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
            if (numRead > 5) throw new IOException("VarInt za duzy");
        } while ((read & 128) != 0);
        return result;
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }
}

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
        String username = "jankoder2"; // Twój stały nick

        while (true) {
            try {
                System.out.println("[BOT] Proba polaczenia z " + host + ":" + port + " jako nick: " + username + "...");
                
                // Polaczenie z timeoutem 5 sekund
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 5000);
                
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());

                // Handshake Packet (wersja 1.21.4 = protocol 768)
                ByteArrayOutputStream handshakeBytes = new ByteArrayOutputStream();
                DataOutputStream handshakeOut = new DataOutputStream(handshakeBytes);
                writeVarInt(handshakeOut, 0x00);
                writeVarInt(handshakeOut, 768);
                writeString(handshakeOut, host);
                handshakeOut.writeShort(port);
                writeVarInt(handshakeOut, 2); // Next State: Login
                sendPacket(out, handshakeBytes.toByteArray());

                // Login Start Packet
                ByteArrayOutputStream loginBytes = new ByteArrayOutputStream();
                DataOutputStream loginOut = new DataOutputStream(loginBytes);
                writeVarInt(loginOut, 0x00);
                writeString(loginOut, username);
                loginOut.writeLong(0); // Offline UUID Sig
                loginOut.writeLong(0); // Offline UUID Least
                sendPacket(out, loginBytes.toByteArray());

                System.out.println("[BOT] [OK] Pakiety polaczeniowe wyslane! Bot wbija na serwer!");
                System.out.println("[BOT] Utrzymuje polaczenie AFK...");

                // Petla czytajaca dane z serwera
                while (!socket.isClosed()) {
                    int length = readVarInt(in);
                    if (length < 0) {
                        System.out.println("[BOT] [OSTRZEZENIE] Serwer zamknal polaczenie (rozlaczenie / kick).");
                        break;
                    }
                    byte[] packetData = new byte[length];
                    in.readFully(packetData);
                    
                    Thread.sleep(50);
                }
            } catch (SocketTimeoutException e) {
                System.err.println("[BOT] [BLAD TIMEOUT] Serwer nie odpowiedzial w ciagu 5 sekund.");
            } catch (UnknownHostException e) {
                System.err.println("[BOT] [BLAD DOMENY] Nie znaleziono adresu " + host + ". Sprawdz IP.");
            } catch (ConnectException e) {
                System.err.println("[BOT] [BLAD POLACZENIA] Serwer jest wylaczony lub blokuje polaczenia z tego IP.");
            } catch (EOFException e) {
                System.err.println("[BOT] [BLAD KICK/DISCONNECT] Serwer odrzucil polaczenie bota (np. ban, kick lub wymagane Premium).");
            } catch (Exception e) {
                System.err.println("[BOT] [SZCZEGOLY BLADU]: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                e.printStackTrace(System.err); // Pokaze pelny szczegolowy slad bledu w konsoli
            }

            System.out.println("[BOT] Odczekam 10 sekund przed ponowna proba...");
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
            if (numRead > 5) throw new IOException("VarInt za duzy - uszkodzony pakiet");
        } while ((read & 128) != 0);
        return result;
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }
}

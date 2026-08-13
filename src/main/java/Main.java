import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.PacketReceivedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.tcp.TcpClientSession;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;

import java.util.Hashtable;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

public class Main {

    private static final String HOST = "anarchia.gg";
    private static final String USERNAME = "jankoder2";
    private static final String PASSWORD = "Krokodyl12!";

    public static void main(String[] args) {
        System.out.println("=================================");
        System.out.println("   AUTORSKI BOT AFK 1.21.4      ");
        System.out.println("   (MCProtocolLib + Maven)      ");
        System.out.println("=================================");

        while (true) {
            try {
                String[] srv = resolveSrv(HOST);
                String targetHost = srv[0];
                int targetPort = Integer.parseInt(srv[1]);

                System.out.println("[BOT] Łączenie z " + targetHost + ":" + targetPort + " jako nick: " + USERNAME + "...");

                MinecraftProtocol protocol = new MinecraftProtocol(USERNAME);
                Session client = new TcpClientSession(targetHost, targetPort, protocol);

                client.addListener(new SessionAdapter() {
                    private boolean loggedIn = false;

                    @Override
                    public void packetReceived(PacketReceivedEvent event) {
                        String packetName = event.getPacket().getClass().getSimpleName();

                        if ((packetName.contains("Login") || packetName.contains("Join") || packetName.contains("PlayerPosition")) && !loggedIn) {
                            loggedIn = true;
                            System.out.println("[BOT] [OK] Połączono z serwerem!");
                            executeBotSequence(client);
                        }
                    }

                    @Override
                    public void disconnected(DisconnectedEvent event) {
                        System.out.println("[BOT] Rozłączono z serwerem. Powód: " + event.getReason());
                    }
                });

                client.connect();

                while (client.isConnected()) {
                    Thread.sleep(1000);
                }

            } catch (Exception e) {
                System.err.println("[BOT] Błąd połączenia: " + e.getMessage());
            }

            System.out.println("[BOT] Odczekam 10 sekund przed ponowną próbą...");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) {}
        }
    }

    private static void executeBotSequence(Session session) {
        new Thread(() -> {
            try {
                // Krok 1: Wpisanie komendy /login
                Thread.sleep(2000);
                System.out.println("[BOT] Wysyłam komendę: /login " + PASSWORD);
                session.send(new ServerboundChatCommandPacket("login " + PASSWORD));

                // Krok 2: Weryfikacja ruchowa
                Thread.sleep(2500);
                System.out.println("[BOT] Rozpoczynam ruchy weryfikacyjne...");
                
                double x = 0, y = 64, z = 0;

                session.send(new ServerboundMovePlayerPosRotPacket(x, y, z, -30.0f, 0.0f, true));
                Thread.sleep(500);
                session.send(new ServerboundMovePlayerPosRotPacket(x, y, z, 30.0f, 0.0f, true));
                Thread.sleep(500);

                System.out.println("[BOT] Idę do przodu przez 6 sekund...");
                for (int i = 0; i < 12; i++) {
                    z += 0.5;
                    session.send(new ServerboundMovePlayerPosRotPacket(x, y, z, 0.0f, 0.0f, true));
                    Thread.sleep(500);
                }

                // Krok 3: Użycie kompasu
                System.out.println("[BOT] Używam kompasu w dłoni...");
                session.send(new ServerboundUseItemPacket(Hand.MAIN_HAND, 0));
                
                // Krok 4: Wybór trybu AnarchiaSMP
                Thread.sleep(1500);
                System.out.println("[BOT] Dołączam na tryb AnarchiaSMP...");
                session.send(new ServerboundChatCommandPacket("anarchia"));

                System.out.println("[BOT] Sekwencja zakończona! Bot dołączył na AnarchiaSMP i utrzymuje AFK.");

            } catch (Exception e) {
                System.err.println("[BOT] Błąd podczas wykonywania sekwencji: " + e.getMessage());
            }
        }).start();
    }

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
}

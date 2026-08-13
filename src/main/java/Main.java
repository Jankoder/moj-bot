package com.bot;

import org.geysermc.mcprotocollib.network.Client;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.PacketReceivedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickType;

import java.util.Hashtable;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

public class Main {

    private static final String HOST = "anarchia.gg";
    private static final String USERNAME = "jankoder2";
    private static final String PASSWORD = "Krokodyl12!";

    private static volatile int activeContainerId = -1;

    public static void main(String[] args) {
        System.out.println("=================================");
        System.out.println("   AUTORSKI BOT AFK 1.20.4       ");
        System.out.println("   (MCProtocolLib + Maven)       ");
        System.out.println("=================================");

        while (true) {
            try {
                String[] srv = resolveSrv(HOST);
                String targetHost = srv[0];
                int targetPort = Integer.parseInt(srv[1]);

                System.out.println("[BOT] Łączenie z " + targetHost + ":" + targetPort + " jako nick: " + USERNAME + "...");

                MinecraftProtocol protocol = new MinecraftProtocol(USERNAME);
                Client client = new Client(targetHost, targetPort, protocol);

                activeContainerId = -1;

                client.getSession().addListener(new SessionAdapter() {
                    @Override
                    public void packetReceived(PacketReceivedEvent event) {
                        try {
                            Object packet = event.getPacket();
                            String packetName = packet.getClass().getSimpleName();

                            // 1. Wykrywanie otwarcia okna (menu / kompas)
                            if (packetName.contains("OpenScreen") || packetName.contains("ContainerOpen")) {
                                java.lang.reflect.Method getContainerId = packet.getClass().getMethod("getContainerId");
                                activeContainerId = (int) getContainerId.invoke(packet);
                                System.out.println("[BOT] [GUI] Otwarto menu ekwipunku. ID kontenera: " + activeContainerId);
                            }

                            // 2. Wykrywanie zawartości okna i szukanie "AnarchiaSMP"
                            if (packetName.contains("ContainerSetContent") || packetName.contains("WindowItems")) {
                                if (activeContainerId != -1) {
                                    java.lang.reflect.Method getContainerId = packet.getClass().getMethod("getContainerId");
                                    int id = (int) getContainerId.invoke(packet);
                                    if (id == activeContainerId) {
                                        java.lang.reflect.Method getItems = packet.getClass().getMethod("getItems");
                                        Iterable<?> items = (Iterable<?>) getItems.invoke(packet);
                                        
                                        int slotIndex = 0;
                                        for (Object item : items) {
                                            if (item != null) {
                                                String itemStr = item.toString().toLowerCase();
                                                if (itemStr.contains("anarchia") || itemStr.contains("anarchiasmp")) {
                                                    System.out.println("[BOT] [GUI] Znaleziono 'AnarchiaSMP' w slocie numer " + slotIndex + "!");
                                                    
                                                    // Klikamy w znaleziony slot
                                                    clickSlot(client.getSession(), activeContainerId, slotIndex, item);
                                                    
                                                    activeContainerId = -1;
                                                    break;
                                                }
                                            }
                                            slotIndex++;
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    @Override
                    public void disconnected(DisconnectedEvent event) {
                        System.out.println("[BOT] Rozłączono z serwerem. Powód: " + event.getReason());
                    }
                });

                client.connect();
                System.out.println("[BOT] [OK] Wysłano żądanie połączenia...");

                startBotSequence(client.getSession());

                while (client.getSession() != null && client.getSession().isConnected()) {
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

    private static void startBotSequence(Session session) {
        new Thread(() -> {
            try {
                System.out.println("[BOT] Czekam 4 sekundy na załadowanie świata...");
                Thread.sleep(4000);

                if (session == null || !session.isConnected()) return;

                System.out.println("[BOT] Wysyłam komendę: /login [HASŁO]");
                session.send(new ServerboundChatCommandPacket("login " + PASSWORD));

                System.out.println("[BOT] Czekam 4 sekundy po zalogowaniu na odblokowanie ekwipunku...");
                Thread.sleep(4000);

                if (session == null || !session.isConnected()) return;

                System.out.println("[BOT] Rozpoczynam ruchy weryfikacyjne...");
                double x = 0, y = 64, z = 0;

                session.send(new ServerboundMovePlayerPosRotPacket(true, false, x, y, z, -30.0f, 0.0f));
                Thread.sleep(500);
                session.send(new ServerboundMovePlayerPosRotPacket(true, false, x, y, z, 30.0f, 0.0f));
                Thread.sleep(500);

                System.out.println("[BOT] Idę do przodu...");
                for (int i = 0; i < 6; i++) {
                    z += 0.5;
                    session.send(new ServerboundMovePlayerPosRotPacket(true, false, x, y, z, 0.0f, 0.0f));
                    Thread.sleep(500);
                }

                // Użycie kompasu na samym końcu sekwencji
                System.out.println("[BOT] Używam kompasu w dłoni...");
                session.send(new ServerboundUseItemPacket(Hand.MAIN_HAND, 0));

                System.out.println("[BOT] Kompas został użyty. Oczekuję na menu serwera...");

            } catch (Exception e) {
                System.err.println("[BOT] Błąd sekwencji: " + e.getMessage());
            }
        }).start();
    }

    private static void clickSlot(Session session, int containerId, int slotIndex, Object clickedItem) {
        try {
            ServerboundContainerClickPacket packet = new ServerboundContainerClickPacket(
                containerId, 0, slotIndex, 0, ClickType.PICKUP, (org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack) clickedItem, null
            );
            session.send(packet);
            System.out.println("[BOT] Wysłano pakiet kliknięcia w slot " + slotIndex);
        } catch (Exception e) {
            System.err.println("[BOT] Błąd kliknięcia slotu: " + e.getMessage());
        }
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
                    return new String[]{targetHost, targetPort};
                }
            }
        } catch (Exception ignored) {}
        return new String[]{host, "25565"};
    }
}

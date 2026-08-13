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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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
        System.out.println("   AUTORSKI BOT AFK 1.21.4       ");
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

                            // 1. Wykrywanie otwarcia okna (kompas / menu)
                            if (packetName.contains("OpenScreen") || packetName.contains("ContainerOpen")) {
                                Method getContainerId = packet.getClass().getMethod("getContainerId");
                                activeContainerId = (int) getContainerId.invoke(packet);
                                System.out.println("[BOT] [GUI] Otwarto menu ekwipunku. ID kontenera: " + activeContainerId);
                            }

                            // 2. Wykrywanie zawartości okna i szukanie "AnarchiaSMP"
                            if (packetName.contains("ContainerSetContent") || packetName.contains("WindowItems")) {
                                if (activeContainerId != -1) {
                                    Method getContainerId = packet.getClass().getMethod("getContainerId");
                                    int id = (int) getContainerId.invoke(packet);
                                    if (id == activeContainerId) {
                                        Method getItems = packet.getClass().getMethod("getItems");
                                        Iterable<?> items = (Iterable<?>) getItems.invoke(packet);
                                        
                                        int slotIndex = 0;
                                        for (Object item : items) {
                                            if (item != null) {
                                                String itemStr = item.toString().toLowerCase();
                                                if (itemStr.contains("anarchia") || itemStr.contains("anarchiasmp")) {
                                                    System.out.println("[BOT] [GUI] Znaleziono 'AnarchiaSMP' w slocie numer " + slotIndex + "!");
                                                    
                                                    // Klikamy w znaleziony slot
                                                    clickSlotViaPacket(client.getSession(), activeContainerId, slotIndex, item);
                                                    
                                                    activeContainerId = -1; // Reset, aby nie klikać wielokrotnie
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

                // Uruchomienie sekwencji bota w osobnym wątku
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
                // Krok 1: Czekamy 4 sekundy na pełne załadowanie świata
                System.out.println("[BOT] Czekam 4 sekundy na załadowanie świata...");
                Thread.sleep(4000);

                if (session == null || !session.isConnected()) {
                    System.out.println("[BOT] Sesja nie jest aktywna, przerywam sekwencję.");
                    return;
                }

                // Krok 2: Wpisanie komendy /login
                System.out.println("[BOT] Wysyłam komendę: /login [HASŁO]");
                session.send(new ServerboundChatCommandPacket("login " + PASSWORD));

                // Krok 3: Czekamy 4 sekundy po zalogowaniu na załadowanie ekwipunku
                System.out.println("[BOT] Czekam 4 sekundy po zalogowaniu na odblokowanie ekwipunku...");
                Thread.sleep(4000);

                if (session == null || !session.isConnected()) {
                    return;
                }

                // Krok 4: Weryfikacja ruchowa (antycheat)
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

                // Krok 5: Użycie kompasu w dłoni (kliknięcie PPM)
                System.out.println("[BOT] Używam kompasu w dłoni...");
                session.send(new ServerboundUseItemPacket(Hand.MAIN_HAND, 0));

                System.out.println("[BOT] Kompas został użyty. Czekam na reakcję serwera...");

            } catch (Exception e) {
                System.err.println("[BOT] Błąd podczas wykonywania sekwencji: " + e.getMessage());
            }
        }).start();
    }

    private static void clickSlotViaPacket(Session session, int containerId, int slotIndex, Object clickedItem) {
        try {
            Class<?> clickPacketClass = Class.forName("org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket");
            Class<?> clickTypeClass = Class.forName("org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickType");
            Object clickTypePickup = clickTypeClass.getField("PICKUP").get(null);

            for (Constructor<?> constructor : clickPacketClass.getConstructors()) {
                try {
                    if (constructor.getParameterCount() >= 4) {
                        Object packet;
                        if (constructor.getParameterCount() == 6) {
                            packet = constructor.newInstance(containerId, 0, slotIndex, 0, clickTypePickup, clickedItem);
                        } else if (constructor.getParameterCount() == 7) {
                            packet = constructor.newInstance(containerId, 0, slotIndex, 0, clickTypePickup, clickedItem, null);
                        } else {
                            packet = constructor.newInstance(containerId, slotIndex, 0, clickTypePickup);
                        }
                        session.send((org.geysermc.mcprotocollib.network.packet.Packet) packet);
                        System.out.println("[BOT] Wysłano pakiet kliknięcia w slot " + slotIndex);
                        return;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.err.println("[BOT] Nie udało się wysłać kliknięcia: " + e.getMessage());
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
                    System.out.println("[DNS] Znaleziono rekord SRV: " + targetHost + ":" + targetPort);
                    return new String[]{targetHost, targetPort};
                }
            }
        } catch (Exception ignored) {}
        return new String[]{host, "25565"};
    }
}

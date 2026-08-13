package com.bot;

import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
        System.out.println("   AUTORSKI BOT AFK 1.21.x       ");
        System.out.println("   (MCProtocolLib + Maven)       ");
        System.out.println("=================================");

        while (true) {
            try {
                String[] srv = resolveSrv(HOST);
                String targetHost = srv[0];
                int targetPort = Integer.parseInt(srv[1]);

                System.out.println("[BOT] Łączenie z " + targetHost + ":" + targetPort + " jako nick: " + USERNAME + "...");

                MinecraftProtocol protocol = new MinecraftProtocol(USERNAME);
                Session session = createSession(targetHost, targetPort, protocol);

                activeContainerId = -1;

                session.addListener(new SessionAdapter() {
                    @Override
                    public void packetReceived(Session session, Packet packet) {
                        try {
                            String packetName = packet.getClass().getSimpleName();

                            // 1. Wykrywanie otwarcia okna (menu / kompas)
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
                                                    
                                                    clickSlot(session, activeContainerId, slotIndex, item);
                                                    
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

                connectSession(session);
                System.out.println("[BOT] [OK] Wysłano żądanie połączenia...");

                startBotSequence(session);

                while (session.isConnected()) {
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

                if (!session.isConnected()) return;

                System.out.println("[BOT] Wysyłam komendę: /login [HASŁO]");
                session.send(new ServerboundChatCommandPacket("login " + PASSWORD));

                System.out.println("[BOT] Czekam 4 sekundy po zalogowaniu na odblokowanie ekwipunku...");
                Thread.sleep(4000);

                if (!session.isConnected()) return;

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

                System.out.println("[BOT] Używam kompasu w dłoni...");
                session.send(new ServerboundUseItemPacket(Hand.MAIN_HAND, 0, 0.0f, 0.0f));

                System.out.println("[BOT] Kompas został użyty. Oczekuję na menu serwera...");

            } catch (Exception e) {
                System.err.println("[BOT] Błąd sekwencji: " + e.getMessage());
            }
        }).start();
    }

    private static void clickSlot(Session session, int containerId, int slotIndex, Object clickedItem) {
        try {
            // 1. Znajdź wartość ContainerActionType
            Class<?> actionTypeClass = Class.forName("org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType");
            Object actionType = null;
            Object[] actionConstants = actionTypeClass.getEnumConstants();
            if (actionConstants != null) {
                for (Object c : actionConstants) {
                    String name = c.toString().toUpperCase();
                    if (name.contains("CLICK") || name.contains("PICKUP")) {
                        actionType = c;
                        break;
                    }
                }
                if (actionType == null && actionConstants.length > 0) {
                    actionType = actionConstants[0];
                }
            }

            // 2. Znajdź wartość ContainerAction
            Object containerAction = null;
            String[] actionClassNames = new String[] {
                "org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerAction$ClickItemAction",
                "org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction",
                "org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerAction",
                "org.geysermc.mcprotocollib.protocol.data.game.inventory.DefaultContainerAction"
            };
            for (String acName : actionClassNames) {
                try {
                    Class<?> acClass = Class.forName(acName);
                    Object[] constants = acClass.getEnumConstants();
                    if (constants != null && constants.length > 0) {
                        for (Object c : constants) {
                            if (c.toString().toUpperCase().contains("LEFT")) {
                                containerAction = c;
                                break;
                            }
                        }
                        if (containerAction == null) {
                            containerAction = constants[0];
                        }
                        break;
                    }
                } catch (ClassNotFoundException ignored) {}
            }

            // 3. Przygotuj HashedStack
            Object hashedStack = null;
            try {
                Class<?> hashedStackClass = Class.forName("org.geysermc.mcprotocollib.protocol.data.game.item.HashedStack");
                for (Constructor<?> cons : hashedStackClass.getConstructors()) {
                    if (cons.getParameterCount() == 0) {
                        hashedStack = cons.newInstance();
                        break;
                    } else if (cons.getParameterCount() == 1 && clickedItem != null && cons.getParameterTypes()[0].isAssignableFrom(clickedItem.getClass())) {
                        hashedStack = cons.newInstance(clickedItem);
                        break;
                    }
                }
                if (hashedStack == null) {
                    for (Constructor<?> cons : hashedStackClass.getConstructors()) {
                        if (cons.getParameterCount() == 3) {
                            hashedStack = cons.newInstance(0, 0, null);
                            break;
                        } else if (cons.getParameterCount() == 2) {
                            hashedStack = cons.newInstance(0, 0);
                            break;
                        }
                    }
                }
            } catch (ClassNotFoundException ignored) {}

            // 4. Utwórz pakiet ServerboundContainerClickPacket
            Object packet = null;
            Constructor<?>[] constructors = ServerboundContainerClickPacket.class.getConstructors();
            for (Constructor<?> cons : constructors) {
                Class<?>[] pTypes = cons.getParameterTypes();
                Object[] args = new Object[pTypes.length];
                boolean valid = true;

                for (int i = 0; i < pTypes.length; i++) {
                    Class<?> p = pTypes[i];
                    if (p == int.class || p == Integer.class) {
                        if (i == 0) args[i] = containerId;
                        else if (i == 2) args[i] = slotIndex;
                        else args[i] = 0;
                    } else if (actionType != null && p.isAssignableFrom(actionType.getClass())) {
                        args[i] = actionType;
                    } else if (containerAction != null && p.isAssignableFrom(containerAction.getClass())) {
                        args[i] = containerAction;
                    } else if (p.getName().contains("ContainerAction") && containerAction != null) {
                        args[i] = containerAction;
                    } else if (p.getName().contains("HashedStack")) {
                        args[i] = hashedStack;
                    } else if (p == java.util.Map.class || p.getName().contains("Map")) {
                        try {
                            args[i] = Class.forName("it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap").getDeclaredConstructor().newInstance();
                        } catch (Exception e) {
                            args[i] = new java.util.HashMap<>();
                        }
                    } else {
                        valid = false;
                    }
                }

                if (valid) {
                    try {
                        packet = cons.newInstance(args);
                        break;
                    } catch (Exception ignored) {}
                }
            }

            if (packet != null) {
                session.send((Packet) packet);
                System.out.println("[BOT] Wysłano pakiet kliknięcia w slot " + slotIndex);
            } else {
                System.err.println("[BOT] Błąd: Nie odnaleziono pasującego konstruktora dla kliknięcia.");
            }

        } catch (Exception e) {
            System.err.println("[BOT] Błąd kliknięcia slotu: " + e.getMessage());
        }
    }

    private static Session createSession(String host, int port, MinecraftProtocol protocol) throws Exception {
        // Lista prawdopodobnych nazw klas sesji
        String[] candidateClasses = new String[] {
            "org.geysermc.mcprotocollib.network.session.TcpSession",
            "org.geysermc.mcprotocollib.network.tcp.TcpSession",
            "org.geysermc.mcprotocollib.network.session.ClientSession",
            "org.geysermc.mcprotocollib.network.client.ClientSession",
            "org.geysermc.mcprotocollib.network.client.TcpClientSession",
            "org.geysermc.mcprotocollib.network.tcp.TcpClientSession",
            "org.geysermc.mcprotocollib.network.session.TcpClientSession",
            "org.geysermc.mcprotocollib.network.TcpClientSession",
            "org.geysermc.mcprotocollib.network.TcpSession",
            "org.geysermc.mcprotocollib.network.ClientSession",
            "org.geysermc.mcprotocollib.protocol.ClientSession",
            "org.geysermc.mcprotocollib.protocol.TcpClientSession"
        };

        for (String className : candidateClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                for (Constructor<?> cons : clazz.getConstructors()) {
                    if (cons.getParameterCount() == 3) {
                        try {
                            return (Session) cons.newInstance(host, port, protocol);
                        } catch (Exception ignored) {}
                    }
                }
            } catch (ClassNotFoundException ignored) {}
        }

        // Skanowanie biblioteki JAR, jeśli nazwy statyczne zawiodą
        java.net.URL location = Session.class.getProtectionDomain().getCodeSource().getLocation();
        if (location != null) {
            try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(new java.io.File(location.toURI()))) {
                java.util.Enumeration<java.util.jar.JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    java.util.jar.JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.endsWith(".class") && name.startsWith("org/geysermc/mcprotocollib/")) {
                        String className = name.replace('/', '.').substring(0, name.length() - 6);
                        try {
                            Class<?> clazz = Class.forName(className);
                            if (Session.class.isAssignableFrom(clazz) && !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers())) {
                                for (Constructor<?> cons : clazz.getConstructors()) {
                                    if (cons.getParameterCount() == 3) {
                                        System.out.println("[BOT] Wykryto klasę sesji: " + className);
                                        return (Session) cons.newInstance(host, port, protocol);
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Exception e) {
                System.err.println("[BOT] Błąd podczas skanowania JAR: " + e.getMessage());
            }
        }

        throw new IllegalStateException("Nie odnaleziono odpowiedniej klasy sesji w bibliotece MCProtocolLib.");
    }

    private static void connectSession(Session session) throws Exception {
        try {
            Method m = session.getClass().getMethod("connect");
            m.invoke(session);
        } catch (NoSuchMethodException e) {
            try {
                Method m = session.getClass().getMethod("connect", boolean.class);
                m.invoke(session, true);
            } catch (NoSuchMethodException e2) {
                Method m = session.getClass().getMethod("connect", boolean.class, boolean.class);
                m.invoke(session, true, false);
            }
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

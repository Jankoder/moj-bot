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
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

public class Main {

    private static final String HOST = "anarchia.gg";
    private static final String USERNAME = "jankoder2";
    private static final String PASSWORD = "Krokodyl12!";

    private static volatile int activeContainerId = -1;
    private static volatile boolean waitingForGui = false;

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
                waitingForGui = false;

                session.addListener(new SessionAdapter() {
                    @Override
                    public void packetReceived(Session session, Packet packet) {
                        try {
                            String packetName = packet.getClass().getSimpleName();

                            // Podgląd pakietów w konsoli + wyciąganie wiadomości z czatu
                            if (waitingForGui) {
                                if (packetName.contains("SystemChat") || packetName.contains("Chat")) {
                                    String chatText = extractChatText(packet);
                                    System.out.println("[BOT] [CZAT SERWERA] -> " + chatText);
                                } else {
                                    System.out.println("[BOT] [ODBIEG-PAKIET] -> " + packetName);
                                }
                            }

                            // 1. Wykrywanie otwarcia okna (menu / kompas)
                            if (packetName.contains("OpenScreen") || packetName.contains("ContainerOpen") || packetName.contains("OpenWindow")) {
                                Method getContainerId = packet.getClass().getMethod("getContainerId");
                                activeContainerId = (int) getContainerId.invoke(packet);
                                System.out.println("[BOT] [GUI] Otwarto menu ekwipunku! ID kontenera: " + activeContainerId);
                            }

                            // 2. Wykrywanie zawartości okna i szukanie "AnarchiaSMP"
                            if (packetName.contains("ContainerSetContent") || packetName.contains("WindowItems") || packetName.contains("SetContainerContent")) {
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
                                                if (itemStr.contains("anarchia") || itemStr.contains("anarchiasmp") || itemStr.contains("smp")) {
                                                    System.out.println("[BOT] [GUI] Znaleziono 'AnarchiaSMP' w slocie numer " + slotIndex + "!");
                                                    
                                                    clickSlot(session, activeContainerId, slotIndex, item);
                                                    
                                                    activeContainerId = -1;
                                                    waitingForGui = false;
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
                System.out.println("[BOT] Czekam 1 sekundę na wejście do lobby...");
                Thread.sleep(1000);

                if (!session.isConnected()) return;

                System.out.println("[BOT] Wysyłam komendę: /login [HASŁO]");
                session.send(new ServerboundChatCommandPacket("login " + PASSWORD));

                System.out.println("[BOT] Czekam 10 sekund na załadowanie świata, teleportację i odblokowanie EQ...");
                Thread.sleep(10000);

                if (!session.isConnected()) return;

                System.out.println("[BOT] Rozpoczynam ruchy weryfikacyjne...");
                double x = 0, y = 64, z = 0;

                session.send(new ServerboundMovePlayerPosRotPacket(true, false, x, y, z, -30.0f, 0.0f));
                Thread.sleep(300);
                session.send(new ServerboundMovePlayerPosRotPacket(true, false, x, y, z, 30.0f, 0.0f));
                Thread.sleep(300);

                System.out.println("[BOT] Idę do przodu przez dokładnie 6 sekund...");
                for (int i = 0; i < 20; i++) {
                    z += 0.2;
                    session.send(new ServerboundMovePlayerPosRotPacket(true, false, x, y, z, 0.0f, 0.0f));
                    Thread.sleep(300);
                }

                // USTAWIONO SLOT 4 (DLA 5. OKIENKA NA EKRANIE)
                System.out.println("[BOT] Wybieram slot 4 (5. okienko z kompasem)...");
                session.send(new ServerboundSetCarriedItemPacket(4));
                Thread.sleep(500);

                System.out.println("[BOT] Używam kompasu w dłoni (z zamachem ręki)...");
                waitingForGui = true;
                
                // Machnięcie ręką + Użycie przedmiotu
                session.send(new ServerboundSwingPacket(Hand.MAIN_HAND));
                session.send(new ServerboundUseItemPacket(Hand.MAIN_HAND, 0, 0.0f, 0.0f));

                System.out.println("[BOT] Kompas został użyty. Oczekuję na menu serwera...");

            } catch (Exception e) {
                System.err.println("[BOT] Błąd sekwencji: " + e.getMessage());
            }
        }).start();
    }

    private static String extractChatText(Packet packet) {
        try {
            Method getContent = packet.getClass().getMethod("getContent");
            Object content = getContent.invoke(packet);
            if (content != null) {
                return content.toString();
            }
        } catch (Exception ignored) {}
        return packet.toString();
    }

    private static void clickSlot(Session session, int containerId, int slotIndex, Object clickedItem) {
        try {
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
                    } else if (p == Map.class || p.getName().contains("Map")) {
                        try {
                            args[i] = Class.forName("it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap").getDeclaredConstructor().newInstance();
                        } catch (Exception e) {
                            args[i] = new HashMap<>();
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
        System.out.println("[BOT] Szukam odpowiedniej klasy w pliku JAR...");
        List<Class<?>> classes = scanMcProtocolLibClasses();
        System.out.println("[BOT] Przeszukano " + classes.size() + " klas związanych z sesją.");

        classes.sort((c1, c2) -> {
            String n1 = c1.getSimpleName();
            String n2 = c2.getSimpleName();
            if (n1.contains("ClientNetworkSession")) return -1;
            if (n2.contains("ClientNetworkSession")) return 1;
            if (n1.contains("ClientSession")) return -1;
            if (n2.contains("ClientSession")) return 1;
            return n1.compareTo(n2);
        });

        for (Class<?> clazz : classes) {
            if (!clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers())) {
                Session session = tryInstantiateSessionClass(clazz, host, port, protocol);
                if (session != null) {
                    injectExecutors(session);
                    System.out.println("[BOT] Utworzono sesję z klasy: " + clazz.getName());
                    return session;
                }
            }
        }

        throw new IllegalStateException("Nie odnaleziono odpowiedniej klasy sesji w bibliotece MCProtocolLib.");
    }

    private static Session tryInstantiateSessionClass(Class<?> clazz, String host, int port, MinecraftProtocol protocol) {
        for (Constructor<?> cons : clazz.getConstructors()) {
            Class<?>[] types = cons.getParameterTypes();
            Object[] args = new Object[types.length];

            boolean hostAssigned = false;
            boolean portAssigned = false;

            for (int i = 0; i < types.length; i++) {
                Class<?> t = types[i];

                if (Executor.class.isAssignableFrom(t) || t.getName().contains("Executor")) {
                    args[i] = Executors.newSingleThreadExecutor();
                } else if (Proxy.class.isAssignableFrom(t)) {
                    args[i] = Proxy.NO_PROXY;
                } else if (t == String.class) {
                    if (!hostAssigned) {
                        args[i] = host;
                        hostAssigned = true;
                    } else {
                        args[i] = null;
                    }
                } else if (t == int.class || t == Integer.class) {
                    if (!portAssigned) {
                        args[i] = port;
                        portAssigned = true;
                    } else {
                        args[i] = 0;
                    }
                } else if (t.isAssignableFrom(protocol.getClass()) || t.getName().contains("Protocol")) {
                    args[i] = protocol;
                } else if (SocketAddress.class.isAssignableFrom(t) || InetSocketAddress.class.isAssignableFrom(t)) {
                    if (!hostAssigned) {
                        args[i] = new InetSocketAddress(host, port);
                        hostAssigned = true;
                        portAssigned = true;
                    } else {
                        args[i] = null;
                    }
                } else if (t == boolean.class || t == Boolean.class) {
                    args[i] = false;
                } else if (t.isPrimitive()) {
                    if (t == long.class) args[i] = 0L;
                    else if (t == float.class) args[i] = 0.0f;
                    else if (t == double.class) args[i] = 0.0d;
                    else args[i] = 0;
                } else {
                    args[i] = null;
                }
            }

            try {
                cons.setAccessible(true);
                Object obj = cons.newInstance(args);
                if (obj instanceof Session) {
                    return (Session) obj;
                }
            } catch (Throwable ignored) {}
        }

        return null;
    }

    private static void injectExecutors(Object obj) {
        if (obj == null) return;
        Class<?> current = obj.getClass();
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (Executor.class.isAssignableFrom(f.getType()) || f.getType().getName().contains("Executor")) {
                    try {
                        f.setAccessible(true);
                        if (f.get(obj) == null) {
                            f.set(obj, Executors.newSingleThreadExecutor());
                        }
                    } catch (Throwable ignored) {}
                }
            }
            current = current.getSuperclass();
        }
    }

    private static List<Class<?>> scanMcProtocolLibClasses() {
        List<Class<?>> classes = new ArrayList<>();
        try {
            Set<URL> urls = new HashSet<>();
            try { urls.add(Session.class.getProtectionDomain().getCodeSource().getLocation()); } catch (Throwable ignored) {}
            try { urls.add(Main.class.getProtectionDomain().getCodeSource().getLocation()); } catch (Throwable ignored) {}

            for (URL location : urls) {
                if (location == null) continue;
                java.io.File file = new java.io.File(location.toURI());
                if (file.exists() && file.isFile() && file.getName().endsWith(".jar")) {
                    try (JarFile jarFile = new JarFile(file)) {
                        Enumeration<JarEntry> entries = jarFile.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (name.endsWith(".class") && name.contains("mcprotocollib")) {
                                String className = name.replace('/', '.').substring(0, name.length() - 6);
                                try {
                                    Class<?> c = Class.forName(className);
                                    if (Session.class.isAssignableFrom(c) || className.toLowerCase().contains("session")) {
                                        classes.add(c);
                                    }
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            System.err.println("[BOT] Błąd skanowania klas: " + e.getMessage());
        }
        return classes;
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

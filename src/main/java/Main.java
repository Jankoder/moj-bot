package com.bot;

import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;

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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

public class Main {

    private static final String HOST = "anarchia.gg";
    private static final String USERNAME = "jankoder2";
    private static final String PASSWORD = "Krokodyl12!"; // Podmień na własne hasło

    private static volatile int activeContainerId = -1;
    private static volatile boolean resourcePackFinished = false;
    private static volatile boolean compassClicked = false;
    private static final AtomicInteger sequenceCounter = new AtomicInteger(0);

    // Pozycja i celownik bota
    private static volatile double currentX = 0;
    private static volatile double currentY = 64;
    private static volatile double currentZ = 0;
    private static volatile float currentYaw = 0;
    private static volatile float currentPitch = 0;
    private static volatile boolean positionReceived = false;
    private static volatile boolean isVerifying = false;

    // Do blokowania spamu na czacie
    private static volatile String lastChatMessage = "";

    public static void main(String[] args) {
        System.out.println("=================================");
        System.out.println("   AUTORSKI BOT AFK 1.21.x       ");
        System.out.println("=================================");

        while (true) {
            try {
                String[] srv = resolveSrv(HOST);
                String targetHost = srv[0];
                int targetPort = Integer.parseInt(srv[1]);

                System.out.println("[BOT] Łączenie z " + targetHost + ":" + targetPort + " jako: " + USERNAME + "...");

                MinecraftProtocol protocol = new MinecraftProtocol(USERNAME);
                Session session = createSession(targetHost, targetPort, protocol);

                activeContainerId = -1;
                resourcePackFinished = false;
                compassClicked = false;
                positionReceived = false;
                isVerifying = false;
                lastChatMessage = "";

                session.addListener(new SessionAdapter() {
                    @Override
                    public void packetReceived(Session session, Packet packet) {
                        try {
                            String packetName = packet.getClass().getSimpleName();

                            // 0. POTWIERDZANIE TELEPORTACJI I AKTUALIZACJA POZYCJI
                            if (packetName.contains("PlayerPosition") || packetName.contains("PosRot") || packetName.contains("LookAt")) {
                                handlePlayerPositionPacket(session, packet);
                            }

                            // 1. PACZKA ZASOBÓW
                            if (packetName.contains("ResourcePack")) {
                                System.out.println("[BOT] [RESOURCE PACK] Obsługa paczki zasobów...");
                                handleResourcePack(session, packet);
                            }

                            // 2. OTWARCIE MENU Z TRYBAMI
                            if (packetName.contains("OpenScreen") || packetName.contains("ContainerOpen") || packetName.contains("OpenWindow")) {
                                Method getContainerId = packet.getClass().getMethod("getContainerId");
                                int cid = (int) getContainerId.invoke(packet);

                                if (compassClicked && cid > 0) {
                                    activeContainerId = cid;
                                    System.out.println("[BOT] [GUI] Otwarto menu! ID: " + activeContainerId);
                                }
                            }

                            // 3. KLIKNIĘCIE W TRYB
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
                                                if (itemStr.contains("anarchia") || itemStr.contains("smp") || itemStr.contains("graj")) {
                                                    System.out.println("[BOT] [GUI] Klikam AnarchiaSMP w slocie " + slotIndex);
                                                    Thread.sleep(ThreadLocalRandom.current().nextLong(400, 800));
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

                            // 4. CZAT SERWERA I NATYCHMIASTOWA REAKCJA NA WERYFIKACJĘ
                            if (packetName.contains("SystemChat") || packetName.contains("Chat")) {
                                String cleanedText = cleanChatMessage(packet.toString());

                                if (!cleanedText.isEmpty() && !cleanedText.equals(lastChatMessage)) {
                                    lastChatMessage = cleanedText;
                                    System.out.println("[BOT] [CZAT] " + cleanedText);
                                }

                                String rawPacket = packet.toString().toLowerCase();
                                if (rawPacket.contains("weryfikacja") || rawPacket.contains("poruszać") || rawPacket.contains("pozycji") || rawPacket.contains("ruszaj")) {
                                    if (!isVerifying) {
                                        // KLUCZOWE ZABEZPIECZENIE: Jeśli bot nie dostał jeszcze pakietu pozycji ze świata,
                                        // to czekamy i blokujemy start ruchu, żeby nie wysłać kordów 0, 0, 0
                                        if (!positionReceived) {
                                            System.out.println("[BOT] [ANTY-CHEAT] Serwer żąda weryfikacji, ale jeszcze nie znamy naszej pozycji! Czekam...");
                                            return; 
                                        }
                                        
                                        isVerifying = true;
                                        new Thread(() -> performMovementVerification(session)).start();
                                    }
                                }
                            }

                        } catch (Exception ignored) {}
                    }

                    @Override
                    public void disconnected(DisconnectedEvent event) {
                        System.out.println("[BOT] Rozłączono. Powód: " + event.getReason());
                    }
                });

                connectSession(session);
                System.out.println("[BOT] [OK] Połączono!");

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

    private static void handlePlayerPositionPacket(Session session, Packet packet) {
        try {
            boolean updated = false;
            
            // W nowym MCProtocolLib pakiety pozycji mogą mieć kordy w obiekcie lub bezpośrednio.
            // Skanujemy wszystkie metody pakietu, żeby na 100% wyciągnąć współrzędne X, Y, Z.
            for (Method m : packet.getClass().getMethods()) {
                if (m.getParameterCount() == 0) {
                    String name = m.getName().toLowerCase();
                    
                    // Szukamy metod zwracających współrzędne (Double)
                    if (name.equals("getx") || name.equals("x")) { 
                        currentX = ((Number) m.invoke(packet)).doubleValue(); 
                        updated = true; 
                    }
                    if (name.equals("gety") || name.equals("y")) { 
                        currentY = ((Number) m.invoke(packet)).doubleValue(); 
                        updated = true; 
                    }
                    if (name.equals("getz") || name.equals("z")) { 
                        currentZ = ((Number) m.invoke(packet)).doubleValue(); 
                        updated = true; 
                    }
                    
                    // Szukamy kątów patrzenia głowy (Float)
                    if (name.equals("getyaw") || name.equals("yaw")) { 
                        currentYaw = ((Number) m.invoke(packet)).floatValue(); 
                    }
                    if (name.equals("getpitch") || name.equals("pitch")) { 
                        currentPitch = ((Number) m.invoke(packet)).floatValue(); 
                    }
                }
            }

            // Jeśli pakiety przechowują pozycję wewnątrz wektora/obiektu (np. Vector3d)
            if (!updated) {
                for (Method m : packet.getClass().getMethods()) {
                    if (m.getParameterCount() == 0 && (m.getReturnType().getSimpleName().contains("Vector") || m.getReturnType().getSimpleName().contains("Pos"))) {
                        Object vec = m.invoke(packet);
                        if (vec != null) {
                            for (Method vm : vec.getClass().getMethods()) {
                                if (vm.getParameterCount() == 0) {
                                    String vName = vm.getName().toLowerCase();
                                    if (vName.equals("getx") || vName.equals("x")) { currentX = ((Number) vm.invoke(vec)).doubleValue(); updated = true; }
                                    if (vName.equals("gety") || vName.equals("y")) { currentY = ((Number) vm.invoke(vec)).doubleValue(); updated = true; }
                                    if (vName.equals("getz") || vName.equals("z")) { currentZ = ((Number) vm.invoke(vec)).doubleValue(); updated = true; }
                                }
                            }
                        }
                    }
                }
            }

            // Sukces! Bot w końcu poprawnie odczytał kordy świata z pakietu serwera
            if (updated && !positionReceived) {
                positionReceived = true;
                System.out.printf("[BOT] [SUKCES] Odczytano pozycję lobby: X=%.2f, Y=%.2f, Z=%.2f\n", currentX, currentY, currentZ);
            }

            // Szukamy ID teleportu, aby wysłać potwierdzenie (wymagane przez anticheat serwera)
            for (Method m : packet.getClass().getMethods()) {
                if (m.getName().toLowerCase().contains("teleportid") && m.getParameterCount() == 0) {
                    int teleportId = (int) m.invoke(packet);
                    sendTeleportConfirm(session, teleportId);
                    
                    // Natychmiast odsyłamy serwerowi naszą nową pozycję w świecie jako potwierdzenie stanięcia na ziemi
                    sendMovePacket(session, currentX, currentY, currentZ, currentYaw, currentPitch, true);
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[BOT] Błąd podczas parsowania pakietu pozycji: " + e.getMessage());
        }
    }

    private static void sendTeleportConfirm(Session session, int teleportId) {
        try {
            Class<?> confirmClass = Class.forName("org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket");
            Constructor<?> cons = confirmClass.getConstructor(int.class);
            Packet confirmPacket = (Packet) cons.newInstance(teleportId);
            session.send(confirmPacket);
        } catch (Exception e) {
            try {
                Class<?> confirmClass = Class.forName("org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundAcceptTeleportationPacket");
                Constructor<?> cons = confirmClass.getConstructor(int.class);
                Packet confirmPacket = (Packet) cons.newInstance(teleportId);
                session.send(confirmPacket);
            } catch (Exception ignored) {}
        }
    }

    private static void startBotSequence(Session session) {
        new Thread(() -> {
            try {
                Thread.sleep(1800);
                if (!session.isConnected()) return;
                sendBrandPayload(session);
                sendClientInformation(session);
                System.out.println("[BOT] Wysyłam komendę: /login [HASŁO]");
                session.send(new ServerboundChatCommandPacket("login " + PASSWORD));

                long startWait = System.currentTimeMillis();
                while (!resourcePackFinished && (System.currentTimeMillis() - startWait < 12000)) {
                    if (!session.isConnected()) return;
                    if (isVerifying) break;
                    Thread.sleep(200);
                }
                if (!session.isConnected()) return;

                startWait = System.currentTimeMillis();
                while (!positionReceived && (System.currentTimeMillis() - startWait < 6000)) {
                    if (!session.isConnected()) return;
                    if (isVerifying) break;
                    Thread.sleep(100);
                }
                Thread.sleep(1200);
                if (!session.isConnected()) return;

                if (!isVerifying) {
                    isVerifying = true;
                    performMovementVerification(session);
                } else {
                    while (isVerifying && session.isConnected()) {
                        Thread.sleep(100);
                    }
                }
                if (!session.isConnected()) return;

                System.out.println("[BOT] Wybieram slot 4 (kompas)...");
                session.send(new ServerboundSetCarriedItemPacket(4));
                Thread.sleep(1100);
                if (!session.isConnected()) return;

                System.out.println("[BOT] Klikam kompasem...");
                compassClicked = true;
                int seq = sequenceCounter.incrementAndGet();
                session.send(new ServerboundSwingPacket(Hand.MAIN_HAND));
                sendUseItemPacket(session, seq);
            } catch (Exception e) {
                System.err.println("[BOT] Błąd w sekwencji bota: " + e.getMessage());
            }
        }).start();
    }

    private static void performMovementVerification(Session session) {
        System.out.println("[BOT] [RUCH] Uruchamiam bezpieczną emulację ruchu Vanilla...");
        
        // Losujemy kierunek, w którym bot zacznie iść
        float targetYaw = currentYaw + ThreadLocalRandom.current().nextFloat(-90f, 90f);
        float targetPitch = ThreadLocalRandom.current().nextFloat(-5f, 5f);

        for (int i = 0; i < 180; i++) {
            if (!session.isConnected()) {
                isVerifying = false;
                return;
            }

            // Co 20 ticków (1 sekunda) gracz decyduje się na lekką korektę myszką
            if (i % 20 == 0) {
                targetYaw = currentYaw + ThreadLocalRandom.current().nextFloat(-60f, 60f);
                targetPitch = ThreadLocalRandom.current().nextFloat(-5f, 5f);
            }

            // Płynne, ludzkie zbliżanie się celownika do wyznaczonego punktu
            currentYaw += (targetYaw - currentYaw) * 0.15f;
            currentPitch += (targetPitch - currentPitch) * 0.15f;

            // Permanentny mikro-szum myszki (jitter) w KAŻDYM pakiecie (kluczowe dla antycheata)
            currentYaw += ThreadLocalRandom.current().nextFloat(-0.2f, 0.2f);
            currentPitch += ThreadLocalRandom.current().nextFloat(-0.1f, 0.1f);

            // Obliczanie pozycji chodzenia (bardzo mała, naturalna prędkość)
            double currentSpeed = ThreadLocalRandom.current().nextDouble(0.07, 0.11);
            double rad = Math.toRadians(currentYaw);
            currentX -= Math.sin(rad) * currentSpeed;
            currentZ += Math.cos(rad) * currentSpeed;

            // Losowe kliknięcie ręką (animacja)
            if (ThreadLocalRandom.current().nextInt(100) < 5) {
                session.send(new ServerboundSwingPacket(Hand.MAIN_HAND));
            }

            // WYSYŁAMY PEŁNY PAKIET POZYCJI I OBROTU JEDNOCZEŚNIE
            // Flaga 'true' na końcu oznacza, że bot stoi na bloku (onGround)
            sendMovePacket(session, currentX, currentY, currentZ, currentYaw, currentPitch, true);

            // Całkowite zaburzenie timingów bota poprzez losowy czas oczekiwania (jitter sieciowy)
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(46, 54));
            } catch (InterruptedException e) {
                break;
            }
        }

        System.out.println("[BOT] [RUCH] Sekwencja weryfikacji zakończona pomyślnie!");
        isVerifying = false;
    }

    private static void sendClientInformation(Session session) {
        try {
            Class<?> clientInfoClass = Class.forName("org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientInformationPacket");
            for (Constructor<?> cons : clientInfoClass.getConstructors()) {
                Class<?>[] pTypes = cons.getParameterTypes();
                Object[] args = new Object[pTypes.length];
                boolean valid = true;
                for (int i = 0; i < pTypes.length; i++) {
                    Class<?> p = pTypes[i];
                    if (p == String.class) {
                        args[i] = "pl_pl";
                    } else if (p == int.class || p == Integer.class) {
                        args[i] = 8;
                    } else if (p == boolean.class || p == Boolean.class) {
                        args[i] = true;
                    } else if (p.isEnum()) {
                        Object[] constants = p.getEnumConstants();
                        if (constants != null && constants.length > 0) {
                            args[i] = constants[0];
                        } else {
                            valid = false;
                        }
                    } else if (p == byte.class || p == Byte.class) {
                        args[i] = (byte) 127;
                    } else {
                        args[i] = null;
                    }
                }
                if (valid) {
                    try {
                        session.send((Packet) cons.newInstance(args));
                        System.out.println("[BOT] Wysłano informacje o kliencie (ClientInformation)");
                        return;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    private static void sendMovePacket(Session session, double x, double y, double z, float yaw, float pitch, boolean onGround) {
        try {
            Class<?> moveClass = Class.forName("org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket");
            for (Constructor<?> cons : moveClass.getConstructors()) {
                Class<?>[] types = cons.getParameterTypes();
                if (types.length == 6 && types[0] == double.class) {
                    session.send((Packet) cons.newInstance(x, y, z, yaw, pitch, onGround));
                    return;
                } else if (types.length == 7 && types[0] == boolean.class) {
                    session.send((Packet) cons.newInstance(onGround, false, x, y, z, yaw, pitch));
                    return;
                } else if (types.length == 7 && types[0] == double.class) {
                    session.send((Packet) cons.newInstance(x, y, z, yaw, pitch, onGround, false));
                    return;
                } else if (types.length == 8) {
                    session.send((Packet) cons.newInstance(x, y, z, yaw, pitch, onGround, false, false));
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    private static String cleanChatMessage(String raw) {
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        while ((idx = raw.indexOf("content=\"", idx)) != -1) {
            idx += 9;
            int end = raw.indexOf("\"", idx);
            if (end != -1) {
                String part = raw.substring(idx, end);
                if (!part.isEmpty()) {
                    sb.append(part);
                }
                idx = end;
            }
        }
        return sb.toString().trim();
    }

    private static void sendUseItemPacket(Session session, int sequence) {
        try {
            session.send(new ServerboundUseItemPacket(Hand.MAIN_HAND, sequence, currentYaw, currentPitch));
        } catch (Exception e) {
            System.err.println("[BOT] Błąd ServerboundUseItemPacket: " + e.getMessage());
        }
    }

    private static void sendBrandPayload(Session session) {
        try {
            byte[] data = new byte[] { 0x07, 'v', 'a', 'n', 'i', 'l', 'l', 'a' };
            Class<?> customPayloadClass = Class.forName("org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundCustomPayloadPacket");
            for (Constructor<?> cons : customPayloadClass.getConstructors()) {
                Class<?>[] pTypes = cons.getParameterTypes();
                if (pTypes.length == 2) {
                    Object arg1 = null;
                    if (pTypes[0] == String.class) {
                        arg1 = "minecraft:brand";
                    } else if (pTypes[0].getName().contains("Key") || pTypes[0].getName().contains("ResourceLocation")) {
                        Method keyMethod = pTypes[0].getMethod("key", String.class, String.class);
                        arg1 = keyMethod.invoke(null, "minecraft", "brand");
                    }
                    if (arg1 != null && pTypes[1] == byte[].class) {
                        session.send((Packet) cons.newInstance(arg1, data));
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static void handleResourcePack(Session session, Packet incomingPacket) {
        new Thread(() -> {
            try {
                resourcePackFinished = false;
                UUID packId = null;
                for (Method m : incomingPacket.getClass().getMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType() == UUID.class) {
                        try {
                            packId = (UUID) m.invoke(incomingPacket);
                            if (packId != null) break;
                        } catch (Exception ignored) {}
                    }
                }
                Class<?> packetClass = findServerboundResourcePackClass();
                if (packetClass == null) return;
                Class<?> statusEnum = findStatusEnum(packetClass);
                if (statusEnum == null) return;
                Object accepted = null, downloaded = null, loaded = null;
                for (Object constant : statusEnum.getEnumConstants()) {
                    String name = constant.toString().toUpperCase();
                    if (name.contains("ACCEPTED")) accepted = constant;
                    if (name.contains("DOWNLOADED")) downloaded = constant;
                    if (name.contains("SUCCESSFULLY_LOADED") || name.equals("LOADED")) loaded = constant;
                }
                if (accepted != null) sendResourcePackResponse(session, packetClass, packId, accepted);
                Thread.sleep(ThreadLocalRandom.current().nextLong(2500, 4000)); // Udajemy, że gra myśli i przygotowuje pobieranie
                if (downloaded != null) sendResourcePackResponse(session, packetClass, packId, downloaded);
                Thread.sleep(ThreadLocalRandom.current().nextLong(3500, 6000)); // Udajemy realny czas pobierania pliku z serwera
                if (loaded != null) sendResourcePackResponse(session, packetClass, packId, loaded);
                Thread.sleep(800);
                resourcePackFinished = true;
            } catch (Exception e) {
                System.err.println("[BOT] Błąd paczki zasobów: " + e.getMessage());
            }
        }).start();
    }

    private static Class<?> findServerboundResourcePackClass() {
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
                                if (className.contains("Serverbound") && className.contains("ResourcePack")) {
                                    try {
                                        Class<?> c = Class.forName(className);
                                        if (Packet.class.isAssignableFrom(c)) return c;
                                    } catch (Throwable ignored) {}
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Class<?> findStatusEnum(Class<?> packetClass) {
        for (Constructor<?> cons : packetClass.getConstructors()) {
            for (Class<?> param : cons.getParameterTypes()) {
                if (param.isEnum()) return param;
            }
        }
        for (Class<?> inner : packetClass.getDeclaredClasses()) {
            if (inner.isEnum()) return inner;
        }
        return null;
    }

    private static void sendResourcePackResponse(Session session, Class<?> packetClass, UUID packId, Object status) {
        for (Constructor<?> cons : packetClass.getConstructors()) {
            try {
                Class<?>[] types = cons.getParameterTypes();
                if (types.length == 2) {
                    if (types[0] == UUID.class && types[1].isAssignableFrom(status.getClass())) {
                        session.send((Packet) cons.newInstance(packId, status));
                        return;
                    } else if (types[1] == UUID.class && types[0].isAssignableFrom(status.getClass())) {
                        session.send((Packet) cons.newInstance(status, packId));
                        return;
                    }
                } else if (types.length == 1 && types[0].isAssignableFrom(status.getClass())) {
                    session.send((Packet) cons.newInstance(status));
                    return;
                }
            } catch (Exception ignored) {}
        }
    }

    private static void clickSlot(Session session, int containerId, int slotIndex, Object clickedItem) {
        try {
            Class<?> actionTypeClass = null;
            try {
                actionTypeClass = Class.forName("org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType");
            } catch (ClassNotFoundException e) {
                try {
                    actionTypeClass = Class.forName("org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickType");
                } catch (ClassNotFoundException ignored) {}
            }
            Object actionType = null;
            if (actionTypeClass != null) {
                Object[] actionConstants = actionTypeClass.getEnumConstants();
                if (actionConstants != null) {
                    for (Object c : actionConstants) {
                        if (c.toString().toUpperCase().contains("CLICK") || c.toString().toUpperCase().contains("PICKUP")) {
                            actionType = c;
                            break;
                        }
                    }
                    if (actionType == null && actionConstants.length > 0) actionType = actionConstants[0];
                }
            }
            Object containerAction = null;
            String[] actionClassNames = new String[] {
                "org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerAction$ClickItemAction",
                "org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction",
                "org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerAction"
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
                        if (containerAction == null) containerAction = constants[0];
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
                    }
                }
            } catch (ClassNotFoundException ignored) {}

            Class<?> clickPacketClass = null;
            String[] possiblePacketNames = new String[]{
                "org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket",
                "org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundClickContainerPacket",
                "org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundContainerClickPacket"
            };
            for (String pName : possiblePacketNames) {
                try {
                    clickPacketClass = Class.forName(pName);
                    break;
                } catch (ClassNotFoundException ignored) {}
            }
            if (clickPacketClass == null) {
                System.err.println("[BOT] Błąd: nie odnaleziono klasy pakietu kliknięcia kontenera.");
                return;
            }

            Object packet = null;
            for (Constructor<?> cons : clickPacketClass.getConstructors()) {
                Class<?>[] pTypes = cons.getParameterTypes();
                Object[] args = new Object[pTypes.length];
                boolean valid = true;
                for (int i = 0; i < pTypes.length; i++) {
                    Class<?> p = pTypes[i];
                    if (p == int.class || p == Integer.class) {
                        if (i == 0) args[i] = containerId;
                        else if (i == 2) args[i] = slotIndex;
                        else args[i] = sequenceCounter.incrementAndGet();
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
            }
        } catch (Exception e) {
            System.err.println("[BOT] Błąd kliknięcia slotu: " + e.getMessage());
        }
    }

    private static Session createSession(String host, int port, MinecraftProtocol protocol) throws Exception {
        List<Class<?>> classes = scanMcProtocolLibClasses();
        classes.sort((c1, c2) -> {
            String n1 = c1.getSimpleName();
            String n2 = c2.getSimpleName();
            if (n1.contains("ClientNetworkSession")) return -1;
            if (n2.contains("ClientNetworkSession")) return 1;
            return n1.compareTo(n2);
        });
        for (Class<?> clazz : classes) {
            if (!clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers())) {
                Session session = tryInstantiateSessionClass(clazz, host, port, protocol);
                if (session != null) {
                    injectExecutors(session);
                    return session;
                }
            }
        }
        throw new IllegalStateException("Nie odnaleziono klasy sesji.");
    }

    private static Session tryInstantiateSessionClass(Class<?> clazz, String host, int port, MinecraftProtocol protocol) {
        for (Constructor<?> cons : clazz.getConstructors()) {
            Class<?>[] types = cons.getParameterTypes();
            Object[] args = new Object[types.length];
            boolean hostAssigned = false, portAssigned = false;
            for (int i = 0; i < types.length; i++) {
                Class<?> t = types[i];
                if (Executor.class.isAssignableFrom(t) || t.getName().contains("Executor")) {
                    args[i] = Executors.newSingleThreadExecutor();
                } else if (Proxy.class.isAssignableFrom(t)) {
                    args[i] = Proxy.NO_PROXY;
                } else if (t == String.class) {
                    if (!hostAssigned) { args[i] = host; hostAssigned = true; }
                    else args[i] = null;
                } else if (t == int.class || t == Integer.class) {
                    if (!portAssigned) { args[i] = port; portAssigned = true; }
                    else args[i] = 0;
                } else if (t.isAssignableFrom(protocol.getClass()) || t.getName().contains("Protocol")) {
                    args[i] = protocol;
                } else if (SocketAddress.class.isAssignableFrom(t) || InetSocketAddress.class.isAssignableFrom(t)) {
                    if (!hostAssigned) { args[i] = new InetSocketAddress(host, port); hostAssigned = true; portAssigned = true; }
                    else args[i] = null;
                } else if (t == boolean.class || t == Boolean.class) {
                    args[i] = false;
                } else if (t.isPrimitive()) {
                    args[i] = 0;
                } else {
                    args[i] = null;
                }
            }
            try {
                cons.setAccessible(true);
                Object obj = cons.newInstance(args);
                if (obj instanceof Session) return (Session) obj;
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
                        if (f.get(obj) == null) f.set(obj, Executors.newSingleThreadExecutor());
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
        } catch (Throwable ignored) {}
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
                    if (targetHost.endsWith(".")) targetHost = targetHost.substring(0, targetHost.length() - 1);
                    return new String[]{targetHost, srvData[2]};
                }
            }
        } catch (Exception ignored) {}
        return new String[]{host, "25565"};
    }
}

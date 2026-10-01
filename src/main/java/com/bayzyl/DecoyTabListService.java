package com.bayzyl;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

public final class DecoyTabListService {
    private final JavaPlugin plugin;
    private final DecoyPlayerCountService decoyPlayerCountService;
    private volatile List<DecoyPlayerCountService.DecoyProfile> activeDecoys = List.of();
    private volatile boolean unavailableLogged;

    public DecoyTabListService(JavaPlugin plugin, DecoyPlayerCountService decoyPlayerCountService) {
        this.plugin = plugin;
        this.decoyPlayerCountService = decoyPlayerCountService;
    }

    public void refreshAll() {
        runSafely(() -> {
            List<DecoyPlayerCountService.DecoyProfile> currentDecoys = decoyPlayerCountService.decoys();
            removeDecoys(Bukkit.getOnlinePlayers(), activeDecoys);
            addDecoys(Bukkit.getOnlinePlayers(), currentDecoys);
            activeDecoys = currentDecoys;
        });
    }

    public void refreshViewer(Player player) {
        runSafely(() -> {
            List<DecoyPlayerCountService.DecoyProfile> currentDecoys = decoyPlayerCountService.decoys();
            removeDecoys(List.of(player), activeDecoys);
            addDecoys(List.of(player), currentDecoys);
        });
    }

    public void clearAll() {
        runSafely(() -> {
            removeDecoys(Bukkit.getOnlinePlayers(), activeDecoys);
            activeDecoys = List.of();
        });
    }

    private void addDecoys(Iterable<? extends Player> viewers, List<DecoyPlayerCountService.DecoyProfile> decoys) throws Exception {
        if (decoys.isEmpty()) {
            return;
        }

        ReflectionBindings bindings = ReflectionBindings.create();
        List<Object> entries = new ArrayList<>(decoys.size());
        for (DecoyPlayerCountService.DecoyProfile decoy : decoys) {
            Object gameProfile = bindings.gameProfileConstructor.newInstance(decoy.id(), decoy.name());
            Object displayName = bindings.componentLiteral.invoke(null, decoy.name());
            Object entry = bindings.newEntry(decoy.id(), gameProfile, displayName);
            entries.add(entry);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        EnumSet<?> actions = EnumSet.of(
                (Enum) bindings.addPlayerAction,
                (Enum) bindings.updateListedAction,
                (Enum) bindings.updateLatencyAction,
                (Enum) bindings.updateGameModeAction,
                (Enum) bindings.updateDisplayNameAction
        );
        Object packet = bindings.infoUpdatePacketConstructor.newInstance(actions, entries);
        for (Player viewer : viewers) {
            sendPacket(viewer, packet, bindings);
        }
    }

    private void removeDecoys(Iterable<? extends Player> viewers, List<DecoyPlayerCountService.DecoyProfile> decoys) throws Exception {
        if (decoys.isEmpty()) {
            return;
        }

        ReflectionBindings bindings = ReflectionBindings.create();
        List<UUID> ids = decoys.stream().map(DecoyPlayerCountService.DecoyProfile::id).toList();
        Object packet = bindings.infoRemovePacketConstructor.newInstance(ids);
        for (Player viewer : viewers) {
            sendPacket(viewer, packet, bindings);
        }
    }

    private void sendPacket(Player viewer, Object packet, ReflectionBindings bindings) throws Exception {
        Object handle = bindings.getHandle.invoke(viewer);
        Object connection = bindings.connectionField.get(handle);
        bindings.sendPacket.invoke(connection, packet);
    }

    private void runSafely(ThrowingRunnable action) {
        try {
            action.run();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (!unavailableLogged) {
                unavailableLogged = true;
                plugin.getLogger().log(Level.WARNING, "Decoy tab-list entries are unavailable on this server runtime.", ex);
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Unexpected error while refreshing decoy tab-list entries.", ex);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class ReflectionBindings {
        private static volatile ReflectionBindings instance;

        private final Method getHandle;
        private final Field connectionField;
        private final Method sendPacket;
        private final Constructor<?> gameProfileConstructor;
        private final Method componentLiteral;
        private final Object survivalGameType;
        private final Object addPlayerAction;
        private final Object updateListedAction;
        private final Object updateLatencyAction;
        private final Object updateGameModeAction;
        private final Object updateDisplayNameAction;
        private final Constructor<?> entryConstructor;
        private final Constructor<?> infoUpdatePacketConstructor;
        private final Constructor<?> infoRemovePacketConstructor;
        private final int entryArity;

        private ReflectionBindings(Method getHandle,
                                   Field connectionField,
                                   Method sendPacket,
                                   Constructor<?> gameProfileConstructor,
                                   Method componentLiteral,
                                   Object survivalGameType,
                                   Object addPlayerAction,
                                   Object updateListedAction,
                                   Object updateLatencyAction,
                                   Object updateGameModeAction,
                                   Object updateDisplayNameAction,
                                   Constructor<?> entryConstructor,
                                   Constructor<?> infoUpdatePacketConstructor,
                                   Constructor<?> infoRemovePacketConstructor,
                                   int entryArity) {
            this.getHandle = getHandle;
            this.connectionField = connectionField;
            this.sendPacket = sendPacket;
            this.gameProfileConstructor = gameProfileConstructor;
            this.componentLiteral = componentLiteral;
            this.survivalGameType = survivalGameType;
            this.addPlayerAction = addPlayerAction;
            this.updateListedAction = updateListedAction;
            this.updateLatencyAction = updateLatencyAction;
            this.updateGameModeAction = updateGameModeAction;
            this.updateDisplayNameAction = updateDisplayNameAction;
            this.entryConstructor = entryConstructor;
            this.infoUpdatePacketConstructor = infoUpdatePacketConstructor;
            this.infoRemovePacketConstructor = infoRemovePacketConstructor;
            this.entryArity = entryArity;
        }

        private static ReflectionBindings create() throws Exception {
            ReflectionBindings cached = instance;
            if (cached != null) {
                return cached;
            }

            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            Method getHandle = craftPlayerClass.getMethod("getHandle");

            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            Field connectionField = serverPlayerClass.getField("connection");

            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
            Class<?> connectionClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
            Method sendPacket = connectionClass.getMethod("send", packetClass);

            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Constructor<?> gameProfileConstructor = accessibleConstructor(gameProfileClass, UUID.class, String.class);

            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Method componentLiteral = componentClass.getMethod("literal", String.class);

            Class<?> gameTypeClass = Class.forName("net.minecraft.world.level.GameType");
            Object survivalGameType = Objects.requireNonNull(gameTypeClass.getField("SURVIVAL").get(null));

            Class<?> actionClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
            Object addPlayerAction = Enum.valueOf((Class<Enum>) actionClass.asSubclass(Enum.class), "ADD_PLAYER");
            Object updateListedAction = Enum.valueOf((Class<Enum>) actionClass.asSubclass(Enum.class), "UPDATE_LISTED");
            Object updateLatencyAction = Enum.valueOf((Class<Enum>) actionClass.asSubclass(Enum.class), "UPDATE_LATENCY");
            Object updateGameModeAction = Enum.valueOf((Class<Enum>) actionClass.asSubclass(Enum.class), "UPDATE_GAME_MODE");
            Object updateDisplayNameAction = Enum.valueOf((Class<Enum>) actionClass.asSubclass(Enum.class), "UPDATE_DISPLAY_NAME");

            Class<?> chatSessionDataClass = Class.forName("net.minecraft.network.chat.RemoteChatSession$Data");
            Class<?> entryClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
            Constructor<?> entryConstructor = matchingConstructor(entryClass, parameterTypes ->
                    matchesEntryConstructor(parameterTypes, gameProfileClass, gameTypeClass, componentClass, chatSessionDataClass)
            );

            Class<?> infoUpdatePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            Constructor<?> infoUpdatePacketConstructor = matchingConstructor(infoUpdatePacketClass, parameterTypes ->
                    parameterTypes.length == 2
                            && parameterTypes[0].isAssignableFrom(EnumSet.class)
                            && parameterTypes[1].isAssignableFrom(List.class)
            );

            Class<?> infoRemovePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
            Constructor<?> infoRemovePacketConstructor = matchingConstructor(infoRemovePacketClass, parameterTypes ->
                    parameterTypes.length == 1
                            && parameterTypes[0].isAssignableFrom(List.class)
            );

            ReflectionBindings resolved = new ReflectionBindings(
                    getHandle,
                    connectionField,
                    sendPacket,
                    gameProfileConstructor,
                    componentLiteral,
                    survivalGameType,
                    addPlayerAction,
                    updateListedAction,
                    updateLatencyAction,
                    updateGameModeAction,
                    updateDisplayNameAction,
                    entryConstructor,
                    infoUpdatePacketConstructor,
                    infoRemovePacketConstructor,
                    entryConstructor.getParameterCount()
            );
            instance = resolved;
            return resolved;
        }

        private Object newEntry(UUID id, Object gameProfile, Object displayName) throws Exception {
            if (entryArity == 7) {
                return entryConstructor.newInstance(
                        id,
                        gameProfile,
                        true,
                        0,
                        survivalGameType,
                        displayName,
                        null
                );
            }
            if (entryArity == 9) {
                return entryConstructor.newInstance(
                        id,
                        gameProfile,
                        true,
                        0,
                        survivalGameType,
                        displayName,
                        true,
                        0,
                        null
                );
            }
            throw new NoSuchMethodException("Unsupported entry constructor arity: " + entryArity);
        }

        private static Constructor<?> accessibleConstructor(Class<?> type, Class<?>... parameterTypes) throws NoSuchMethodException {
            try {
                Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
                if (!Modifier.isPublic(constructor.getModifiers()) || !Modifier.isPublic(type.getModifiers())) {
                    constructor.setAccessible(true);
                }
                return constructor;
            } catch (NoSuchMethodException ignored) {
                Constructor<?> constructor = type.getConstructor(parameterTypes);
                if (!Modifier.isPublic(constructor.getModifiers()) || !Modifier.isPublic(type.getModifiers())) {
                    constructor.setAccessible(true);
                }
                return constructor;
            }
        }

        private static Constructor<?> matchingConstructor(Class<?> type, ConstructorMatcher matcher) throws NoSuchMethodException {
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (!matcher.matches(constructor.getParameterTypes())) {
                    continue;
                }
                if (!Modifier.isPublic(constructor.getModifiers()) || !Modifier.isPublic(type.getModifiers())) {
                    constructor.setAccessible(true);
                }
                return constructor;
            }
            throw new NoSuchMethodException("No matching constructor found for " + type.getName());
        }

        @FunctionalInterface
        private interface ConstructorMatcher {
            boolean matches(Class<?>[] parameterTypes);
        }

        private static boolean matchesEntryConstructor(Class<?>[] parameterTypes,
                                                      Class<?> gameProfileClass,
                                                      Class<?> gameTypeClass,
                                                      Class<?> componentClass,
                                                      Class<?> chatSessionDataClass) {
            if (parameterTypes.length == 7) {
                return parameterTypes[0] == UUID.class
                        && parameterTypes[1].isAssignableFrom(gameProfileClass)
                        && parameterTypes[2] == boolean.class
                        && parameterTypes[3] == int.class
                        && parameterTypes[4].isAssignableFrom(gameTypeClass)
                        && parameterTypes[5].isAssignableFrom(componentClass)
                        && (parameterTypes[6].isAssignableFrom(chatSessionDataClass) || !parameterTypes[6].isPrimitive());
            }
            if (parameterTypes.length == 9) {
                return parameterTypes[0] == UUID.class
                        && parameterTypes[1].isAssignableFrom(gameProfileClass)
                        && parameterTypes[2] == boolean.class
                        && parameterTypes[3] == int.class
                        && parameterTypes[4].isAssignableFrom(gameTypeClass)
                        && parameterTypes[5].isAssignableFrom(componentClass)
                        && parameterTypes[6] == boolean.class
                        && parameterTypes[7] == int.class
                        && (parameterTypes[8].isAssignableFrom(chatSessionDataClass) || !parameterTypes[8].isPrimitive());
            }
            return false;
        }
    }
}

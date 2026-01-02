package net.hosenka.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.hosenka.clan.ClanRank;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class ClansReforgedEvents {
    private ClansReforgedEvents() {}

    /* -------------------- PreCreateClanEvent (cancellable) -------------------- */
    @FunctionalInterface
    public interface PreCreateClanCallback {
        /**
         * Return FAIL to cancel.
         * Listener may message the player directly.
         */
        InteractionResult onPreCreate(ServerPlayer creator, String tagPreviewOrTag, String name);
    }

    public static final Event<PreCreateClanCallback> PRE_CREATE_CLAN =
            EventFactory.createArrayBacked(PreCreateClanCallback.class, callbacks -> (creator, tag, name) -> {
                for (var cb : callbacks) {
                    InteractionResult r = cb.onPreCreate(creator, tag, name);
                    if (r == InteractionResult.FAIL) return InteractionResult.FAIL;
                }
                return InteractionResult.PASS;
            });

    /* -------------------- CreateClanEvent (post) -------------------- */
    @FunctionalInterface
    public interface CreateClanCallback {
        void onCreate(ServerPlayer creator, UUID clanId);
    }

    public static final Event<CreateClanCallback> CREATE_CLAN =
            EventFactory.createArrayBacked(CreateClanCallback.class, callbacks -> (creator, clanId) -> {
                for (var cb : callbacks) cb.onCreate(creator, clanId);
            });

    /* -------------------- DisbandClanEvent (post) -------------------- */
    @FunctionalInterface
    public interface DisbandClanCallback {
        void onDisband(@Nullable ServerPlayer actor, UUID clanId);
    }

    public static final Event<DisbandClanCallback> DISBAND_CLAN =
            EventFactory.createArrayBacked(DisbandClanCallback.class, callbacks -> (actor, clanId) -> {
                for (var cb : callbacks) cb.onDisband(actor, clanId);
            });

    /* -------------------- PlayerJoinedClanEvent (post) -------------------- */
    @FunctionalInterface
    public interface PlayerJoinedClanCallback {
        void onJoin(ServerPlayer player, UUID clanId);
    }

    public static final Event<PlayerJoinedClanCallback> PLAYER_JOINED_CLAN =
            EventFactory.createArrayBacked(PlayerJoinedClanCallback.class, callbacks -> (player, clanId) -> {
                for (var cb : callbacks) cb.onJoin(player, clanId);
            });

    /* -------------------- PlayerKickedClanEvent (post) -------------------- */
    @FunctionalInterface
    public interface PlayerKickedClanCallback {
        void onKick(@Nullable ServerPlayer actor, UUID targetPlayerId, UUID clanId);
    }

    public static final Event<PlayerKickedClanCallback> PLAYER_KICKED_CLAN =
            EventFactory.createArrayBacked(PlayerKickedClanCallback.class, callbacks -> (actor, target, clanId) -> {
                for (var cb : callbacks) cb.onKick(actor, target, clanId);
            });

    /* -------------------- PlayerLeftClanEvent (post) -------------------- */
    @FunctionalInterface
    public interface PlayerLeftClanCallback {
        void onLeave(ServerPlayer player, UUID clanId);
    }

    public static final Event<PlayerLeftClanCallback> PLAYER_LEFT_CLAN =
            EventFactory.createArrayBacked(PlayerLeftClanCallback.class, callbacks -> (player, clanId) -> {
                for (var cb : callbacks) cb.onLeave(player, clanId);
            });

    /* -------------------- PlayerHomeSetEvent (cancellable) -------------------- */
    @FunctionalInterface
    public interface PlayerHomeSetCallback {
        /**
         * Return FAIL to cancel.
         * Listener may message the player directly.
         */
        InteractionResult onHomeSet(ServerPlayer player, UUID clanId, GlobalPos pos, float yaw, float pitch);
    }

    public static final Event<PlayerHomeSetCallback> PLAYER_HOME_SET =
            EventFactory.createArrayBacked(PlayerHomeSetCallback.class, callbacks -> (player, clanId, pos, yaw, pitch) -> {
                for (var cb : callbacks) {
                    InteractionResult r = cb.onHomeSet(player, clanId, pos, yaw, pitch);
                    if (r == InteractionResult.FAIL) return InteractionResult.FAIL;
                }
                return InteractionResult.PASS;
            });

    /* -------------------- PlayerRankUpdateEvent (post) -------------------- */
    @FunctionalInterface
    public interface PlayerRankUpdateCallback {
        void onRankUpdate(@Nullable ServerPlayer actor, UUID targetPlayerId, UUID clanId, ClanRank oldRank, ClanRank newRank);
    }

    public static final Event<PlayerRankUpdateCallback> PLAYER_RANK_UPDATE =
            EventFactory.createArrayBacked(PlayerRankUpdateCallback.class, callbacks -> (actor, target, clanId, oldR, newR) -> {
                for (var cb : callbacks) cb.onRankUpdate(actor, target, clanId, oldR, newR);
            });
}

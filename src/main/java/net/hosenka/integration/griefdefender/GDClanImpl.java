package net.hosenka.integration.griefdefender;

import com.griefdefender.api.Clan;
import com.griefdefender.api.ClanPlayer;
import com.griefdefender.api.clan.ClanHome;
import com.griefdefender.api.clan.Rank;
import com.griefdefender.lib.flowpowered.math.vector.Vector3i;
import com.griefdefender.lib.kyori.adventure.text.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import java.util.Locale;
import java.util.*;
import net.minecraft.core.BlockPos;




import net.hosenka.alliance.Alliance;
import net.hosenka.alliance.AllianceRegistry;
import net.hosenka.clan.ClanRegistry;


public class GDClanImpl implements Clan {

    private final net.hosenka.clan.Clan source;

    public GDClanImpl(net.hosenka.clan.Clan clan) {
        this.source = clan;
    }

    private ServerLevel resolveHomeLevel() {
        if (!source.hasHome() || ServerHolder.get() == null) return null;
        ResourceLocation rl = ResourceLocation.tryParse(source.getHomeDimension());
        if (rl == null) return null;
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, rl);
        return ServerHolder.get().getLevel(key);
    }

    private UUID resolveHomeWorldUniqueId() {
        ServerLevel level = resolveHomeLevel();
        if (level == null) return null;
        var core = com.griefdefender.api.GriefDefender.getCore();
        return core.getWorldUniqueId(level);
    }

    private ServerLevel resolveBaseLevel() {
        var server = ServerHolder.get();
        if (server == null) return null;

        // Prefer clan home dimension if valid/loaded
        if (source.hasHome()) {
            ResourceLocation rl = ResourceLocation.tryParse(source.getHomeDimension());
            if (rl != null) {
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, rl);
                ServerLevel homeLevel = server.getLevel(key);
                if (homeLevel != null) {
                    return homeLevel;
                }
            }
        }

        // Fallback: overworld (spawn)
        return server.getLevel(Level.OVERWORLD);
    }


    /* ---------------- CatalogType ---------------- */

    @Override
    public String getId() {
        return "clansreforged:" + source.getId();
    }


    @Override
    public String getName() {
        return source.getName();
    }

    /* ---------------- Clan ---------------- */

    @Override
    public Component getNameComponent() {
        return Component.text(source.getName());
    }

    @Override
    public Component getTagComponent() {
        return Component.text(source.getTag());
    }


    @Override
    public String getTag() {
        return source.getTag();
    }



    @Override
    public String getDescription() {
        return source.getDescription();
    }



    @Override
    public List<Clan> getAllies() {
        UUID allianceId = source.getAllianceId();
        net.hosenka.util.CRDebug.log("[GD] getAllies() called for clan=" + source.getTag()
                + " allianceId=" + allianceId);

        if (allianceId == null) {
            return Collections.emptyList();
        }

        Alliance alliance = AllianceRegistry.getAlliance(allianceId);
        if (alliance == null) {
            net.hosenka.util.CRDebug.log("[GD] allianceId=" + allianceId + " not found in AllianceRegistry");
            return Collections.emptyList();
        }

        net.hosenka.util.CRDebug.log("[GD] alliance=" + alliance.getName()
                + " clans=" + alliance.getClans());

        var provider = com.griefdefender.api.GriefDefender.getCore().getClanProvider();
        ClansReforgedClanProvider cr = (provider instanceof ClansReforgedClanProvider p) ? p : null;

        List<Clan> result = new ArrayList<>();
        for (UUID clanId : alliance.getClans()) {
            if (clanId.equals(source.getId())) continue;

            Clan wrapped;
            if (cr != null) {
                wrapped = cr.getClanById(clanId);
            } else {
                var allied = ClanRegistry.getClan(clanId);
                wrapped = (allied != null) ? new GDClanImpl(allied) : null;
            }

            if (wrapped != null) {
                result.add(wrapped);
            }
        }

        net.hosenka.util.CRDebug.log("[GD] getAllies() returning " + result.size() + " allies for clan=" + source.getTag());
        return result;
    }



    @Override
    public List<Clan> getRivals() {
        UUID myAllianceId = source.getAllianceId();
        if (myAllianceId == null) {
            return Collections.emptyList();
        }

        Alliance myAlliance = AllianceRegistry.getAlliance(myAllianceId);
        if (myAlliance == null) {
            return Collections.emptyList();
        }

        var provider = com.griefdefender.api.GriefDefender.getCore().getClanProvider();
        ClansReforgedClanProvider cr = (provider instanceof ClansReforgedClanProvider p) ? p : null;

        List<Clan> result = new ArrayList<>();

        // Every clan in every enemy alliance is a "rival clan"
        for (UUID enemyAllianceId : myAlliance.getEnemies()) {
            Alliance enemyAlliance = AllianceRegistry.getAlliance(enemyAllianceId);
            if (enemyAlliance == null) continue;

            for (UUID enemyClanId : enemyAlliance.getClans()) {
                Clan wrapped;
                if (cr != null) {
                    wrapped = cr.getClanById(enemyClanId);
                } else {
                    var enemy = ClanRegistry.getClan(enemyClanId);
                    wrapped = (enemy != null) ? new GDClanImpl(enemy) : null;
                }

                if (wrapped != null) {
                    result.add(wrapped);
                }
            }
        }

        return result;
    }


    @Override
    public List<ClanPlayer> getMembers(boolean onlineOnly) {
        List<ClanPlayer> members = new ArrayList<>();

        var provider = com.griefdefender.api.GriefDefender.getCore().getClanProvider();
        ClansReforgedClanProvider cr = (provider instanceof ClansReforgedClanProvider p) ? p : null;

        for (UUID memberId : source.getMembers()) {
            ClanPlayer cp = (cr != null) ? cr.getClanPlayer(memberId) : new GDClanPlayerImpl(memberId, source);
            if (cp == null) continue;

            if (!onlineOnly || cp.isOnline()) {
                members.add(cp);
            }
        }

        return members;
    }

    @Override
    public List<ClanPlayer> getLeaders(boolean onlineOnly) {
        UUID leaderId = source.getLeaderId();
        if (leaderId == null) return Collections.emptyList();

        var provider = com.griefdefender.api.GriefDefender.getCore().getClanProvider();
        ClansReforgedClanProvider cr = (provider instanceof ClansReforgedClanProvider p) ? p : null;

        ClanPlayer cp = (cr != null) ? cr.getClanPlayer(leaderId) : new GDClanPlayerImpl(leaderId, source);
        if (cp == null) return Collections.emptyList();

        if (onlineOnly && !cp.isOnline()) return Collections.emptyList();
        return List.of(cp);
    }


    @Override
    public List<Rank> getRanks() {
        return List.of(
                CRRank.MEMBER,
                CRRank.RIGHT_ARM,
                CRRank.LEADER
        );
    }



    @Override
    public Rank getRank(String rankName) {
        if (rankName == null) {
            return null;
        }
        for (Rank rank : getRanks()) {
            if (rank.getName().equalsIgnoreCase(rankName)) {
                return rank;
            }
        }
        return null;
    }

    @Override
    public boolean isAlly(String tag) {
        if (tag == null || tag.isBlank()) {
            return false;
        }

        UUID allianceId = source.getAllianceId();
        if (allianceId == null) {
            return false;
        }

        // Find the clan by tag and verify it shares the same alliance id
        net.hosenka.clan.Clan other = ClanRegistry.getByTag(tag);
        if (other == null) {
            return false;
        }

        // Not an ally with self
        if (other.getId().equals(source.getId())) {
            return false;
        }

        return allianceId.equals(other.getAllianceId());
    }

    @Override
    public boolean isRival(String tag) {
        if (tag == null || tag.isBlank()) return false;
        UUID myAllianceId = source.getAllianceId();
        if (myAllianceId == null) return false; // no alliance => neutral to everyone

        net.hosenka.clan.Clan other = ClanRegistry.getByTag(tag);
        if (other == null) other = ClanRegistry.getByName(tag);
        if (other == null) return false;
        UUID otherAllianceId = other.getAllianceId();
        if (otherAllianceId == null) return false; // no alliance => neutral
        if (myAllianceId.equals(otherAllianceId)) return false; // same alliance => ally

        return net.hosenka.alliance.AllianceRegistry.areAlliancesAtWar(myAllianceId, otherAllianceId);
    }

    @Override
    public UUID getBaseWorldUniqueId() {
        ServerLevel level = resolveBaseLevel();
        if (level == null) return null; // only possible very early during startup
        return com.griefdefender.api.GriefDefender.getCore().getWorldUniqueId(level);
    }

    @Override
    public Vector3i getBasePos() {
        ServerLevel level = resolveBaseLevel();
        if (level == null) return null; // only possible very early during startup

        // If home is set, base pos = home pos
        if (source.hasHome()) {
            return new Vector3i(
                    (int) Math.floor(source.getHomeX()),
                    (int) Math.floor(source.getHomeY()),
                    (int) Math.floor(source.getHomeZ())
            );
        }

        // Otherwise, base pos = world spawn pos
        BlockPos spawn = level.getSharedSpawnPos();
        return new Vector3i(spawn.getX(), spawn.getY(), spawn.getZ());
    }


    @Override
    public List<ClanHome> getHomes() {
        UUID wid = resolveHomeWorldUniqueId();
        if (wid == null) return Collections.emptyList();

        Vector3i pos = new Vector3i(
                (int) Math.floor(source.getHomeX()),
                (int) Math.floor(source.getHomeY()),
                (int) Math.floor(source.getHomeZ())
        );

        Object location = null;
        ServerLevel level = resolveHomeLevel();
        if (level != null) {
            location = net.minecraft.core.GlobalPos.of(
                    level.dimension(),
                    net.minecraft.core.BlockPos.containing(source.getHomeX(), source.getHomeY(), source.getHomeZ())
            );
        }

        return List.of(new CRGDClanHome("home", wid, pos, location));
    }




}
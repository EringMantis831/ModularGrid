package com.modulargrid.common.command;

import com.modulargrid.common.channel.Channel;
import com.modulargrid.common.channel.ChannelCleanup;
import com.modulargrid.common.channel.ChannelRegistry;
import com.modulargrid.common.channel.ChannelWorldData;
import com.modulargrid.common.channel.FirmChannelConfig;
import com.modulargrid.common.channel.FirmChannelDefinition;
import com.mojang.authlib.GameProfile;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /modulargrid} - channel management.
 *
 * <pre>
 * /modulargrid reg &lt;name&gt; [-p] [-e]   register a channel, -p public, -e energy (one per player)
 * /modulargrid del &lt;name&gt;             delete one of your own channels
 * /modulargrid list                    list your own channels
 * /modulargrid set &lt;name&gt; &lt;value&gt;     set the value, player/name form allowed (permission level 2)
 * </pre>
 */
public class CommandModularGrid extends CommandBase {

    private static final String PERMISSION_TARGET = "modulargrid";

    private static final String SUB_REG = "reg";
    private static final String SUB_DEL = "del";
    private static final String SUB_LIST = "list";
    private static final String SUB_SET = "set";

    private static final String FLAG_PUBLIC = "-p";
    private static final String FLAG_ENERGY = "-e";

    private static final List<String> SUB_COMMANDS = Collections.unmodifiableList(
            Arrays.asList(SUB_REG, SUB_DEL, SUB_LIST, SUB_SET));

    @Override
    public String getName() {
        return PERMISSION_TARGET;
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("mgrid");
    }

    /** Every player may use {@code reg}/{@code del}/{@code list}, {@code set} checks its own level. */
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/modulargrid reg <name> [-p] [-e] | del <name> | list | set <name> <value>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new CommandException("command.modulargrid.usage");
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case SUB_REG:
                executeRegister(sender, args);
                break;
            case SUB_DEL:
                executeDelete(sender, args);
                break;
            case SUB_LIST:
                executeList(sender, args);
                break;
            case SUB_SET:
                executeSet(server, sender, args);
                break;
            default:
                throw new CommandException("command.modulargrid.unknownSub", sub);
        }
    }

    // ---------------------------------------------------------------- sub commands

    private void executeRegister(ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = requirePlayer(sender);
        String name = null;
        boolean publicChannel = false;
        boolean energy = false;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (FLAG_PUBLIC.equals(arg)) {
                publicChannel = true;
            } else if (FLAG_ENERGY.equals(arg)) {
                energy = true;
            } else if (arg.startsWith("-")) {
                throw new CommandException("command.modulargrid.reg.unknownFlag", arg);
            } else if (name == null) {
                name = arg;
            } else {
                throw new CommandException("command.modulargrid.usage.reg");
            }
        }
        if (name == null) {
            throw new CommandException("command.modulargrid.usage.reg");
        }

        ChannelRegistry.RegisterOutcome outcome = ChannelRegistry.register(
                sender.getEntityWorld(), player.getUniqueID(), player.getName(), name, publicChannel, energy);
        if (!outcome.isSuccess()) {
            // name first, then the channel limit: messages use only the arguments they need
            throw new CommandException(registerMessageKey(outcome.getResult()), name, Channel.MAX_CHANNELS_PER_OWNER);
        }
        Channel channel = outcome.getChannel();
        sender.sendMessage(new TextComponentTranslation("command.modulargrid.reg.ok", channel.getName(),
                new TextComponentTranslation(channel.isEnergy()
                        ? "command.modulargrid.type.energy" : "command.modulargrid.type.normal"),
                new TextComponentTranslation(channel.isPublic()
                        ? "command.modulargrid.visibility.public" : "command.modulargrid.visibility.private")));
    }

    /**
     * @return the message key of a refused registration. The two reasons a pack can have for energy channels - it
     *         declares its own, or it turns player ones off - are one answer to the player: they may not register one.
     */
    private static String registerMessageKey(ChannelRegistry.RegisterResult result) {
        return switch (result) {
            case ENERGY_PROVIDED_BY_FIRM, PLAYER_ENERGY_DISABLED -> "command.modulargrid.reg.energy_not_allowed";
            default -> "command.modulargrid.reg." + result.name().toLowerCase(Locale.ROOT);
        };
    }

    private void executeDelete(ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = requirePlayer(sender);
        if (args.length != 2) {
            throw new CommandException("command.modulargrid.usage.del");
        }
        String name = args[1];
        World world = sender.getEntityWorld();
        if (FirmChannelConfig.isFirm(name)) {
            // a firm channel is part of the pack, only its definition can remove it
            throw new CommandException("command.modulargrid.del.firm", name);
        }
        Channel owned = ChannelRegistry.get(world, player.getUniqueID(), name);
        if (owned == null) {
            Channel foreign = ChannelRegistry.findAny(world, name);
            if (foreign != null) {
                throw new CommandException("command.modulargrid.del.notOwner", name, foreign.getOwnerName());
            }
            throw new CommandException("command.modulargrid.del.unknown", name);
        }
        Channel removed = ChannelRegistry.delete(world, player.getUniqueID(), name);
        if (removed == null) {
            throw new CommandException("command.modulargrid.del.unknown", name);
        }
        sender.sendMessage(new TextComponentTranslation("command.modulargrid.del.ok", removed.getName()));
        // access plugs remember the channels they switched off, so they have to forget this one
        int purged = ChannelCleanup.purgeFromAccessPlugs(removed.getAddress());
        if (purged > 0) {
            sender.sendMessage(new TextComponentTranslation("command.modulargrid.del.purged", purged));
        }
    }

    private void executeList(ICommandSender sender, String[] args) throws CommandException {
        EntityPlayerMP player = requirePlayer(sender);
        if (args.length != 1) {
            throw new CommandException("command.modulargrid.usage.list");
        }
        List<Channel> channels = ChannelRegistry.listOwn(sender.getEntityWorld(), player.getUniqueID());
        List<Channel> firm = ChannelRegistry.listFirmFor(sender.getEntityWorld(), player.getUniqueID());
        if (channels.isEmpty() && firm.isEmpty()) {
            sender.sendMessage(new TextComponentTranslation("command.modulargrid.list.empty"));
            return;
        }
        if (!channels.isEmpty()) {
            sender.sendMessage(new TextComponentTranslation("command.modulargrid.list.header", channels.size()));
            for (Channel channel : channels) {
                sender.sendMessage(listEntry(channel));
            }
        }
        for (Channel channel : firm) {
            sender.sendMessage(listEntry(channel));
        }
    }

    /**
     * @return one line of {@code /modulargrid list} for a channel. A firm channel is marked and named the way the pack
     *         declared it: its configured text as a translation component (so a language key declared by the pack is
     *         translated on the client, while plain text stays plain), falling back to the definition id.
     */
    private static ITextComponent listEntry(Channel channel) {
        FirmChannelDefinition definition = channel.isFirm()
                ? FirmChannelConfig.find(channel.getFirmChannelId()) : null;
        ITextComponent name = definition == null
                ? new TextComponentString(channel.getName())
                : definition.getChannelLocalization().isEmpty()
                        ? new TextComponentString(definition.getChannelId())
                        : new TextComponentTranslation(definition.getChannelLocalization());
        return new TextComponentTranslation(channel.isEnergy()
                ? "command.modulargrid.list.entry.energy" : "command.modulargrid.list.entry",
                name,
                new TextComponentTranslation(channel.isEnergy()
                        ? "command.modulargrid.type.energy" : "command.modulargrid.type.normal"),
                new TextComponentTranslation(definition != null
                        ? "command.modulargrid.visibility.firm" : channel.isPublic()
                                ? "command.modulargrid.visibility.public"
                                : "command.modulargrid.visibility.private"),
                channel.getValueDisplay());
    }

    private void executeSet(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 3) {
            throw new CommandException("command.modulargrid.usage.set");
        }
        if (!sender.canUseCommand(2, getName())) {
            throw new CommandException("command.modulargrid.set.noPermission");
        }
        String target = args[1];
        String rawValue = args[2];

        UUID owner;
        String name;
        int separator = target.indexOf('/');
        if (separator < 0) {
            owner = requirePlayer(sender).getUniqueID();
            name = target;
        } else {
            String ownerName = target.substring(0, separator);
            name = target.substring(separator + 1);
            owner = resolveOwner(sender.getEntityWorld(), server, ownerName);
            if (owner == null) {
                throw new CommandException("command.modulargrid.set.unknownOwner", ownerName);
            }
        }

        World world = sender.getEntityWorld();
        Channel channel = ChannelRegistry.get(world, owner, name);
        if (channel == null) {
            throw new CommandException("command.modulargrid.set.unknown", target);
        }
        // The channel type decides the value type; the value never passes through a double
        if (channel.isEnergy()) {
            ChannelRegistry.setEnergyValue(world, owner, name, parseEnergyValue(rawValue));
        } else {
            ChannelRegistry.setFloatValue(world, owner, name, parseFloatValue(rawValue));
        }
        sender.sendMessage(new TextComponentTranslation(channel.isEnergy()
                ? "command.modulargrid.set.ok.energy" : "command.modulargrid.set.ok",
                target, channel.getValueDisplay()));
    }

    // -------------------------------------------------------------------- helpers

    private static EntityPlayerMP requirePlayer(ICommandSender sender) throws CommandException {
        if (!(sender instanceof EntityPlayerMP)) {
            throw new CommandException("command.modulargrid.playerOnly");
        }
        return (EntityPlayerMP) sender;
    }

    /**
     * Resolves the UUID of a channel owner by name: online players first, then operators,
     * then whoever registered a channel under that name.
     */
    private static UUID resolveOwner(World world, MinecraftServer server, String ownerName) {
        EntityPlayerMP online = server.getPlayerList().getPlayerByUsername(ownerName);
        if (online != null) {
            return online.getUniqueID();
        }
        GameProfile opProfile = server.getPlayerList().getOppedPlayers().getGameProfileFromName(ownerName);
        if (opProfile != null) {
            return opProfile.getId();
        }
        ChannelWorldData data = ChannelRegistry.getData(world);
        if (data != null) {
            for (Map.Entry<UUID, Map<String, Channel>> entry : data.getAllChannels().entrySet()) {
                for (Channel channel : entry.getValue().values()) {
                    if (ownerName.equalsIgnoreCase(channel.getOwnerName())) {
                        return entry.getKey();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Parses the value of an energy channel. Energy is stored as a {@code long}, so the value is
     * parsed straight into one - routing it through a {@code double} would lose precision above 2^53.
     */
    private static long parseEnergyValue(String rawValue) throws CommandException {
        try {
            return Long.parseLong(rawValue.trim());
        } catch (NumberFormatException ex) {
            throw new CommandException("command.modulargrid.set.invalidEnergy", rawValue);
        }
    }

    /**
     * Parses the value of a normal channel, which is stored as a {@code float}. Non finite input
     * such as {@code NaN} is rejected because it would poison every later calculation.
     */
    private static float parseFloatValue(String rawValue) throws CommandException {
        try {
            float value = Float.parseFloat(rawValue.trim());
            if (Float.isFinite(value)) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // reported below
        }
        throw new CommandException("command.modulargrid.set.invalidFloat", rawValue);
    }
    // ------------------------------------------------------------ tab completion

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUB_COMMANDS);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (SUB_DEL.equals(sub) || SUB_SET.equals(sub)) {
                return getListOfStringsMatchingLastWord(args, ownChannelNames(sender));
            }
            return Collections.emptyList();
        }
        if (args.length == 3 && SUB_REG.equals(args[0].toLowerCase(Locale.ROOT))) {
            List<String> flags = new ArrayList<>();
            Collection<String> used = Arrays.asList(args);
            if (!used.contains(FLAG_PUBLIC)) {
                flags.add(FLAG_PUBLIC);
            }
            if (!used.contains(FLAG_ENERGY)) {
                flags.add(FLAG_ENERGY);
            }
            return getListOfStringsMatchingLastWord(args, flags);
        }
        return Collections.emptyList();
    }

    private static List<String> ownChannelNames(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (Channel channel : ChannelRegistry.listOwn(sender.getEntityWorld(), ((EntityPlayerMP) sender).getUniqueID())) {
            names.add(channel.getName());
        }
        return names;
    }

}

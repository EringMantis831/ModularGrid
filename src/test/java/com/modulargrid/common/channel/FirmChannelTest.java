package com.modulargrid.common.channel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Addressing of {@link FirmChannelDefinition firm channels} and the decision which stored firm value may stay.
 */
class FirmChannelTest {

    private static final UUID PLAYER = UUID.fromString("a01a21f5-c117-3c1f-b5d0-e055b927f733");
    private static final UUID OTHER_PLAYER = UUID.fromString("b2c5a1de-7f1e-4d0b-9a3c-8e6f2c4d5a10");

    private static final String CONFIGURATION = """
            {
              "allowPlayerEnergyChannels": false,
              "channels": [
                {
                  "channelId": "temp",
                  "channelLocalization": "温度",
                  "isPublic": true,
                  "isEnergy": false,
                  "masterTo": ["alloy_furnace"]
                }
              ]
            }
            """;

    @Test
    void sharedChannelIsStoredUnderItsIdForEveryone() {
        FirmChannelDefinition shared = definition(true, false);
        assertEquals("temp", ChannelRegistry.firmValueName(shared, null));
        assertEquals("temp", ChannelRegistry.firmValueName(shared, PLAYER));
        assertEquals("temp", ChannelRegistry.firmValueName(shared, OTHER_PLAYER));
    }

    @Test
    void perPlayerChannelIsStoredPerPlayer() {
        FirmChannelDefinition perPlayer = definition(false, false);
        assertEquals("temp@" + PLAYER, ChannelRegistry.firmValueName(perPlayer, PLAYER));
        assertEquals("temp@" + OTHER_PLAYER, ChannelRegistry.firmValueName(perPlayer, OTHER_PLAYER));
        // without a player there is no per player value to read, which is what a script outside a machine sees
        assertNull(ChannelRegistry.firmValueName(perPlayer, null));
    }

    @Test
    void firmChannelKnowsItsDefinitionAndItsPlayer() {
        Channel shared = firmChannel("temp", false);
        assertTrue(shared.isFirm());
        assertEquals("temp", shared.getFirmChannelId());
        assertNull(shared.getFirmPlayer());

        Channel perPlayer = firmChannel("temp@" + PLAYER, false);
        assertTrue(perPlayer.isFirm());
        assertEquals("temp", perPlayer.getFirmChannelId());
        assertEquals(PLAYER, perPlayer.getFirmPlayer());

        Channel playerOwned = new Channel("mine", PLAYER, "Developer", false, false);
        assertFalse(playerOwned.isFirm());
        assertNull(playerOwned.getFirmChannelId());
        assertNull(playerOwned.getFirmPlayer());
    }

    @Test
    void declaredValuesAreKeptAndOthersAreDropped(@TempDir Path directory) throws IOException {
        load(directory, CONFIGURATION);
        assertTrue(FirmChannelConfig.isLoaded());
        assertTrue(FirmChannelConfig.isFirm("temp"));

        // still declared, same sharing mode and same type
        assertNull(ChannelRegistry.firmPruneReason("temp", firmChannel("temp", false)));
        // no longer declared
        assertNotNull(ChannelRegistry.firmPruneReason("gone", firmChannel("gone", false)));
        // declared as shared, so a leftover per player entry is stale
        assertNotNull(ChannelRegistry.firmPruneReason("temp@" + PLAYER, firmChannel("temp@" + PLAYER, false)));
        // declared as a data channel, stored as an energy one
        assertNotNull(ChannelRegistry.firmPruneReason("temp", firmChannel("temp", true)));
    }

    @Test
    void aPerPlayerEnergyChannelOccupiesThePlayersOnlyEnergySlot(@TempDir Path directory) throws IOException {
        load(directory, """
                {
                  "allowPlayerEnergyChannels": true,
                  "channels": [
                    { "channelId": "shared_power", "isPublic": true, "isEnergy": true },
                    { "channelId": "grid_power", "isPublic": false, "isEnergy": true }
                  ]
                }
                """);
        assertTrue(ChannelRegistry.hasFirmEnergyChannel());
        FirmChannelDefinition energy = ChannelRegistry.getPrivateFirmEnergyChannel();
        assertNotNull(energy);
        assertEquals("grid_power", energy.getChannelId());
        // a shared energy channel is a pool everyone may draw from, it takes nobody's slot
        assertTrue(FirmChannelConfig.isFirm("shared_power"));
        assertEquals("grid_power" + ChannelRegistry.FIRM_PLAYER_SEPARATOR + PLAYER,
                ChannelRegistry.firmValueName(energy, PLAYER));
    }

    @Test
    void withoutAPerPlayerEnergyChannelTheSlotStaysFree(@TempDir Path directory) throws IOException {
        load(directory, CONFIGURATION); // declares the public data channel 'temp' only
        assertFalse(ChannelRegistry.hasFirmEnergyChannel());
        assertNull(ChannelRegistry.getPrivateFirmEnergyChannel());
    }

    @Test
    void aSharedEnergyChannelAlsoTakesOverPlayerEnergyChannels(@TempDir Path directory) throws IOException {
        load(directory, """
                {
                  "channels": [
                    { "channelId": "temp", "isPublic": true, "isEnergy": false },
                    { "channelId": "pack_power", "isPublic": true, "isEnergy": true }
                  ]
                }
                """);
        // any declared energy channel replaces the energy channels players registered themselves, a shared one included
        assertTrue(ChannelRegistry.hasFirmEnergyChannel());
        // but only a per player declaration is the energy channel of each player
        assertNull(ChannelRegistry.getPrivateFirmEnergyChannel());
    }

    @Test
    void withoutAFirmEnergyChannelPlayerChannelsStayInUse(@TempDir Path directory) throws IOException {
        load(directory, CONFIGURATION); // the public data channel 'temp' only
        assertFalse(ChannelRegistry.hasFirmEnergyChannel());
    }

    @Test
    void theFirstPerPlayerEnergyChannelIsTheOneThatTakesTheSlot(@TempDir Path directory) throws IOException {
        load(directory, """
                {
                  "channels": [
                    { "channelId": "first", "isPublic": false, "isEnergy": true },
                    { "channelId": "second", "isPublic": false, "isEnergy": true }
                  ]
                }
                """);
        assertTrue(ChannelRegistry.hasFirmEnergyChannel());
        FirmChannelDefinition used = ChannelRegistry.getPrivateFirmEnergyChannel();
        assertNotNull(used);
        assertEquals("first", used.getChannelId());
    }

    @Test
    void machineRestrictionAcceptsBareAndFullNamesAndNeverAppliesToEnergy() {
        FirmChannelDefinition restricted = FirmChannelDefinition.create("temp", "", true, false,
                List.of(FirmChannelDefinition.normalizeMachineName("alloy_furnace"),
                        FirmChannelDefinition.normalizeMachineName("modularmachinery:blast_furnace")),
                false);
        assertNotNull(restricted);
        // a bare name is completed with the namespace of the only mod that defines machines
        assertTrue(restricted.allowsMachine("modularmachinery:alloy_furnace"));
        assertTrue(restricted.allowsMachine("modularmachinery:blast_furnace"));
        assertFalse(restricted.allowsMachine("modularmachinery:centrifuge"));
        assertTrue(restricted.isMachineRestricted());

        // an empty entry means every machine, and an energy channel ignores the restriction altogether
        FirmChannelDefinition open = FirmChannelDefinition.create("open", "", true, false, List.of("alloy_furnace"),
                true);
        assertNotNull(open);
        assertFalse(open.isMachineRestricted());
        assertTrue(open.allowsMachine("modularmachinery:centrifuge"));

        FirmChannelDefinition energy = FirmChannelDefinition.create("grid", "", false, true,
                List.of(FirmChannelDefinition.normalizeMachineName("alloy_furnace")), false);
        assertNotNull(energy);
        assertTrue(energy.allowsMachine("modularmachinery:centrifuge"));
    }

    private static void load(Path directory, String json) throws IOException {
        Path config = directory.resolve(FirmChannelConfig.DIRECTORY_NAME);
        Files.createDirectories(config);
        Files.writeString(config.resolve(FirmChannelConfig.FILE_NAME), json, StandardCharsets.UTF_8);
        FirmChannelConfig.load(directory.toFile());
    }

    private static FirmChannelDefinition definition(boolean publicChannel, boolean energy) {
        FirmChannelDefinition definition = FirmChannelDefinition.create("temp", "温度", publicChannel, energy,
                List.of(), false);
        assertNotNull(definition);
        return definition;
    }

    private static Channel firmChannel(String key, boolean energy) {
        return new Channel(key, FirmChannelDefinition.FIRM_OWNER, "", !key.contains("@"), energy);
    }
}

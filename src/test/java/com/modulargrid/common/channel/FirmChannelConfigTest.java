package com.modulargrid.common.channel;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading {@code firm_channel.json}: the camelCase keys, the defaults, and what happens to entries a pack author got
 * wrong.
 *
 * <p>One faulty entry must never cost the other channels of the file, so every entry is read on its own and reported
 * while the file is read instead of at some later point.</p>
 */
class FirmChannelConfigTest {

    private static FirmChannelDefinition only(String json) {
        FirmChannelConfig config = FirmChannelConfig.parse(json);
        assertEquals(1, config.definitionsOf().size());
        return config.definitionsOf().get(0);
    }

    @Test
    void camelCaseKeysAreRead() {
        FirmChannelDefinition definition = only("""
                {
                  "allowPlayerEnergyChannels": false,
                  "channels": [
                    {
                      "channelId": "temp",
                      "channelLocalization": "温度",
                      "isPublic": true,
                      "isEnergy": false,
                      "masterTo": ["alloy_furnace", "modularmachinery:blast_furnace"]
                    }
                  ]
                }
                """);
        assertEquals("temp", definition.getChannelId());
        assertEquals("温度", definition.getChannelLocalization());
        assertEquals("modulargrid.channel.temp", definition.getConventionalLocalizationKey());
        assertTrue(definition.isPublic());
        assertFalse(definition.isEnergy());
        assertEquals(List.of("modularmachinery:alloy_furnace", "modularmachinery:blast_furnace"),
                definition.getMasterTo());
        assertTrue(definition.allowsMachine("modularmachinery:alloy_furnace"));
        assertFalse(definition.allowsMachine("modularmachinery:centrifuge"));
    }

    @Test
    void aMissingEntryTakesTheDocumentedDefaults() {
        FirmChannelDefinition definition = only("""
                { "channels": [ { "channelId": "temp" } ] }
                """);
        // shared and a data channel, open to every machine, no text of its own
        assertTrue(definition.isPublic());
        assertFalse(definition.isEnergy());
        assertEquals("", definition.getChannelLocalization());
        assertFalse(definition.isMachineRestricted());
        assertTrue(definition.allowsMachine("modularmachinery:any_machine_at_all"));
        // player energy channels stay allowed unless the file says otherwise
        assertTrue(FirmChannelConfig.parse("{ \"channels\": [] }").allowsPlayerEnergyChannels());
        assertFalse(FirmChannelConfig.parse("{ \"allowPlayerEnergyChannels\": false, \"channels\": [] }")
                .allowsPlayerEnergyChannels());
    }

    @Test
    void unknownKeysAreIgnored() {
        FirmChannelDefinition definition = only("""
                { "channels": [ { "channelId": "temp", "channelDisplayName": "typo", "isEnegry": true } ] }
                """);
        assertEquals("temp", definition.getChannelId());
        assertEquals("", definition.getChannelLocalization());
        assertFalse(definition.isEnergy());
    }

    @Test
    void anUnusableIdCostsThatEntryOnly() {
        FirmChannelConfig config = FirmChannelConfig.parse("""
                {
                  "channels": [
                    { "channelId": "bad id!" },
                    { "channelId": "2starts_with_a_digit" },
                    { "channelId": "this_id_is_far_too_long_to_be_used_as_a_script_variable" },
                    { "channelId": "temp" }
                  ]
                }
                """);
        assertEquals(1, config.definitionsOf().size());
        assertEquals("temp", config.definitionsOf().get(0).getChannelId());
    }

    @Test
    void aDuplicateIdKeepsTheFirstDeclaration() {
        FirmChannelConfig config = FirmChannelConfig.parse("""
                {
                  "channels": [
                    { "channelId": "temp", "isPublic": true },
                    { "channelId": "temp", "isPublic": false }
                  ]
                }
                """);
        assertEquals(1, config.definitionsOf().size());
        assertTrue(config.definitionsOf().get(0).isPublic());
    }

    @Test
    void masterToHasToBeAnArray() {
        FirmChannelConfig config = FirmChannelConfig.parse("""
                {
                  "channels": [
                    { "channelId": "temp", "masterTo": "alloy_furnace" },
                    { "channelId": "energy", "isEnergy": true, "masterTo": [] },
                    { "channelId": "open", "masterTo": [""] }
                  ]
                }
                """);
        // a single string is the one mistake that silently widens a restriction if it is ignored, so the entry goes
        List<String> ids = config.definitionsOf().stream().map(FirmChannelDefinition::getChannelId).toList();
        assertEquals(List.of("energy", "open"), ids);
        // an empty array means the same as a single empty entry: every machine
        assertFalse(config.definitionsOf().get(0).isMachineRestricted());
        assertFalse(config.definitionsOf().get(1).isMachineRestricted());
    }

    @Test
    void channelsThatAreNotAnArrayLeaveTheToggleAlone() {
        FirmChannelConfig config = FirmChannelConfig.parse("""
                { "allowPlayerEnergyChannels": false, "channels": { "channelId": "temp" } }
                """);
        assertTrue(config.definitionsOf().isEmpty());
        assertFalse(config.allowsPlayerEnergyChannels());
    }

    @Test
    void brokenJsonIsRejectedInsteadOfBeingHalfRead() {
        assertThrows(RuntimeException.class, () -> FirmChannelConfig.parse("{ \"channels\": [ "));
        assertThrows(RuntimeException.class, () -> FirmChannelConfig.parse("[]"));
    }

    @Test
    void severalPerPlayerEnergyChannelsAreAllDeclared() {
        FirmChannelConfig config = FirmChannelConfig.parse("""
                {
                  "channels": [
                    { "channelId": "first", "isPublic": false, "isEnergy": true },
                    { "channelId": "second", "isPublic": false, "isEnergy": true }
                  ]
                }
                """);
        // both stay declared and usable, which of them is the player's energy channel is decided when one is asked for
        assertEquals(2, config.definitionsOf().size());
        assertTrue(config.definitionsOf().get(0).isEnergy());
        assertFalse(config.definitionsOf().get(0).isPublic());
    }

    @Test
    void anIdThatCouldNotBeAScriptVariableIsRefused() {
        assertTrue(FirmChannelDefinition.isValidId("temp"));
        assertTrue(FirmChannelDefinition.isValidId("_temp2"));
        assertFalse(FirmChannelDefinition.isValidId("temp-2"));
        assertFalse(FirmChannelDefinition.isValidId("temp.now"));
        assertFalse(FirmChannelDefinition.isValidId("2temp"));
        assertFalse(FirmChannelDefinition.isValidId(""));
        assertNull(FirmChannelDefinition.create("no good", "", true, false, List.of(), false));
    }

}

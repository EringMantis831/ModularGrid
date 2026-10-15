package com.modulargrid.common.block;

import com.modulargrid.ModularGrid;
import com.modulargrid.Tags;
import com.modulargrid.common.integration.circulation.CirculationIntegration;
import com.modulargrid.common.integration.fluxnetworks.FluxNetworksIntegration;
import com.modulargrid.common.tile.TileAccessPlug;
import hellfirepvp.modularmachinery.common.CommonProxy;
import hellfirepvp.modularmachinery.common.block.BlockMachineComponent;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 流通数据节点 / Data Flow Access Plug.
 *
 * <p>A Modular Machinery machine component, usable in machine structures. It is also usable
 * wherever a structure accepts an energy hatch, and it exposes Forge Energy itself, see
 * {@link TileAccessPlug}.</p>
 */
public class BlockAccessPlug extends BlockMachineComponent {

    public static final String REGISTRY_NAME = "access_plug";

    public static final BlockAccessPlug INSTANCE = new BlockAccessPlug();

    protected BlockAccessPlug() {
        super(Material.IRON);
        setHardness(5.0F);
        setResistance(15.0F);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 1);
        // MMCE's creative tab, created in its preInit, so it is available once blocks are registered
        setCreativeTab(CommonProxy.creativeTabModularMachinery);
        setRegistryName(Tags.MOD_ID, REGISTRY_NAME);
        setTranslationKey(Tags.MOD_ID + '.' + REGISTRY_NAME);
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileAccessPlug();
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileAccessPlug();
    }

    /**
     * Remembers who placed the plug. While the plug is not part of a formed machine that player's
     * channels are the ones it exposes, so a loose plug still works as a channel port.
     */
    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer,
                                ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (world.isRemote || !(placer instanceof EntityPlayer player)) {
            return;
        }
        if (world.getTileEntity(pos) instanceof TileAccessPlug plug) {
            plug.setPlacer(player.getUniqueID());
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        String prefix = "tile." + Tags.MOD_ID + "." + REGISTRY_NAME + ".tooltip.";
        tooltip.add(TextFormatting.GRAY + I18n.format(prefix + "transfer"));
        if (Loader.isModLoaded("ic2")) {
            // the voltage is an IndustrialCraft 2 figure, so it is only worth a line while that mod is there
            tooltip.add(TextFormatting.GRAY + I18n.format(prefix + "voltage"));
        }
        boolean circulation = CirculationIntegration.isAvailable();
        boolean fluxNetworks = FluxNetworksIntegration.isAvailable();
        if (circulation || fluxNetworks) {
            // what the plug moves and what it can be plugged into are two different things
            tooltip.add("");
        }
        if (circulation) {
            tooltip.add(TextFormatting.GRAY + I18n.format(prefix + "circulation"));
        }
        if (fluxNetworks) {
            tooltip.add(TextFormatting.GRAY + I18n.format(prefix + "flux"));
        }
        tooltip.add(TextFormatting.GRAY + I18n.format(prefix + "open"));
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            // the server decides whether the GUI opens
            return true;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileAccessPlug plug)) {
            return false;
        }
        if (plug.getMachineOwner() == null) {
            // A loose plug belongs to its placer and can still be configured as a Flux Networks connector.
            if (FluxNetworksIntegration.isAvailable() && plug.isPlugOwner(player)) {
                player.openGui(ModularGrid.instance, com.modulargrid.common.CommonProxy.GuiType.ACCESS_PLUG.ordinal(),
                        world, pos.getX(), pos.getY(), pos.getZ());
                return true;
            }
            player.sendMessage(new TextComponentTranslation("message.modulargrid.notFormed"));
            return true;
        }
        if (!plug.isOwner(player)) {
            player.sendMessage(new TextComponentTranslation("message.modulargrid.notOwner"));
            return true;
        }
        player.openGui(ModularGrid.instance, com.modulargrid.common.CommonProxy.GuiType.ACCESS_PLUG.ordinal(),
                world, pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

}

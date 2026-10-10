package com.modulargrid.common.block;

import com.modulargrid.Tags;
import com.modulargrid.common.tile.TileAccessPlug;
import hellfirepvp.modularmachinery.common.CommonProxy;
import hellfirepvp.modularmachinery.common.block.BlockMachineComponent;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * 流通数据节点 / Data Flow Access Plug.
 *
 * <p>A Modular Machinery machine component, usable in machine structures.
 * Its actual behaviour is still to be defined, see {@link TileAccessPlug}.</p>
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

}

package com.modulargrid.common.integration.circulation;

import com.circulation.circulation_networks.CFNConfig;
import com.circulation.circulation_networks.api.API;
import com.circulation.circulation_networks.api.node.INode;
import com.circulation.circulation_networks.api.node.NodeContext;
import com.circulation.circulation_networks.api.node.NodeType;
import com.circulation.circulation_networks.manager.NetworkManager;
import com.circulation.circulation_networks.network.nodes.NodeFactory;
import com.circulation.circulation_networks.network.nodes.PortNode;
import com.circulation.circulation_networks.registry.NodeTypes;
import com.modulargrid.ModularGrid;
import com.modulargrid.common.tile.TileAccessPlug;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Bridges an access plug into CFN's actual node graph as a Port Node. */
public final class AccessPlugCfnNode {

    private AccessPlugCfnNode() {
    }

    public static INode getNode(TileAccessPlug plug) {
        Object node = plug.getCirculationNode();
        return node instanceof INode ? (INode) node : null;
    }

    public static NodeType<PortNode> getNodeType() {
        return NodeTypes.PORT_NODE;
    }

    public static BlockPos getNodePos(TileAccessPlug plug) {
        return plug.getPos();
    }

    public static World getNodeWorld(TileAccessPlug plug) {
        return plug.getWorld();
    }

    /** Matches CFN's BaseNodeTileEntity validation and adds the node through its normal network manager. */
    public static void validate(TileAccessPlug plug) {
        World world = plug.getWorld();
        if (world == null || world.isRemote || plug.isInvalid() || !NetworkManager.INSTANCE.isInit()) {
            return;
        }
        BlockPos pos = plug.getPos();
        INode node = getNode(plug);
        INode indexed = API.getNodeAt(world, pos);
        if (!NodeTypes.PORT_NODE.matches(node)) {
            if (NodeTypes.PORT_NODE.matches(indexed)) {
                node = indexed;
            } else {
                node = NodeFactory.createNode(NodeTypes.PORT_NODE, NodeContext.fromWorld(world, pos));
            }
            plug.setCirculationNode(node);
            plug.setCirculationNodeRegistered(false);
        } else if (indexed != null && indexed != node) {
            NetworkManager.INSTANCE.removeNode(indexed.getDimensionId(), indexed.getPos());
        }
        node.setActive(true);
        if (plug.isCirculationNodeRegistered() && indexed == node) {
            return;
        }
        NetworkManager.AddNodeResult result = NetworkManager.INSTANCE.addNode(node, plug);
        if (result.isSuccess()) {
            plug.setCirculationNodeRegistered(true);
            ModularGrid.LOGGER.info("Access plug at {} registered as a Circulation Flow Networks Port Node", pos);
        } else {
            plug.setCirculationNodeRegistered(false);
            ModularGrid.LOGGER.warn("Could not register access plug at {} as a Circulation Flow Networks Port Node: {}",
                    pos, result.getStatus());
        }
    }

    public static void invalidate(TileAccessPlug plug) {
        INode node = getNode(plug);
        if (node == null) {
            return;
        }
        node.setActive(false);
        NetworkManager.INSTANCE.removeNode(node.getDimensionId(), node.getPos());
        plug.setCirculationNode(null);
    }

    public static void syncAfterNetworkInit(TileAccessPlug plug) {
        validate(plug);
    }
}

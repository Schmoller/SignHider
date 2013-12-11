package au.com.addstar.signhider;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;

import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;

import com.comphenix.protocol.Packets;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;

public class BlockSender
{
	private Player mPlayer;
	private HashMap<Chunk, OutputSet> mChunks;
	
	private LinkedList<PacketContainer> mTilePackets;
	
	public void begin(Player player)
	{
		mPlayer = player;
	
		mChunks = new HashMap<Chunk, OutputSet>();
		mTilePackets = new LinkedList<PacketContainer>();
	}
	
	@SuppressWarnings( "deprecation" )
	public void add(BlockVector location)
	{
		Block block = mPlayer.getWorld().getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
		add(location, block.getTypeId(), block.getData());
	}
	public void add(BlockVector location, int material, int data)
	{
		Chunk chunk = mPlayer.getWorld().getChunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4);
		if(!mChunks.containsKey(chunk))
			mChunks.put(chunk, new OutputSet());
		
		OutputSet output = mChunks.get(chunk);
		
		short locPart = (short)((location.getBlockX() & 0xF) << 12 | (location.getBlockZ() & 0xF) << 8 | (location.getBlockY() & 0xFF));
		short dataPart = (short)((material & 4095) << 4 | (data & 0xF));
		
		try
		{
			output.output.writeShort(locPart);
			output.output.writeShort(dataPart);
			++output.blockCount;
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}
	
	public void setText(BlockVector location, String[] text)
	{
		PacketContainer packet = new PacketContainer(Packets.Server.UPDATE_SIGN);
		packet.getIntegers().write(0, location.getBlockX());
		packet.getIntegers().write(1, location.getBlockY());
		packet.getIntegers().write(2, location.getBlockZ());
		
		packet.getStringArrays().write(0, text);
		
		mTilePackets.add(packet);
	}
	
	public void end()
	{
		try
		{
			for(Entry<Chunk, OutputSet> entry : mChunks.entrySet())
			{
				PacketContainer packet = new PacketContainer(Packets.Server.MULTI_BLOCK_CHANGE);
				SignHiderPlugin.setChunkCoord(packet, entry.getKey().getX(), entry.getKey().getZ());
				packet.getIntegers().write(0, entry.getValue().blockCount);
				packet.getByteArrays().write(0, entry.getValue().stream.toByteArray());
				
				
				ProtocolLibrary.getProtocolManager().sendServerPacket(mPlayer, packet, false);
			}
			
			for(PacketContainer tilePacket : mTilePackets)
			{
				ProtocolLibrary.getProtocolManager().sendServerPacket(mPlayer, tilePacket, false);
			}
		}
		catch ( InvocationTargetException e )
		{
			e.printStackTrace();
		}
		
		mPlayer = null;
		mChunks.clear();
		mChunks = null;
	}
	
	private class OutputSet
	{
		public OutputSet()
		{
			blockCount = 0;
			stream = new ByteArrayOutputStream();
			output = new DataOutputStream(stream);
		}
		
		public int blockCount;
		public DataOutputStream output;
		public ByteArrayOutputStream stream;
	}
}

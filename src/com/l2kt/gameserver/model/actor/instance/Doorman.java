package com.l2kt.gameserver.model.actor.instance;

import java.util.StringTokenizer;

import com.l2kt.gameserver.data.xml.DoorData;
import com.l2kt.gameserver.data.xml.TeleportLocationData;
import com.l2kt.gameserver.model.actor.template.NpcTemplate;
import com.l2kt.gameserver.model.location.TeleportLocation;
import com.l2kt.gameserver.network.SystemMessageId;
import com.l2kt.gameserver.network.serverpackets.ActionFailed;
import com.l2kt.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * An instance type extending {@link Folk}, used to open doors and teleport into specific locations. Used notably by Border Frontier captains, and Doorman (clan halls and castles).<br>
 * <br>
 * It has an active siege (false by default) and ownership (true by default) checks, which are overidden on children classes.<br>
 * <br>
 * It is the mother class of {@link ClanHallDoorman} and {@link CastleDoorman}.
 */
public class Doorman extends Folk
{
	public Doorman(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		if (command.startsWith("open_doors"))
		{
			if (isOwnerClan(player))
			{
				if (isUnderSiege())
				{
					cannotManageDoors(player);
					player.sendPacket(SystemMessageId.GATES_NOT_OPENED_CLOSED_DURING_SIEGE);
				}
				else
					openDoors(player, command);
			}
		}
		else if (command.startsWith("close_doors"))
		{
			if (isOwnerClan(player))
			{
				if (isUnderSiege())
				{
					cannotManageDoors(player);
					player.sendPacket(SystemMessageId.GATES_NOT_OPENED_CLOSED_DURING_SIEGE);
				}
				else
					closeDoors(player, command);
			}
		}
		else if (command.startsWith("tele"))
		{
			if (isOwnerClan(player))
				doTeleport(player, command);
		}
		else
			super.onBypassFeedback(player, command);
	}
	
	@Override
	public void showChatWindow(Player player)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile("data/html/doormen/" + ((NpcTemplate)getTemplate()).getNpcId() + ((!isOwnerClan(player)) ? "-no.htm" : ".htm"));
		html.replace("%objectId%", getObjectId());
		player.sendPacket(html);
		
		player.sendPacket(ActionFailed.Companion.getSTATIC_PACKET());
	}
	
	protected void openDoors(Player player, String command)
	{
		StringTokenizer st = new StringTokenizer(command.substring(10), ", ");
		st.nextToken();
		
		while (st.hasMoreTokens())
			DoorData.INSTANCE.getDoor(Integer.parseInt(st.nextToken())).openMe();
	}
	
	protected void closeDoors(Player player, String command)
	{
		StringTokenizer st = new StringTokenizer(command.substring(11), ", ");
		st.nextToken();
		
		while (st.hasMoreTokens())
			DoorData.INSTANCE.getDoor(Integer.parseInt(st.nextToken())).closeMe();
	}
	
	protected void cannotManageDoors(Player player)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile("data/html/doormen/busy.htm");
		player.sendPacket(html);
		
		player.sendPacket(ActionFailed.Companion.getSTATIC_PACKET());
	}
	
	protected void doTeleport(Player player, String command)
	{
		final TeleportLocation list = TeleportLocationData.INSTANCE.getTeleportLocation(Integer.parseInt(command.substring(5).trim()));
		if (list != null && !player.isAlikeDead())
			player.teleToLocation(list, 0);
		
		player.sendPacket(ActionFailed.Companion.getSTATIC_PACKET());
	}
	
	protected boolean isOwnerClan(Player player)
	{
		return true;
	}
	
	protected boolean isUnderSiege()
	{
		return false;
	}
}
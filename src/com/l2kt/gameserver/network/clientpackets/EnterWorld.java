package com.l2kt.gameserver.network.clientpackets;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map.Entry;

import com.l2kt.Config;
import com.l2kt.L2DatabaseFactory;
import com.l2kt.gameserver.data.SkillTable;
import com.l2kt.gameserver.data.manager.CastleManager;
import com.l2kt.gameserver.data.manager.CoupleManager;
import com.l2kt.gameserver.data.manager.DimensionalRiftManager;
import com.l2kt.gameserver.data.manager.PetitionManager;
import com.l2kt.gameserver.data.xml.AdminData;
import com.l2kt.gameserver.data.xml.AnnouncementData;
import com.l2kt.gameserver.data.xml.MapRegionData;
import com.l2kt.gameserver.data.xml.ScriptData;
import com.l2kt.gameserver.model.L2Skill;
import com.l2kt.gameserver.model.World;
import com.l2kt.gameserver.model.actor.instance.Player;
import com.l2kt.gameserver.model.base.ClassRace;
import com.l2kt.gameserver.model.entity.Castle;
import com.l2kt.gameserver.model.entity.ClanHall;
import com.l2kt.gameserver.model.entity.Siege;
import com.l2kt.gameserver.model.holder.IntIntHolder;
import com.l2kt.gameserver.model.olympiad.Olympiad;
import com.l2kt.gameserver.model.pledge.Clan;
import com.l2kt.gameserver.model.pledge.SubPledge;
import com.l2kt.gameserver.model.zone.ZoneId;
import com.l2kt.gameserver.network.SystemMessageId;
import com.l2kt.gameserver.communitybbs.Manager.MailBBSManager;
import com.l2kt.gameserver.instancemanager.ClanHallManager;
import com.l2kt.gameserver.instancemanager.SevenSigns;
import com.l2kt.gameserver.instancemanager.SevenSigns.CabalType;
import com.l2kt.gameserver.instancemanager.SevenSigns.SealType;
import com.l2kt.gameserver.network.serverpackets.ActionFailed;
import com.l2kt.gameserver.network.serverpackets.Die;
import com.l2kt.gameserver.network.serverpackets.EtcStatusUpdate;
import com.l2kt.gameserver.network.serverpackets.ExMailArrived;
import com.l2kt.gameserver.network.serverpackets.ExStorageMaxCount;
import com.l2kt.gameserver.network.serverpackets.FriendList;
import com.l2kt.gameserver.network.serverpackets.HennaInfo;
import com.l2kt.gameserver.network.serverpackets.ItemList;
import com.l2kt.gameserver.network.serverpackets.NpcHtmlMessage;
import com.l2kt.gameserver.network.serverpackets.PlaySound;
import com.l2kt.gameserver.network.serverpackets.PledgeShowMemberListAll;
import com.l2kt.gameserver.network.serverpackets.PledgeShowMemberListUpdate;
import com.l2kt.gameserver.network.serverpackets.PledgeSkillList;
import com.l2kt.gameserver.network.serverpackets.PledgeStatusChanged;
import com.l2kt.gameserver.network.serverpackets.QuestList;
import com.l2kt.gameserver.network.serverpackets.ShortCutInit;
import com.l2kt.gameserver.network.serverpackets.SkillCoolTime;
import com.l2kt.gameserver.network.serverpackets.SystemMessage;
import com.l2kt.gameserver.network.serverpackets.UserInfo;
import com.l2kt.gameserver.scripting.Quest;
import com.l2kt.gameserver.scripting.QuestState;
import com.l2kt.gameserver.taskmanager.GameTimeTaskManager;

public class EnterWorld extends L2GameClientPacket
{
	private static final String LOAD_PLAYER_QUESTS = "SELECT name,var,value FROM character_quests WHERE charId=?";
	
	@Override
	protected void readImpl()
	{
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getClient().getActiveChar();
		if (player == null)
		{
			getClient().closeNow();
			return;
		}
		
		final int objectId = player.getObjectId();
		
		if (player.isGM())
		{
			if (Config.GM_STARTUP_INVULNERABLE && AdminData.getInstance().hasAccess("admin_setinvul", player.getAccessLevel()))
				player.setIsMortal(false);
			
			if (Config.GM_STARTUP_INVISIBLE && AdminData.getInstance().hasAccess("admin_hide", player.getAccessLevel()))
				player.getAppearance().setInvisible();
			
			if (Config.GM_STARTUP_SILENCE && AdminData.getInstance().hasAccess("admin_silence", player.getAccessLevel()))
				player.setInRefusalMode(true);
			
			if (Config.GM_STARTUP_AUTO_LIST && AdminData.getInstance().hasAccess("admin_gmlist", player.getAccessLevel()))
				AdminData.getInstance().addGm(player, false);
			else
				AdminData.getInstance().addGm(player, true);
		}
		
		// Set dead status if applies
		if (player.getCurrentHp() < 0.5 && player.isMortal())
			player.setIsDead(true);
		
		// Clan checks.
		final Clan clan = player.getClan();
		if (clan != null)
		{
			player.sendPacket(new PledgeSkillList(clan));
			
			// Refresh player instance.
			clan.getClanMember(objectId).setPlayerInstance(player);
			
			final SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.CLAN_MEMBER_S1_LOGGED_IN).addCharName(player);
			final PledgeShowMemberListUpdate update = new PledgeShowMemberListUpdate(player);
			
			// Send packets to others members.
			for (Player member : clan.getOnlineMembers())
			{
				if (member == player)
					continue;
				
				member.sendPacket(msg);
				member.sendPacket(update);
			}
			
			// Send a login notification to sponsor or apprentice, if logged.
			if (player.getSponsor() != 0)
			{
				final Player sponsor = World.getInstance().getPlayer(player.getSponsor());
				if (sponsor != null)
					sponsor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOUR_APPRENTICE_S1_HAS_LOGGED_IN).addCharName(player));
			}
			else if (player.getApprentice() != 0)
			{
				final Player apprentice = World.getInstance().getPlayer(player.getApprentice());
				if (apprentice != null)
					apprentice.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOUR_SPONSOR_S1_HAS_LOGGED_IN).addCharName(player));
			}
			
			// Add message at connexion if clanHall not paid.
			final ClanHall clanHall = ClanHallManager.getInstance().getClanHallByOwner(clan);
			if (clanHall != null && !clanHall.getPaid())
				player.sendPacket(SystemMessageId.PAYMENT_FOR_YOUR_CLAN_HALL_HAS_NOT_BEEN_MADE_PLEASE_MAKE_PAYMENT_TO_YOUR_CLAN_WAREHOUSE_BY_S1_TOMORROW);
			
			for (Castle castle : CastleManager.getInstance().getCastles())
			{
				final Siege siege = castle.getSiege();
				if (!siege.isInProgress())
					continue;
				
				final Siege.SiegeSide type = siege.getSide(clan);
				if (type == Siege.SiegeSide.ATTACKER)
					player.setSiegeState((byte) 1);
				else if (type == Siege.SiegeSide.DEFENDER || type == Siege.SiegeSide.OWNER)
					player.setSiegeState((byte) 2);
			}
			
			player.sendPacket(new PledgeShowMemberListAll(clan, 0));
			
			for (SubPledge sp : clan.getAllSubPledges())
				player.sendPacket(new PledgeShowMemberListAll(clan, sp.getId()));
			
			player.sendPacket(new UserInfo(player));
			player.sendPacket(new PledgeStatusChanged(clan));
		}
		
		// Updating Seal of Strife Buff/Debuff
		if (SevenSigns.getInstance().isSealValidationPeriod() && SevenSigns.getInstance().getSealOwner(SealType.STRIFE) != CabalType.NORMAL)
		{
			CabalType cabal = SevenSigns.getInstance().getPlayerCabal(objectId);
			if (cabal != CabalType.NORMAL)
			{
				if (cabal == SevenSigns.getInstance().getSealOwner(SealType.STRIFE))
					player.addSkill(SkillTable.FrequentSkill.THE_VICTOR_OF_WAR.getSkill(), false);
				else
					player.addSkill(SkillTable.FrequentSkill.THE_VANQUISHED_OF_WAR.getSkill(), false);
			}
		}
		else
		{
			player.removeSkill(SkillTable.FrequentSkill.THE_VICTOR_OF_WAR.getSkill().getId(), false);
			player.removeSkill(SkillTable.FrequentSkill.THE_VANQUISHED_OF_WAR.getSkill().getId(), false);
		}
		
		if (Config.PLAYER_SPAWN_PROTECTION > 0)
			player.setSpawnProtection(true);
		
		player.spawnMe();
		
		// Engage and notify partner.
		if (Config.ALLOW_WEDDING)
		{
			for (Entry<Integer, IntIntHolder> coupleEntry : CoupleManager.getInstance().getCouples().entrySet())
			{
				final IntIntHolder couple = coupleEntry.getValue();
				if (couple.getId() == objectId || couple.getValue() == objectId)
				{
					player.setCoupleId(coupleEntry.getKey());
					break;
				}
			}
		}
		
		// Announcements, welcome & Seven signs period messages
		player.sendPacket(SystemMessageId.WELCOME_TO_LINEAGE);
		player.sendPacket(SevenSigns.getInstance().getCurrentPeriod().getMessageId());
		AnnouncementData.getInstance().showAnnouncements(player, false);
		
		// if player is DE, check for shadow sense skill at night
		if (player.getRace() == ClassRace.DARK_ELF && player.hasSkill(L2Skill.SKILL_SHADOW_SENSE))
			player.sendPacket(SystemMessage.getSystemMessage((GameTimeTaskManager.getInstance().isNight()) ? SystemMessageId.NIGHT_S1_EFFECT_APPLIES : SystemMessageId.DAY_S1_EFFECT_DISAPPEARS).addSkillName(L2Skill.SKILL_SHADOW_SENSE));
		
		player.getMacroses().sendUpdate();
		player.sendPacket(new UserInfo(player));
		player.sendPacket(new HennaInfo(player));
		player.sendPacket(new FriendList(player));
		// activeChar.queryGameGuard();
		player.sendPacket(new ItemList(player, false));
		player.sendPacket(new ShortCutInit(player));
		player.sendPacket(new ExStorageMaxCount(player));
		
		// no broadcast needed since the player will already spawn dead to others
		if (player.isAlikeDead())
			player.sendPacket(new Die(player));
		
		player.updateEffectIcons();
		player.sendPacket(new EtcStatusUpdate(player));
		player.sendSkillList();
		
		// Load quests.
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(LOAD_PLAYER_QUESTS))
		{
			ps.setInt(1, objectId);
			
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					final String questName = rs.getString("name");
					
					// Test quest existence.
					final Quest quest = ScriptData.getInstance().getQuest(questName);
					if (quest == null)
					{
						LOGGER.warn("Unknown quest {} for player {}.", questName, player.getName());
						continue;
					}
					
					// Each quest get a single state ; create one QuestState per found <state> variable.
					final String var = rs.getString("var");
					if (var.equals("<state>"))
					{
						new QuestState(player, quest, rs.getByte("value"));
						
						// Notify quest for enterworld event, if quest allows it.
						if (quest.getOnEnterWorld())
							quest.notifyEnterWorld(player);
					}
					// Feed an existing quest state.
					else
					{
						final QuestState qs = player.getQuestState(questName);
						if (qs == null)
						{
							LOGGER.warn("Unknown quest state {} for player {}.", questName, player.getName());
							continue;
						}
						
						qs.setInternal(var, rs.getString("value"));
					}
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't load quests for player {}.", e, player.getName());
		}
		
		player.sendPacket(new QuestList(player));
		
		// Unread mails make a popup appears.
		if (Config.ENABLE_COMMUNITY_BOARD && MailBBSManager.getInstance().checkUnreadMail(player) > 0)
		{
			player.sendPacket(SystemMessageId.NEW_MAIL);
			player.sendPacket(new PlaySound("systemmsg_e.1233"));
			player.sendPacket(ExMailArrived.STATIC_PACKET);
		}
		
		// Clan notice, if active.
		if (Config.ENABLE_COMMUNITY_BOARD && clan != null && clan.isNoticeEnabled())
		{
			final NpcHtmlMessage html = new NpcHtmlMessage(0);
			html.setFile("data/html/clan_notice.htm");
			html.replace("%clan_name%", clan.getName());
			html.replace("%notice_text%", clan.getNotice().replaceAll("\r\n", "<br>").replaceAll("action", "").replaceAll("bypass", ""));
			sendPacket(html);
		}
		else if (Config.SERVER_NEWS)
		{
			final NpcHtmlMessage html = new NpcHtmlMessage(0);
			html.setFile("data/html/servnews.htm");
			sendPacket(html);
		}
		
		PetitionManager.getInstance().checkPetitionMessages(player);
		
		player.onPlayerEnter();
		
		sendPacket(new SkillCoolTime(player));
		
		// If player logs back in a stadium, port him in nearest town.
		if (Olympiad.getInstance().playerInStadia(player))
			player.teleToLocation(MapRegionData.TeleportType.TOWN);
		
		if (DimensionalRiftManager.getInstance().checkIfInRiftZone(player.getX(), player.getY(), player.getZ(), false))
			DimensionalRiftManager.getInstance().teleportToWaitingRoom(player);
		
		if (player.getClanJoinExpiryTime() > System.currentTimeMillis())
			player.sendPacket(SystemMessageId.CLAN_MEMBERSHIP_TERMINATED);
		
		// Attacker or spectator logging into a siege zone will be ported at town.
		if (!player.isGM() && (!player.isInSiege() || player.getSiegeState() < 2) && player.isInsideZone(ZoneId.SIEGE))
			player.teleToLocation(MapRegionData.TeleportType.TOWN);
		
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	protected boolean triggersOnActionRequest()
	{
		return false;
	}
}
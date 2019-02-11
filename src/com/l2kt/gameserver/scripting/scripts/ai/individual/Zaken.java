package com.l2kt.gameserver.scripting.scripts.ai.individual;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.l2kt.Config;
import com.l2kt.commons.random.Rnd;
import com.l2kt.gameserver.data.SkillTable;
import com.l2kt.gameserver.data.manager.ZoneManager;
import com.l2kt.gameserver.data.xml.DoorData;
import com.l2kt.gameserver.model.L2Skill;
import com.l2kt.gameserver.model.WorldObject;
import com.l2kt.gameserver.model.actor.Attackable;
import com.l2kt.gameserver.model.actor.Creature;
import com.l2kt.gameserver.model.actor.Npc;
import com.l2kt.gameserver.model.actor.Playable;
import com.l2kt.gameserver.model.actor.ai.CtrlIntention;
import com.l2kt.gameserver.model.actor.instance.Door;
import com.l2kt.gameserver.model.actor.instance.GrandBoss;
import com.l2kt.gameserver.model.actor.instance.Player;
import com.l2kt.gameserver.model.location.Location;
import com.l2kt.gameserver.model.zone.type.BossZone;
import com.l2kt.gameserver.network.serverpackets.PlaySound;
import com.l2kt.gameserver.scripting.scripts.ai.L2AttackableAIScript;

import com.l2kt.gameserver.instancemanager.GrandBossManager;
import com.l2kt.gameserver.taskmanager.GameTimeTaskManager;
import com.l2kt.gameserver.templates.StatsSet;

public class Zaken extends L2AttackableAIScript
{
	private static final BossZone ZONE = ZoneManager.getInstance().getZoneById(110000, BossZone.class);
	private static final Set<Player> VICTIMS = ConcurrentHashMap.newKeySet();
	
	private static final Location[] LOCS =
	{
		new Location(53950, 219860, -3488),
		new Location(55980, 219820, -3488),
		new Location(54950, 218790, -3488),
		new Location(55970, 217770, -3488),
		new Location(53930, 217760, -3488),
		
		new Location(55970, 217770, -3216),
		new Location(55980, 219920, -3216),
		new Location(54960, 218790, -3216),
		new Location(53950, 219860, -3216),
		new Location(53930, 217760, -3216),
		
		new Location(55970, 217770, -2944),
		new Location(55980, 219920, -2944),
		new Location(54960, 218790, -2944),
		new Location(53950, 219860, -2944),
		new Location(53930, 217760, -2944)
	};
	
	private static final int ZAKEN = 29022;
	private static final int DOLL_BLADER = 29023;
	private static final int VALE_MASTER = 29024;
	private static final int PIRATE_CAPTAIN = 29026;
	private static final int PIRATE_ZOMBIE = 29027;
	
	private static final byte ALIVE = 0;
	private static final byte DEAD = 1;
	
	private final Location _zakenLocation = new Location(0, 0, 0);
	
	private int _teleportCheck;
	private int _minionStatus;
	private int _hate;
	
	private boolean _hasTeleported;
	
	private Creature _mostHated;
	
	public Zaken()
	{
		super("ai/individual");
		
		final StatsSet info = GrandBossManager.getInstance().getStatsSet(ZAKEN);
		
		// Zaken is dead, calculate the respawn time. If passed, we spawn it directly, otherwise we set a task to spawn it lately.
		if (GrandBossManager.getInstance().getBossStatus(ZAKEN) == DEAD)
		{
			final long temp = info.getLong("respawn_time") - System.currentTimeMillis();
			if (temp > 0)
				startQuestTimer("zaken_unlock", temp, null, null, false);
			else
				spawnBoss(true);
		}
		// Zaken is alive, spawn it using stored data.
		else
			spawnBoss(false);
	}
	
	@Override
	protected void registerNpcs()
	{
		addAggroRangeEnterId(ZAKEN, DOLL_BLADER, VALE_MASTER, PIRATE_CAPTAIN, PIRATE_ZOMBIE);
		addAttackId(ZAKEN);
		addFactionCallId(DOLL_BLADER, VALE_MASTER, PIRATE_CAPTAIN, PIRATE_ZOMBIE);
		addKillId(ZAKEN, DOLL_BLADER, VALE_MASTER, PIRATE_CAPTAIN, PIRATE_ZOMBIE);
		addSkillSeeId(ZAKEN);
		addSpellFinishedId(ZAKEN);
		
		addGameTimeNotify();
	}
	
	@Override
	public String onAdvEvent(String event, Npc npc, Player player)
	{
		if (GrandBossManager.getInstance().getBossStatus(ZAKEN) == DEAD && !event.equalsIgnoreCase("zaken_unlock"))
			return super.onAdvEvent(event, npc, player);
		
		if (event.equalsIgnoreCase("1001"))
		{
			if (GameTimeTaskManager.INSTANCE.isNight())
			{
				L2Skill skill = SkillTable.FrequentSkill.ZAKEN_DAY_TO_NIGHT.getSkill();
				if (npc.getFirstEffect(skill) == null)
				{
					// Add effect "Day to Night" if not found.
					skill.getEffects(npc, npc);
					
					// Refresh stored Zaken location.
					_zakenLocation.set(npc.getPosition());
				}
				
				// Add Night regen if not found.
				skill = SkillTable.FrequentSkill.ZAKEN_REGEN_NIGHT.getSkill();
				if (npc.getFirstEffect(skill) == null)
					skill.getEffects(npc, npc);
				
				final Creature mostHated = ((Attackable) npc).getMostHated();
				
				// Under attack stance, but didn't yet teleported. Check most hated and current victims distance.
				if (npc.getAI().getDesire().getIntention() == CtrlIntention.ATTACK && !_hasTeleported)
				{
					boolean willTeleport = true;
					
					// Check most hated distance. If distance is low, Zaken doesn't teleport.
					if (mostHated != null && mostHated.isInsideRadius(_zakenLocation, 1500, true, false))
						willTeleport = false;
					
					// We're still under willTeleport possibility. Now we check each victim distance. If at least one is near Zaken, we cancel the teleport possibility.
					if (willTeleport)
					{
						for (Player ply : VICTIMS)
						{
							if (ply.isInsideRadius(_zakenLocation, 1500, true, false))
							{
								willTeleport = false;
								continue;
							}
						}
					}
					
					// All targets are far, clear victims list and Zaken teleport.
					if (willTeleport)
					{
						VICTIMS.clear();
						npc.doCast(SkillTable.FrequentSkill.ZAKEN_SELF_TELE.getSkill());
					}
				}
				
				// Potentially refresh the stored location.
				if (Rnd.INSTANCE.get(20) < 1 && !_hasTeleported)
					_zakenLocation.set(npc.getPosition());
				
				// Process to cleanup hate from most hated upon 5 straight AI loops.
				if (npc.getAI().getDesire().getIntention() == CtrlIntention.ATTACK && mostHated != null)
				{
					if (_hate == 0)
					{
						_mostHated = mostHated;
						_hate = 1;
					}
					else
					{
						if (_mostHated == mostHated)
							_hate++;
						else
						{
							_hate = 1;
							_mostHated = mostHated;
						}
					}
				}
				
				// Cleanup build hate towards Intention IDLE.
				if (npc.getAI().getDesire().getIntention() == CtrlIntention.IDLE)
					_hate = 0;
				
				// We built enough hate ; release the current most hated target, reset the hate counter.
				if (_hate > 5)
				{
					((Attackable) npc).stopHating(_mostHated);
					
					_hate = 0;
				}
			}
			else
			{
				L2Skill skill = SkillTable.FrequentSkill.ZAKEN_NIGHT_TO_DAY.getSkill();
				if (npc.getFirstEffect(skill) == null)
				{
					// Add effect "Night to Day" if not found.
					skill.getEffects(npc, npc);
					
					_teleportCheck = 3;
				}
				
				// Add Day regen if not found.
				skill = SkillTable.FrequentSkill.ZAKEN_REGEN_DAY.getSkill();
				if (npc.getFirstEffect(skill) == null)
					skill.getEffects(npc, npc);
			}
			
			if (Rnd.INSTANCE.get(40) < 1)
				npc.doCast(SkillTable.FrequentSkill.ZAKEN_SELF_TELE.getSkill());
			
			startQuestTimer("1001", 30000, npc, null, false);
		}
		else if (event.equalsIgnoreCase("1002"))
		{
			// Clear victims list.
			VICTIMS.clear();
			
			// Teleport Zaken.
			npc.doCast(SkillTable.FrequentSkill.ZAKEN_SELF_TELE.getSkill());
			
			// Flag the teleport as false.
			_hasTeleported = false;
		}
		else if (event.equalsIgnoreCase("1003"))
		{
			if (_minionStatus == 1)
			{
				spawnMinionOnEveryLocation(PIRATE_CAPTAIN, 1);
				
				// Pass to the next spawn cycle.
				_minionStatus = 2;
			}
			else if (_minionStatus == 2)
			{
				spawnMinionOnEveryLocation(DOLL_BLADER, 1);
				
				// Pass to the next spawn cycle.
				_minionStatus = 3;
			}
			else if (_minionStatus == 3)
			{
				spawnMinionOnEveryLocation(VALE_MASTER, 2);
				
				// Pass to the next spawn cycle.
				_minionStatus = 4;
			}
			else if (_minionStatus == 4)
			{
				spawnMinionOnEveryLocation(PIRATE_ZOMBIE, 5);
				
				// Pass to the next spawn cycle.
				_minionStatus = 5;
			}
			else if (_minionStatus == 5)
			{
				addSpawn(DOLL_BLADER, 52675, 219371, -3290, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(DOLL_BLADER, 52687, 219596, -3368, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(DOLL_BLADER, 52672, 219740, -3418, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 52857, 219992, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 52959, 219997, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(VALE_MASTER, 53381, 220151, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 54236, 220948, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 54885, 220144, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 55264, 219860, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 55399, 220263, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 55679, 220129, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(VALE_MASTER, 56276, 220783, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(VALE_MASTER, 57173, 220234, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 56267, 218826, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(DOLL_BLADER, 56294, 219482, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 56094, 219113, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(DOLL_BLADER, 56364, 218967, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 57113, 218079, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(DOLL_BLADER, 56186, 217153, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 55440, 218081, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 55202, 217940, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 55225, 218236, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 54973, 218075, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 53412, 218077, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(VALE_MASTER, 54226, 218797, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(VALE_MASTER, 54394, 219067, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 54139, 219253, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(DOLL_BLADER, 54262, 219480, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				
				// Pass to the next spawn cycle.
				_minionStatus = 6;
			}
			else if (_minionStatus == 6)
			{
				addSpawn(PIRATE_ZOMBIE, 53412, 218077, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(VALE_MASTER, 54413, 217132, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(DOLL_BLADER, 54841, 217132, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(DOLL_BLADER, 55372, 217128, -3343, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(DOLL_BLADER, 55893, 217122, -3488, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 56282, 217237, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(VALE_MASTER, 56963, 218080, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 56267, 218826, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(DOLL_BLADER, 56294, 219482, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 56094, 219113, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(DOLL_BLADER, 56364, 218967, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(VALE_MASTER, 56276, 220783, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(VALE_MASTER, 57173, 220234, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 54885, 220144, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 55264, 219860, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 55399, 220263, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 55679, 220129, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 54236, 220948, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 54464, 219095, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(VALE_MASTER, 54226, 218797, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(VALE_MASTER, 54394, 219067, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 54139, 219253, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(DOLL_BLADER, 54262, 219480, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 53412, 218077, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 55440, 218081, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 55202, 217940, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 55225, 218236, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 54973, 218075, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				
				// Pass to the next spawn cycle.
				_minionStatus = 7;
			}
			else if (_minionStatus == 7)
			{
				addSpawn(PIRATE_ZOMBIE, 54228, 217504, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(VALE_MASTER, 54181, 217168, -3216, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(DOLL_BLADER, 54714, 217123, -3168, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(DOLL_BLADER, 55298, 217127, -3073, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(DOLL_BLADER, 55787, 217130, -2993, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 56284, 217216, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(VALE_MASTER, 56963, 218080, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 56267, 218826, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(DOLL_BLADER, 56294, 219482, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 56094, 219113, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(DOLL_BLADER, 56364, 218967, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(VALE_MASTER, 56276, 220783, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(VALE_MASTER, 57173, 220234, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 54885, 220144, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 55264, 219860, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 55399, 220263, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 55679, 220129, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 54236, 220948, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 54464, 219095, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(VALE_MASTER, 54226, 218797, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(VALE_MASTER, 54394, 219067, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 54139, 219253, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(DOLL_BLADER, 54262, 219480, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 53412, 218077, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 54280, 217200, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 55440, 218081, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_CAPTAIN, 55202, 217940, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 55225, 218236, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				addSpawn(PIRATE_ZOMBIE, 54973, 218075, -2944, Rnd.INSTANCE.get(65536), false, 0, true);
				
				cancelQuestTimer("1003", null, null);
			}
		}
		else if (event.equalsIgnoreCase("zaken_unlock"))
		{
			// Spawn the boss.
			spawnBoss(true);
		}
		else if (event.equalsIgnoreCase("CreateOnePrivateEx"))
			addSpawn(npc.getNpcId(), npc.getX(), npc.getY(), npc.getZ(), Rnd.INSTANCE.get(65535), false, 0, true);
		
		return super.onAdvEvent(event, npc, player);
	}
	
	@Override
	public String onAggro(Npc npc, Player player, boolean isPet)
	{
		final Playable realBypasser = (isPet && player.getPet() != null) ? player.getPet() : player;
		
		if (ZONE.isInsideZone(npc))
			((Attackable) npc).addDamageHate(realBypasser, 1, 200);
		
		if (npc.getNpcId() == ZAKEN)
		{
			// Feed victims list, but only if not already full.
			if (Rnd.INSTANCE.get(3) < 1 && VICTIMS.size() < 5)
				VICTIMS.add(player);
			
			// Cast a skill.
			if (Rnd.INSTANCE.get(15) < 1)
				callSkills(npc, realBypasser);
		}
		else if (testCursesOnAggro(npc, realBypasser))
			return null;
		
		return super.onAggro(npc, player, isPet);
	}
	
	@Override
	public String onAttack(Npc npc, Creature attacker, int damage, L2Skill skill)
	{
		// Curses
		if (attacker instanceof Playable && testCursesOnAttack(npc, attacker))
			return null;
		
		if (Rnd.INSTANCE.get(10) < 1)
			callSkills(npc, attacker);
		
		if (!GameTimeTaskManager.INSTANCE.isNight() && (npc.getCurrentHp() < (npc.getMaxHp() * _teleportCheck) / 4))
		{
			_teleportCheck -= 1;
			npc.doCast(SkillTable.FrequentSkill.ZAKEN_SELF_TELE.getSkill());
		}
		return super.onAttack(npc, attacker, damage, skill);
	}
	
	@Override
	public String onFactionCall(Npc npc, Npc caller, Player attacker, boolean isPet)
	{
		if (caller.getNpcId() == ZAKEN && GameTimeTaskManager.INSTANCE.isNight())
		{
			if (npc.getAI().getDesire().getIntention() == CtrlIntention.IDLE && !_hasTeleported && caller.getCurrentHp() < (0.9 * caller.getMaxHp()) && Rnd.INSTANCE.get(450) < 1)
			{
				// Set the teleport flag as true.
				_hasTeleported = true;
				
				// Edit Zaken stored location.
				_zakenLocation.set(npc.getPosition());
				
				// Run the 1002 timer.
				startQuestTimer("1002", 300, caller, null, false);
			}
		}
		return super.onFactionCall(npc, caller, attacker, isPet);
	}
	
	@Override
	public String onKill(Npc npc, Creature killer)
	{
		if (npc.getNpcId() == ZAKEN)
		{
			// Broadcast death sound.
			npc.broadcastPacket(new PlaySound(1, "BS02_D", npc));
			
			// Flag Zaken as dead.
			GrandBossManager.getInstance().setBossStatus(ZAKEN, DEAD);
			
			// Calculate the next respawn time.
			final long respawnTime = (long) (Config.SPAWN_INTERVAL_ZAKEN + Rnd.INSTANCE.get(-Config.RANDOM_SPAWN_TIME_ZAKEN, Config.RANDOM_SPAWN_TIME_ZAKEN)) * 3600000;
			
			// Cancel tasks.
			cancelQuestTimer("1001", npc, null);
			cancelQuestTimer("1003", null, null);
			
			// Start respawn timer.
			startQuestTimer("zaken_unlock", respawnTime, null, null, false);
			
			// Save the respawn time so that the info is maintained past reboots
			final StatsSet info = GrandBossManager.getInstance().getStatsSet(ZAKEN);
			info.set("respawn_time", System.currentTimeMillis() + respawnTime);
			GrandBossManager.getInstance().setStatsSet(ZAKEN, info);
		}
		else if (GrandBossManager.getInstance().getBossStatus(ZAKEN) == ALIVE)
			startQuestTimer("CreateOnePrivateEx", ((30 + Rnd.INSTANCE.get(60)) * 1000), npc, null, false);
		
		return super.onKill(npc, killer);
	}
	
	@Override
	public String onSkillSee(Npc npc, Player caster, L2Skill skill, WorldObject[] targets, boolean isPet)
	{
		if (Rnd.INSTANCE.get(12) < 1)
			callSkills(npc, caster);
		
		return super.onSkillSee(npc, caster, skill, targets, isPet);
	}
	
	@Override
	public String onSpellFinished(Npc npc, Player player, L2Skill skill)
	{
		switch (skill.getId())
		{
			case 4222: // Instant Move; a self teleport skill Zaken uses to move from one point to another. Location is computed on the fly, depending conditions/checks.
				((Attackable) npc).cleanAllHate();
				npc.teleToLocation(_zakenLocation, 0);
				break;
			
			case 4216: // Scatter Enemy ; a target teleport skill, which teleports the targeted Player to a defined, random Location.
				((Attackable) npc).stopHating(player);
				player.teleToLocation(LOCS[Rnd.INSTANCE.get(15)], 0);
				break;
			
			case 4217: // Mass Teleport ; teleport victims and targeted Player, each on a defined, random Location.
				for (Player ply : VICTIMS)
				{
					if (ply.isInsideRadius(player, 250, true, false))
					{
						((Attackable) npc).stopHating(ply);
						ply.teleToLocation(LOCS[Rnd.INSTANCE.get(15)], 0);
					}
				}
				((Attackable) npc).stopHating(player);
				player.teleToLocation(LOCS[Rnd.INSTANCE.get(15)], 0);
				break;
		}
		return super.onSpellFinished(npc, player, skill);
	}
	
	@Override
	public void onGameTime()
	{
		if (GameTimeTaskManager.INSTANCE.getGameTime() == 0)
		{
			final Door door = DoorData.getInstance().getDoor(21240006);
			if (door != null)
				door.openMe();
		}
	}
	
	/**
	 * Call skills depending of luck and specific events.
	 * @param npc : The npc who casts the spell (Zaken).
	 * @param target : The target Zaken currently aims at.
	 */
	private static void callSkills(Npc npc, WorldObject target)
	{
		if (npc.isCastingNow())
			return;
		
		npc.setTarget(target);
		
		final int chance = Rnd.INSTANCE.get(225);
		if (chance < 1)
			npc.doCast(SkillTable.FrequentSkill.ZAKEN_TELE.getSkill());
		else if (chance < 2)
			npc.doCast(SkillTable.FrequentSkill.ZAKEN_MASS_TELE.getSkill());
		else if (chance < 4)
			npc.doCast(SkillTable.FrequentSkill.ZAKEN_HOLD.getSkill());
		else if (chance < 8)
			npc.doCast(SkillTable.FrequentSkill.ZAKEN_DRAIN.getSkill());
		else if (chance < 15)
		{
			if (target != ((Attackable) npc).getMostHated() && npc.isInsideRadius(target, 100, false, false))
				npc.doCast(SkillTable.FrequentSkill.ZAKEN_MASS_DUAL_ATTACK.getSkill());
		}
		
		if (Rnd.INSTANCE.nextBoolean() && target == ((Attackable) npc).getMostHated())
			npc.doCast(SkillTable.FrequentSkill.ZAKEN_DUAL_ATTACK.getSkill());
	}
	
	/**
	 * Make additional actions on boss spawn : register the NPC as boss, activate tasks.
	 * @param freshStart : If true, it uses static data, otherwise it uses stored data.
	 */
	private void spawnBoss(boolean freshStart)
	{
		final GrandBoss zaken;
		if (freshStart)
		{
			GrandBossManager.getInstance().setBossStatus(ZAKEN, ALIVE);
			
			final Location loc = LOCS[Rnd.INSTANCE.get(15)];
			zaken = (GrandBoss) addSpawn(ZAKEN, loc.getX(), loc.getY(), loc.getZ(), 0, false, 0, false);
		}
		else
		{
			final StatsSet info = GrandBossManager.getInstance().getStatsSet(ZAKEN);
			
			zaken = (GrandBoss) addSpawn(ZAKEN, info.getInteger("loc_x"), info.getInteger("loc_y"), info.getInteger("loc_z"), info.getInteger("heading"), false, 0, false);
			zaken.setCurrentHpMp(info.getInteger("currentHP"), info.getInteger("currentMP"));
		}
		
		GrandBossManager.getInstance().addBoss(zaken);
		
		// Reset variables.
		_teleportCheck = 3;
		_hate = 0;
		_hasTeleported = false;
		_mostHated = null;
		
		// Store current Zaken position.
		_zakenLocation.set(zaken.getPosition());
		
		// Clear victims list.
		VICTIMS.clear();
		
		// If Zaken is on its lair, begin the minions spawn cycle.
		if (ZONE.isInsideZone(zaken))
		{
			_minionStatus = 1;
			startQuestTimer("1003", 1700, null, null, true);
		}
		
		// Generic task is running from now.
		startQuestTimer("1001", 1000, zaken, null, false);
		
		zaken.broadcastPacket(new PlaySound(1, "BS01_A", zaken));
	}
	
	/**
	 * Spawn one {@link Npc} on every {@link Location} from the LOCS array. Process it for the roundsNumber amount.
	 * @param npcId : The npcId to spawn.
	 * @param roundsNumber : The rounds number to process.
	 */
	private void spawnMinionOnEveryLocation(int npcId, int roundsNumber)
	{
		for (Location loc : LOCS)
		{
			for (int i = 0; i < roundsNumber; i++)
			{
				final int x = loc.getX() + Rnd.INSTANCE.get(650);
				final int y = loc.getY() + Rnd.INSTANCE.get(650);
				
				addSpawn(npcId, x, y, loc.getZ(), Rnd.INSTANCE.get(65536), false, 0, true);
			}
		}
	}
}
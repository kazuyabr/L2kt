package com.l2kt.gameserver.network.clientpackets;

import com.l2kt.Config;
import com.l2kt.commons.concurrent.ThreadPool;
import com.l2kt.gameserver.handler.IItemHandler;
import com.l2kt.gameserver.handler.ItemHandler;
import com.l2kt.gameserver.model.L2Skill;
import com.l2kt.gameserver.model.actor.instance.Pet;
import com.l2kt.gameserver.model.actor.instance.Player;
import com.l2kt.gameserver.model.holder.IntIntHolder;
import com.l2kt.gameserver.model.item.instance.ItemInstance;
import com.l2kt.gameserver.model.item.kind.Item;
import com.l2kt.gameserver.model.item.type.ActionType;
import com.l2kt.gameserver.model.item.type.EtcItemType;
import com.l2kt.gameserver.model.item.type.WeaponType;
import com.l2kt.gameserver.model.itemcontainer.Inventory;
import com.l2kt.gameserver.network.SystemMessageId;

import com.l2kt.gameserver.network.serverpackets.ItemList;
import com.l2kt.gameserver.network.serverpackets.PetItemList;
import com.l2kt.gameserver.network.serverpackets.SystemMessage;
import com.l2kt.gameserver.scripting.Quest;
import com.l2kt.gameserver.scripting.QuestState;
import com.l2kt.gameserver.templates.skills.L2SkillType;

public final class UseItem extends L2GameClientPacket
{
	private int _objectId;
	private boolean _ctrlPressed;
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
		_ctrlPressed = readD() != 0;
	}
	
	@Override
	protected void runImpl()
	{
		final Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		if (activeChar.isInStoreMode())
		{
			activeChar.sendPacket(SystemMessageId.ITEMS_UNAVAILABLE_FOR_STORE_MANUFACTURE);
			return;
		}
		
		if (activeChar.getActiveTradeList() != null)
		{
			activeChar.sendPacket(SystemMessageId.CANNOT_PICKUP_OR_USE_ITEM_WHILE_TRADING);
			return;
		}
		
		final ItemInstance item = activeChar.getInventory().getItemByObjectId(_objectId);
		if (item == null)
			return;
		
		if (item.getItem().getType2() == Item.TYPE2_QUEST)
		{
			activeChar.sendPacket(SystemMessageId.CANNOT_USE_QUEST_ITEMS);
			return;
		}
		
		if (activeChar.isAlikeDead() || activeChar.isStunned() || activeChar.isSleeping() || activeChar.isParalyzed() || activeChar.isAfraid())
			return;
		
		if (!Config.KARMA_PLAYER_CAN_TELEPORT && activeChar.getKarma() > 0)
		{
			final IntIntHolder[] sHolders = item.getItem().getSkills();
			if (sHolders != null)
			{
				for (IntIntHolder sHolder : sHolders)
				{
					final L2Skill skill = sHolder.getSkill();
					if (skill != null && (skill.getSkillType() == L2SkillType.TELEPORT || skill.getSkillType() == L2SkillType.RECALL))
						return;
				}
			}
		}
		
		if (activeChar.isFishing() && item.getItem().getDefaultAction() != ActionType.fishingshot)
		{
			activeChar.sendPacket(SystemMessageId.CANNOT_DO_WHILE_FISHING_3);
			return;
		}
		
		/*
		 * The player can't use pet items if no pet is currently summoned. If a pet is summoned and player uses the item directly, it will be used by the pet.
		 */
		if (item.isPetItem())
		{
			// If no pet, cancels the use
			if (!activeChar.hasPet())
			{
				activeChar.sendPacket(SystemMessageId.CANNOT_EQUIP_PET_ITEM);
				return;
			}
			
			final Pet pet = ((Pet) activeChar.getPet());
			
			if (!pet.canWear(item.getItem()))
			{
				activeChar.sendPacket(SystemMessageId.PET_CANNOT_USE_ITEM);
				return;
			}
			
			if (pet.isDead())
			{
				activeChar.sendPacket(SystemMessageId.CANNOT_GIVE_ITEMS_TO_DEAD_PET);
				return;
			}
			
			if (!pet.getInventory().validateCapacity(item))
			{
				activeChar.sendPacket(SystemMessageId.YOUR_PET_CANNOT_CARRY_ANY_MORE_ITEMS);
				return;
			}
			
			if (!pet.getInventory().validateWeight(item, 1))
			{
				activeChar.sendPacket(SystemMessageId.UNABLE_TO_PLACE_ITEM_YOUR_PET_IS_TOO_ENCUMBERED);
				return;
			}
			
			activeChar.transferItem("Transfer", _objectId, 1, pet.getInventory(), pet);
			
			// Equip it, removing first the previous item.
			if (item.isEquipped())
			{
				pet.getInventory().unEquipItemInSlot(item.getLocationSlot());
				activeChar.sendPacket(SystemMessage.Companion.getSystemMessage(SystemMessageId.PET_TOOK_OFF_S1).addItemName(item));
			}
			else
			{
				pet.getInventory().equipPetItem(item);
				activeChar.sendPacket(SystemMessage.Companion.getSystemMessage(SystemMessageId.PET_PUT_ON_S1).addItemName(item));
			}
			
			activeChar.sendPacket(new PetItemList(pet));
			pet.updateAndBroadcastStatus(1);
			return;
		}
		
		if (!item.isEquipped())
		{
			if (!item.getItem().checkCondition(activeChar, activeChar, true))
				return;
		}
		
		if (item.isEquipable())
		{
			if (activeChar.isCastingNow() || activeChar.isCastingSimultaneouslyNow())
			{
				activeChar.sendPacket(SystemMessageId.CANNOT_USE_ITEM_WHILE_USING_MAGIC);
				return;
			}
			
			switch (item.getItem().getBodyPart())
			{
				case Item.SLOT_LR_HAND:
				case Item.SLOT_L_HAND:
				case Item.SLOT_R_HAND:
				{
					if (activeChar.isMounted())
					{
						activeChar.sendPacket(SystemMessageId.CANNOT_EQUIP_ITEM_DUE_TO_BAD_CONDITION);
						return;
					}
					
					// Don't allow weapon/shield equipment if a cursed weapon is equipped
					if (activeChar.isCursedWeaponEquipped())
						return;
					
					break;
				}
			}
			
			if (activeChar.isCursedWeaponEquipped() && item.getItemId() == 6408) // Don't allow to put formal wear
				return;

			if (activeChar.isAttackingNow())
				ThreadPool.schedule(() -> {
					final ItemInstance itemToTest = activeChar.getInventory().getItemByObjectId(_objectId);
					if(itemToTest == null)
						return;

					activeChar.useEquippableItem(itemToTest, false);
				}, activeChar.getAttackEndTime() - System.currentTimeMillis());
			else
				activeChar.useEquippableItem(item, true);
		}
		else
		{
			if (activeChar.isCastingNow() && !(item.isPotion() || item.isElixir()))
				return;
			
			if (activeChar.getAttackType() == WeaponType.FISHINGROD && item.getItem().getItemType() == EtcItemType.LURE)
			{
				activeChar.getInventory().setPaperdollItem(Inventory.PAPERDOLL_LHAND, item);
				activeChar.broadcastUserInfo();
				
				sendPacket(new ItemList(activeChar, false));
				return;
			}
			
			final IItemHandler handler = ItemHandler.getInstance().getHandler(item.getEtcItem());
			if (handler != null)
				handler.useItem(activeChar, item, _ctrlPressed);
			
			for (Quest quest : item.getQuestEvents())
			{
				QuestState state = activeChar.getQuestState(quest.getName());
				if (state == null || !state.isStarted())
					continue;
				
				quest.notifyItemUse(item, activeChar, activeChar.getTarget());
			}
		}
	}
}
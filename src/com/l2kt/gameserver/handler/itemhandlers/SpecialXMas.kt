package com.l2kt.gameserver.handler.itemhandlers

import com.l2kt.gameserver.handler.IItemHandler
import com.l2kt.gameserver.model.actor.Playable
import com.l2kt.gameserver.model.actor.instance.Player
import com.l2kt.gameserver.model.item.instance.ItemInstance
import com.l2kt.gameserver.network.serverpackets.ShowXMasSeal

class SpecialXMas : IItemHandler {
    override fun useItem(playable: Playable, item: ItemInstance, forceUse: Boolean) {
        if (playable !is Player)
            return

        playable.sendPacket(ShowXMasSeal(item.itemId))
    }
}
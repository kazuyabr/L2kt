package com.l2kt.gameserver.network.serverpackets

import com.l2kt.gameserver.model.item.instance.ItemInstance

/**
 * Format: (ch)ddd
 */
class ExConfirmCancelItem(item: ItemInstance, private val _price: Int) : L2GameServerPacket() {
    private val _itemObjId: Int = item.objectId
    private val _itemId: Int = item.itemId
    private val _itemAug1: Int = item.getAugmentation()?.getAugmentationId()?.toShort()?.toInt() ?: 0
    private val _itemAug2: Int = (item.getAugmentation()?.getAugmentationId() ?: 0) shr 16

    override fun writeImpl() {
        writeC(0xfe)
        writeH(0x56)
        writeD(_itemObjId)
        writeD(_itemId)
        writeD(_itemAug1)
        writeD(_itemAug2)
        writeQ(_price.toLong())
        writeD(0x01)
    }
}
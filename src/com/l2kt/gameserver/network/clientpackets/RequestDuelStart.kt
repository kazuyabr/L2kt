package com.l2kt.gameserver.network.clientpackets

import com.l2kt.gameserver.model.World
import com.l2kt.gameserver.network.SystemMessageId
import com.l2kt.gameserver.network.serverpackets.ExDuelAskStart
import com.l2kt.gameserver.network.serverpackets.SystemMessage

class RequestDuelStart : L2GameClientPacket() {
    private var _player: String = ""
    private var _partyDuel: Int = 0

    override fun readImpl() {
        _player = readS()
        _partyDuel = readD()
    }

    override fun runImpl() {
        val activeChar = client.activeChar ?: return

        val targetChar = World.getPlayer(_player)
        if (targetChar == null || activeChar == targetChar) {
            activeChar.sendPacket(SystemMessageId.THERE_IS_NO_OPPONENT_TO_RECEIVE_YOUR_CHALLENGE_FOR_A_DUEL)
            return
        }

        // Check if duel is possible.
        if (!activeChar.canDuel()) {
            activeChar.sendPacket(SystemMessageId.YOU_ARE_UNABLE_TO_REQUEST_A_DUEL_AT_THIS_TIME)
            return
        }

        if (!targetChar.canDuel()) {
            activeChar.sendPacket(targetChar.noDuelReason)
            return
        }

        // Players musn't be too far.
        if (!activeChar.isInsideRadius(targetChar, 2000, false, false)) {
            activeChar.sendPacket(
                SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_RECEIVE_A_DUEL_CHALLENGE_BECAUSE_S1_IS_TOO_FAR_AWAY).addCharName(
                    targetChar
                )
            )
            return
        }

        // Duel is a party duel.
        if (_partyDuel == 1) {
            // Player must be a party leader, the target can't be of the same party.
            val activeCharParty = activeChar.party
            if (activeCharParty == null || !activeCharParty.isLeader(activeChar) || activeCharParty.containsPlayer(
                    targetChar
                )
            ) {
                activeChar.sendPacket(SystemMessageId.YOU_ARE_UNABLE_TO_REQUEST_A_DUEL_AT_THIS_TIME)
                return
            }

            // Target must be in a party.
            val targetCharParty = targetChar.party
            if (targetCharParty == null) {
                activeChar.sendPacket(SystemMessageId.SINCE_THE_PERSON_YOU_CHALLENGED_IS_NOT_CURRENTLY_IN_A_PARTY_THEY_CANNOT_DUEL_AGAINST_YOUR_PARTY)
                return
            }

            // Check if every player is ready for a duel.
            for (member in activeCharParty.members) {
                if (member != activeChar && !member.canDuel()) {
                    activeChar.sendPacket(SystemMessageId.YOU_ARE_UNABLE_TO_REQUEST_A_DUEL_AT_THIS_TIME)
                    return
                }
            }

            for (member in targetCharParty.members) {
                if (member != targetChar && !member.canDuel()) {
                    activeChar.sendPacket(SystemMessageId.THE_OPPOSING_PARTY_IS_CURRENTLY_UNABLE_TO_ACCEPT_A_CHALLENGE_TO_A_DUEL)
                    return
                }
            }

            val partyLeader = targetCharParty.leader

            // Send request to targetChar's party leader.
            if (!partyLeader.isProcessingRequest) {
                // Drop command channels, for both requestor && player parties.
                val activeCharChannel = activeCharParty.commandChannel
                activeCharChannel?.removeParty(activeCharParty)

                val targetCharChannel = targetCharParty.commandChannel
                targetCharChannel?.removeParty(targetCharParty)

                // Partymatching
                for (member in activeCharParty.members)
                    member.removeMeFromPartyMatch()

                for (member in targetCharParty.members)
                    member.removeMeFromPartyMatch()

                activeChar.onTransactionRequest(partyLeader)
                partyLeader.sendPacket(ExDuelAskStart(activeChar.name, _partyDuel))

                activeChar.sendPacket(
                    SystemMessage.getSystemMessage(SystemMessageId.S1_PARTY_HAS_BEEN_CHALLENGED_TO_A_DUEL).addCharName(
                        partyLeader
                    )
                )
                targetChar.sendPacket(
                    SystemMessage.getSystemMessage(SystemMessageId.S1_PARTY_HAS_CHALLENGED_YOUR_PARTY_TO_A_DUEL).addCharName(
                        activeChar
                    )
                )
            } else
                activeChar.sendPacket(
                    SystemMessage.getSystemMessage(SystemMessageId.S1_IS_BUSY_TRY_LATER).addCharName(
                        partyLeader
                    )
                )
        } else {
            if (!targetChar.isProcessingRequest) {
                // Partymatching
                activeChar.removeMeFromPartyMatch()
                targetChar.removeMeFromPartyMatch()

                activeChar.onTransactionRequest(targetChar)
                targetChar.sendPacket(ExDuelAskStart(activeChar.name, _partyDuel))

                activeChar.sendPacket(
                    SystemMessage.getSystemMessage(SystemMessageId.S1_HAS_BEEN_CHALLENGED_TO_A_DUEL).addCharName(
                        targetChar
                    )
                )
                targetChar.sendPacket(
                    SystemMessage.getSystemMessage(SystemMessageId.S1_HAS_CHALLENGED_YOU_TO_A_DUEL).addCharName(
                        activeChar
                    )
                )
            } else
                activeChar.sendPacket(
                    SystemMessage.getSystemMessage(SystemMessageId.S1_IS_BUSY_TRY_LATER).addCharName(
                        targetChar
                    )
                )
        }// 1vs1 duel.
    }
}
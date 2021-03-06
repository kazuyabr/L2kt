package com.l2kt.gameserver.scripting.quests

import com.l2kt.gameserver.model.actor.Creature
import com.l2kt.gameserver.model.actor.Npc
import com.l2kt.gameserver.model.actor.instance.Player
import com.l2kt.gameserver.scripting.Quest
import com.l2kt.gameserver.scripting.QuestState

class Q608_SlayTheEnemyCommander : Quest(608, "Slay the enemy commander!") {
    init {

        setItemsIds(HEAD_OF_MOS)

        addStartNpc(31370) // Kadun Zu Ketra
        addTalkId(31370)

        addKillId(25312) // Mos
    }

    override fun onAdvEvent(event: String, npc: Npc?, player: Player?): String? {
        var htmltext = event
        val st = player!!.getQuestState(qn) ?: return htmltext

        if (event.equals("31370-04.htm", ignoreCase = true)) {
            st.state = Quest.STATE_STARTED
            st["cond"] = "1"
            st.playSound(QuestState.SOUND_ACCEPT)
        } else if (event.equals("31370-07.htm", ignoreCase = true)) {
            if (st.hasQuestItems(HEAD_OF_MOS)) {
                st.takeItems(HEAD_OF_MOS, -1)
                st.giveItems(TOTEM_OF_WISDOM, 1)
                st.rewardExpAndSp(10000, 0)
                st.playSound(QuestState.SOUND_FINISH)
                st.exitQuest(true)
            } else {
                htmltext = "31370-06.htm"
                st["cond"] = "1"
                st.playSound(QuestState.SOUND_ACCEPT)
            }
        }

        return htmltext
    }

    override fun onTalk(npc: Npc, player: Player): String? {
        var htmltext = Quest.noQuestMsg
        val st = player.getQuestState(qn) ?: return htmltext

        when (st.state) {
            Quest.STATE_CREATED -> if (player.level >= 75) {
                if (player.allianceWithVarkaKetra >= 4 && st.hasQuestItems(KETRA_ALLIANCE_4) && !st.hasQuestItems(
                        TOTEM_OF_WISDOM
                    )
                )
                    htmltext = "31370-01.htm"
                else
                    htmltext = "31370-02.htm"
            } else
                htmltext = "31370-03.htm"

            Quest.STATE_STARTED -> htmltext = if (st.hasQuestItems(HEAD_OF_MOS)) "31370-05.htm" else "31370-06.htm"
        }

        return htmltext
    }

    override fun onKill(npc: Npc, killer: Creature?): String? {
        val player = killer?.actingPlayer
        if (player != null) {
            for (st in getPartyMembers(player, npc, "cond", "1")) {
                if (st.player.allianceWithVarkaKetra >= 4 && st.hasQuestItems(KETRA_ALLIANCE_4)) {
                    st["cond"] = "2"
                    st.playSound(QuestState.SOUND_MIDDLE)
                    st.giveItems(HEAD_OF_MOS, 1)
                }
            }
        }
        return null
    }

    companion object {
        private val qn = "Q608_SlayTheEnemyCommander"

        // Quest Items
        private val HEAD_OF_MOS = 7236
        private val TOTEM_OF_WISDOM = 7220
        private val KETRA_ALLIANCE_4 = 7214
    }
}
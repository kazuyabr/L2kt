package com.l2kt.gameserver.scripting.quests

import com.l2kt.gameserver.scripting.quests.SagasScripts.SagasSuperClass

class Q091_SagaOfTheArcanaLord : SagasSuperClass(91, "Saga of the Arcana Lord") {
    init {

        NPC = intArrayOf(31605, 31622, 31585, 31608, 31586, 31646, 31647, 31651, 31654, 31655, 31658, 31608)

        Items = intArrayOf(7080, 7604, 7081, 7506, 7289, 7320, 7351, 7382, 7413, 7444, 7110, 0)

        Mob = intArrayOf(27313, 27240, 27310)

        classid = 96
        prevclass = 0x0e

        X = intArrayOf(119518, 181215, 181227)

        Y = intArrayOf(-28658, 36676, 36703)

        Z = intArrayOf(-3811, -4812, -4816)

        registerNPCs()
    }
}
package com.l2kt.gameserver.skills.funcs

import com.l2kt.gameserver.skills.Env
import com.l2kt.gameserver.skills.Formulas
import com.l2kt.gameserver.skills.Stats
import com.l2kt.gameserver.skills.basefuncs.Func

object FuncMaxMpMul : Func(Stats.MAX_MP, 0x20, null, null) {

    override fun calc(env: Env) {
        env.mulValue(Formulas.MEN_BONUS[env.character!!.men])
    }
}
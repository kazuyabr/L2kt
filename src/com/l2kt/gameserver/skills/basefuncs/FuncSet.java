package com.l2kt.gameserver.skills.basefuncs;

import com.l2kt.gameserver.skills.Env;
import com.l2kt.gameserver.skills.Stats;

public class FuncSet extends Func
{
	public FuncSet(Stats pStat, int pOrder, Object owner, Lambda lambda)
	{
		super(pStat, pOrder, owner, lambda);
	}
	
	@Override
	public void calc(Env env)
	{
		if (cond == null || cond.test(env))
			env.setValue(_lambda.calc(env));
	}
}
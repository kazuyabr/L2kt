package com.l2kt.gameserver.taskmanager;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import com.l2kt.Config;
import com.l2kt.commons.concurrent.ThreadPool;
import com.l2kt.gameserver.idfactory.IdFactory;
import com.l2kt.gameserver.model.WorldObject;
import com.l2kt.gameserver.model.actor.Playable;
import com.l2kt.gameserver.model.item.instance.ItemInstance;

/**
 * A task used to delete objects generated by the Config DEBUG_MOVEMENT.
 */
public final class DebugMovementTaskManager implements Runnable
{
	private final Map<ItemInstance, Long> _items = new ConcurrentHashMap<>();
	
	protected DebugMovementTaskManager()
	{
		ThreadPool.scheduleAtFixedRate(this, 1000, 1000);
	}
	
	@Override
	public final void run()
	{
		final long time = System.currentTimeMillis();
		
		for (Entry<ItemInstance, Long> entry : _items.entrySet())
		{
			if (time < entry.getValue())
				continue;
			
			final ItemInstance item = entry.getKey();
			
			item.decayMe();
			
			_items.remove(item);
		}
	}
	
	public final void addItem(WorldObject character, int x, int y, int z)
	{
		final int itemId = (character instanceof Playable) ? 57 : 1831;
		
		final ItemInstance item = new ItemInstance(IdFactory.getInstance().getNextId(), itemId);
		item.setCount(1);
		item.spawnMe(x, y, z + 5);
		
		_items.put(item, System.currentTimeMillis() + Config.DEBUG_MOVEMENT);
	}
	
	public static final DebugMovementTaskManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final DebugMovementTaskManager INSTANCE = new DebugMovementTaskManager();
	}
}
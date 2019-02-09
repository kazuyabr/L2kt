package com.l2kt.gameserver.data.xml;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.l2kt.commons.data.xml.IXmlReader;

import com.l2kt.gameserver.model.actor.instance.Player;
import com.l2kt.gameserver.model.item.Henna;
import com.l2kt.gameserver.templates.StatsSet;

import org.w3c.dom.Document;

/**
 * This class loads and stores {@link Henna}s infos. Hennas are called "dye" ingame.
 */
public class HennaData implements IXmlReader
{
	private final Map<Integer, Henna> _hennas = new HashMap<>();
	
	protected HennaData()
	{
		load();
	}
	
	@Override
	public void load()
	{
		parseFile("./data/xml/hennas.xml");
		LOGGER.info("Loaded {} hennas.", _hennas.size());
	}
	
	@Override
	public void parseDocument(Document doc, Path path)
	{
		forEach(doc, "list", listNode -> forEach(listNode, "henna", hennaNode ->
		{
			final StatsSet set = parseAttributes(hennaNode);
			_hennas.put(set.getInteger("symbolId"), new Henna(set));
		}));
	}
	
	public Henna getHenna(int id)
	{
		return _hennas.get(id);
	}
	
	/**
	 * Retrieve all {@link Henna}s available for a {@link Player} class.
	 * @param player : The Player used as class parameter.
	 * @return a List of all available Hennas for this Player.
	 */
	public List<Henna> getAvailableHennasFor(Player player)
	{
		return _hennas.values().stream().filter(h -> h.canBeUsedBy(player)).collect(Collectors.toList());
	}
	
	public static HennaData getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final HennaData INSTANCE = new HennaData();
	}
}
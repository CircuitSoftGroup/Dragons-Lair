package de.kumpelblase2.npclib.entity;
//original provided by Topcat, modified by kumpelblase2

import java.util.ArrayList;
import java.util.Iterator;
import net.minecraft.server.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerMoveEvent;
import de.kumpelblase2.npclib.NPCManager;
import de.kumpelblase2.npclib.nms.NPCEntity;
import de.kumpelblase2.npclib.pathing.*;

public class NPC
{

	private Entity entity;
	private NPCPathFinder path;
	private Iterator<Node> pathIterator;
	private Node last;
	private NPCPath runningPath;
	private int taskid;
	private Runnable onFail;

	public NPC(Entity entity)
	{
		this.entity = entity;
	}

	public Entity getEntity()
	{
		return entity;
	}

	public void removeFromWorld()
	{
		try
		{
			entity.world.removeEntity(entity);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public org.bukkit.entity.Entity getBukkitEntity()
	{
		return entity.getBukkitEntity();
	}

	public void moveTo(Location l)
	{
		getBukkitEntity().teleport(l);
	}

	public void pathFindTo(Location l, PathReturn callback)
	{
		pathFindTo(l, 3000, callback);
	}

	public void pathFindTo(Location l, int maxIterations, PathReturn callback)
	{
		if (path != null)
			path.cancel = true;
		
		if (l.getWorld() != getBukkitEntity().getWorld())
		{
			ArrayList<Node> pathList = new ArrayList<Node>();
			pathList.add(new Node(l.getBlock()));
			callback.run(new NPCPath(null, pathList, l));
		}
		else
		{
			path = new NPCPathFinder(getBukkitEntity().getLocation(), l, maxIterations, callback);
			path.start();
		}
	}

	public void walkTo(Location l)
	{
		walkTo(l, 3000);
	}

	public void walkTo(final Location l, final int maxIterations)
	{
		pathFindTo(l, maxIterations, new PathReturn()
		{
			@Override
			public void run(NPCPath path)
			{
				usePath(path, new Runnable()
				{
					@Override
					public void run()
					{
						walkTo(l, maxIterations);
					}
				});
			}
		});
	}

	public void usePath(NPCPath path)
	{
		usePath(path, new Runnable()
		{
			@Override
			public void run()
			{
				walkTo(runningPath.getEnd(), 3000);
			}
		});
	}

	public void usePath(NPCPath path, Runnable onFail)
	{
		if (taskid == 0)
		{
			taskid = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(NPCManager.plugin, new Runnable()
			{
				@Override
				public void run()
				{
					pathStep();
				}
			}, 6L, 6L);
		}
		pathIterator = path.getPath().iterator();
		runningPath = path;
		this.onFail = onFail;
	}

	private void pathStep()
	{
		if (pathIterator.hasNext())
		{
			Node n = pathIterator.next();
			if (n.b.getWorld() != getBukkitEntity().getWorld())
			{
				getBukkitEntity().teleport(n.b.getLocation());
			}
			else
			{
				float angle = getEntity().yaw;
				float look = getEntity().pitch;
				if (last == null || runningPath.checkPath(n, last, true))
				{
					if (last != null)
					{
						angle = (float) Math.toDegrees(Math.atan2(last.b.getX() - n.b.getX(), n.b.getZ() - last.b.getZ()));
						look = (float) (Math.toDegrees(Math.asin(last.b.getY() - n.b.getY())) / 2);
					}
					PlayerMoveEvent event = new PlayerMoveEvent(((NPCEntity)getEntity()).getBukkitEntity(), getEntity().getBukkitEntity().getLocation(), n.b.getLocation());
					Bukkit.getPluginManager().callEvent(event);
					if(event.isCancelled())
					{
						this.path.cancel = true;
						return;
					}
					getEntity().setPositionRotation(n.b.getX() + 0.5, n.b.getY(), n.b.getZ() + 0.5, angle, look);
					((EntityPlayer)getEntity()).as = angle;
				}
				else
				{
					onFail.run();
				}
			}
			last = n;
		}
		else
		{
			getEntity().setPositionRotation(runningPath.getEnd().getX(), runningPath.getEnd().getY(), runningPath.getEnd().getZ(), runningPath.getEnd().getYaw(), runningPath.getEnd().getPitch());
			((EntityPlayer)getEntity()).as = runningPath.getEnd().getYaw();
			Bukkit.getServer().getScheduler().cancelTask(taskid);
			taskid = 0;
		}
	}

	public void stopWalking()
	{
		if(this.path != null)
		{
			this.path.cancel = true;
			Bukkit.getServer().getScheduler().cancelTask(taskid);
			taskid = 0;
		}
	}

	public boolean isWalking()
	{
		return (this.pathIterator != null && this.pathIterator.hasNext()) && this.path != null && !this.path.cancel;
	}

}
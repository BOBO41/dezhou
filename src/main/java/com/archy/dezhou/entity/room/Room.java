package com.archy.dezhou.entity.room;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.alibaba.fastjson.JSONObject;
import com.archy.dezhou.entity.User;
import com.archy.dezhou.global.ConstList;
import com.archy.dezhou.container.ActionscriptObject;
import com.archy.dezhou.entity.Player;
import com.archy.dezhou.entity.Puke;
import org.apache.log4j.Logger;

import com.archy.dezhou.util.HeartTimer;
import com.archy.dezhou.util.Utils;

public class Room
{

	private Logger log = Logger.getLogger(getClass());

    private int roomid;
    private String name;

	public String creator;
	private PukerGame pokerGame = null;
	//进入房间，但是尚未坐下的玩家
	private Set<Player> spectatorList = Collections.synchronizedSet( new HashSet<Player>() );
	private boolean isLimbo;
	private String zone;

	public boolean isPlayerInRoom(Player user)
	{
		return this.playerMap.containsValue(user);
	}


	public int getMaxSpectator()
	{
		return 100000;
	}
	
	public void addPlayer(int seatId,Player player)
	{
		this.playerMap.put(seatId,player);
	}
	
	public void removePlayer(int seatId)
	{
		this.playerMap.remove(seatId);
	}
	
	public ActionscriptObject playerSitDown(int seatId, Player player, int cb)
	{
		ActionscriptObject response = new ActionscriptObject();
		ActionscriptObject userAobj = new ActionscriptObject();
		if(player == null)
		{
			return null;
		}

		if(this.playerMap.containsKey(seatId))
		{
			response.put("_cmd", ConstList.CMD_SITDOWN);
			userAobj.put("uid", player.getUid());
			response.put("user", userAobj);
			response.put("issit", "yes");
			response.put("sid", seatId);
			response.put("info", "HaveAlreadySitDowned");
			log.info("roomName: " + this.getName() +   " 此位置已经有人 seat: " + seatId);
			return response;
		}

		if(this.playerMap.values().contains(player))
		{
			response.put("_cmd", ConstList.CMD_SITDOWN);
			response.put("roomKey", this.getName());
			response.put("issit", "yes");
			response.put("info", "YouHaveSitedAtOtherPlace");
			log.info("roomName: " + this.getName() +   " 此玩家已经坐下  uid: " + player.getUid());
			return response;
		}


		log.info("roomName: " + this.getName() + "  " + player.getUid() + " try to sitdown at seatId: " + seatId);
		this.spectatorList.remove(player);

		player.clearRoomMoney();
		player.addRmoney(cb);
		player.deductAmoney(cb);

		this.addPlayer(seatId,player);

		if(this.isGame())
		{
			player.setGameState(ConstList.PlayerGameState.PLAYER_STATE_WAIT);
			player.setPlayerState(ConstList.PlayerCareerState.PLAYER_STATE_WAIT);
		}
		else
		{
			player.setGameState(ConstList.PlayerGameState.PLAYER_STATE_PLAYER);
			player.setPlayerState(ConstList.PlayerCareerState.PLAYER_STATE_PLAYER);
		}


		userAobj.put("uid",player.getUid());
		userAobj.putNumber("sid", seatId);
		userAobj.putNumber("cm", player.getRmoney());
		userAobj.put("tm", String.valueOf(player.getAMoney()));
		userAobj.put("un", player.getName());
		userAobj.put("pic", player.getPic());
		userAobj.putNumber("ps", player.getPlayerState().value());
		userAobj.putNumber("gs", player.getGameState().value());
		userAobj.putNumber("lev", Integer.parseInt(Utils.retLevel(player.getExprience())));
		userAobj.putNumber("yt", 0);
		userAobj.put("big", "");
		userAobj.put("spr", "");

		response.put("_cmd",ConstList.CMD_SITDOWN);
		response.put("user", userAobj);
		response.put("issit", "no");

		this.addPlayer(seatId,player);
		this.notifyRoomPlayerButOne(response, ConstList.MessageType.MESSAGE_NINE,player.getUid());
		return response;
	}
	
	public ActionscriptObject playerStandUp(Integer uid, boolean notifyMySelf)
	{

		log.info("roomName: " + this.getName() + " try standup users: "  + uid);

		int seatId = 0;
		Player player = null;
		for(Map.Entry<Integer, Player> entry : this.playerMap.entrySet())
		{
			if(entry.getValue().getUid().intValue() == uid)
			{
				seatId = entry.getKey();
                player = entry.getValue();
				log.info("roomName: " + this.getName() + "  standup users: "  + uid + " ok! ");
				break;
			}
		}

		if(player == null)
		{
			return null;
		}

		this.spectatorList.add(player);

		log.warn("roomName: " + this.getName() + "  " + uid + " standup at seatId: " + seatId);

		player.setGameState(ConstList.PlayerGameState.GAME_STATE_STANDUP);

		this.playerMap.remove(seatId);
		boolean isp = this.pokerGame.isUserPlaying(player.getUid());
		this.pokerGame.playerStandup(player.getSeatId());


		ActionscriptObject response = new ActionscriptObject();
		response.put("_cmd",ConstList.CMD_STANDUP);

		ActionscriptObject playerAs = new ActionscriptObject();
		playerAs.putNumber("sid",seatId);
		playerAs.put("un",player.getName());
		playerAs.putNumber("yt",player.getYourTurn());
		playerAs.put("uid",player.getUid());
		playerAs.put("uid",player.getUid());
		playerAs.putNumber("ps",player.getPlayerState().value());
		playerAs.putNumber("gs",player.getGameState().value());
		playerAs.putBool("isp",isp);
		playerAs.putBool("ip",true);
		playerAs.putNumber("tb",player.getTempBet());
		response.put("user",playerAs);

		if(notifyMySelf)
		{
			this.notifyRoomPlayer(response, ConstList.MessageType.MESSAGE_NINE);
		}
		else
		{
			this.notifyRoomPlayerButOne(response, ConstList.MessageType.MESSAGE_NINE, player.getUid());
		}
		return response;
	}
	
	private class RoomStateFight implements IRoomState
	{
		
		public RoomStateFight()
		{
			timer = new HeartTimer(1*1000);
		}
		
		@Override
		public void beatHear(long now)
		{
			if( this.timer != null && this.timer.Check(now))
			{
				pokerGame.beatHeart(now);
				this.timer.setNextTick();
			}
		}

		@Override
		public boolean isGame()
		{
			return true;
		}
		
		private HeartTimer timer = null;
		
	}
	
	private volatile IRoomState roomState = new RoomStateReady();

	public int userJoin(Player u)
	{

		if (!this.spectatorList.contains(u))
		{
			log.info("roomName: " + this.getName() + "  " + u.getUid() + " enter room as spectator");
			this.spectatorList.add(u);
			return 0;
		}
		else
		{
			log.info("roomName: " + this.getName() + "  " + u.getUid() + "已经进入过该房间");
			return -2;// 已经进入过房间
		}
	}

	public int playerLeave(Player user)
	{
		this.forceRemoveUser(user);
		return 0;
	}

	public int getRoomId()
	{
		return roomid;
	}

	public String getZone()
	{
		return zone;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}


	public boolean isTemp()
	{
		return false;
	}
	
	public int getMaxUsers()
	{
		return ConstList.MAXNUMPLAYEROFROOM;
	}

	public void beatHeart(long now)
	{
		now = System.currentTimeMillis();
		List<Player> users = new ArrayList<Player>();
		users.addAll(this.playerMap.values());
		for(Player user : users)
		{
			if(user.isStandUpExpired(now))
			{
				log.warn("roomName: " + this.getName() + " at time: " + System.currentTimeMillis() + " user " + user.getUid() + " standUp expired");
				this.playerStandUp(user.getUid(),true);
			}
		}

		users.clear();
		users.addAll(this.spectatorList);
		for(Player user : users)
		{
			if(user.isLeaveExpired(now))
			{
				if(this.isPlayerSitDown(user.getUid()))
				{
					continue;
				}
				log.warn("roomName: " + this.getName() + " at time: " + System.currentTimeMillis() + " user " + user.getUid() + " leave room expired");
				this.playerLeave(user);
			}
		}

		this.roomState.beatHear(now);
	}

	public int getSpectatorCount()
	{
		return this.spectatorList.size();
	}
	
	public boolean isPlayerSitDown(Integer uId)
	{

		for(Map.Entry<Integer,Player> entry : this.playerMap.entrySet())
		{
			if(entry.getValue().getUid().equals(uId))
			{
				return true;
			}
		}
		return false;
	}

	public Player[] getAllUsers()
	{
		return this.playerMap.values().toArray(new Player[this.playerMap.size()]);
	}

	public int getUserCount()
	{
		return this.playerMap.size();
	}

	public int howManySpecators()
	{
		return this.spectatorList.size();
	}

	public Player findPlayerByUser(User u)
	{
		for(Map.Entry<Integer, Player> entry : this.playerMap.entrySet())
		{
			if(entry.getValue().getUid().equals(u.getUid()))
			{
				return entry.getValue();
			}
		}
		return null;
	}

	public String getCreator()
	{
		return creator;
	}
	private Integer minbuy;
	private Integer maxbuy;

	private void setRoomID()
	{
		roomid = autoId.getAndIncrement();
	}

	public static void resetRoomStaticData()
	{
		autoId = new AtomicInteger(0);
	}

	public boolean isLimbo()
	{
		return isLimbo;
	}

	public void setLimbo(boolean isLimbo)
	{
		this.isLimbo = isLimbo;
	}


	public boolean isGame()
	{
		return this.roomState.isGame();
	}

	public boolean forceRemoveUser(User u)
	{
		if(this.isPlayerSitDown(u.getUid()))
		{
			this.playerStandUp(u.getUid(),true);
		}

		this.spectatorList.remove(u);
		return true;
	}

	public void gameOverHandle()
	{
		this.roomState = new RoomStateReady(7);
	}

	public int getSecsPassByTurn()
	{
		if(this.pokerGame == null)
		{
			return 0;
		}
		return this.pokerGame.getSecsPassByTurn();
	}
	private Integer bbet;
	private Integer sbet;
	private String showname;

	/**
	 *
	 *
	 **/
	public Room(String name ,String zone,String creator)
	{
		setRoomID();

		this.name = name;
		this.zone = zone;
		this.creator = creator;


		this.pokerGame = new PukerGame(this);
	}

	public Room(JSONObject obj)
    {
        setRoomID();

        this.name = obj.getString("name");
        this.creator = "admin";
        this.bbet = obj.getIntValue("bbet");
        this.sbet = obj.getIntValue("sbet");
        this.minbuy = obj.getIntValue("mixbuy");
        this.maxbuy = obj.getIntValue("maxbuy");
        this.showname = obj.getString("showname");

        this.pokerGame = new PukerGame(this);
    }

	public int howManyUsers()
	{
		return this.playerMap.size();
	}

	public boolean isUserInSeat(Player player)
	{
		return this.playerMap.values().contains(player);
	}

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public Integer getMinbuy() {
        return minbuy;
    }

    public void setMinbuy(Integer minbuy) {
        this.minbuy = minbuy;
    }

    public Integer getMaxbuy() {
        return maxbuy;
    }

    public void setMaxbuy(Integer maxbuy) {
        this.maxbuy = maxbuy;
    }
	
	public Map<Integer,Player> userListToPlayerMap()
	{
		Map<Integer,Player> map = new HashMap<Integer,Player>();
		map.putAll(this.playerMap);
		return map;
	}
	
	private static AtomicInteger autoId = new AtomicInteger(0);
	
	private Map<Integer,Player> playerMap = new Hashtable<Integer,Player>();
	
	public PukerGame getPokerGame()
	{
		return this.pokerGame;
	}

	public void notifyRoomPlayerButOne(ActionscriptObject aObj, ConstList.MessageType msgType, Integer uId)
	{
		long timeStamp = System.currentTimeMillis();
		Set<User> users = new HashSet<User>();

		synchronized (this.playerMap)
		{
			users.addAll(this.playerMap.values());
		}
		synchronized (this.spectatorList)
		{
			users.addAll(this.spectatorList);
		}

		for(User user : users)
		{
			if(user.getUid().equals(uId))
			{
				continue;
			}
			// TODO 通知其他用户，我做了什么

		}
	}

	public void notifyRoomPlayer(ActionscriptObject aObj, ConstList.MessageType msgType)
	{
		long timeStamp = System.currentTimeMillis();
		Set<User> users = new HashSet<User>();

		synchronized (this.playerMap)
		{
			users.addAll(this.playerMap.values());
		}
		synchronized (this.spectatorList)
		{
			users.addAll(this.spectatorList);
		}

		//通知房间其他用户
	}

	public ActionscriptObject toAsObj()
	{
		ActionscriptObject response = new ActionscriptObject();

		response.put("_cmd",ConstList.CMD_ROOMINFO);
		response.put("ig", this.isGame()?"yes":"no");
		response.putNumber("turn",this.pokerGame.getTurn());
		response.putNumber("round",this.pokerGame.getRound());

		response.putNumber("mbet",this.getBbet());
		response.putNumber("sbet",this.getSbet());
		response.putNumber("msid",this.pokerGame.maxSeatId());
		response.putNumber("bsid",this.pokerGame.getBankerSeatId());
		response.putNumber("wt",this.pokerGame.getNextSeatId());

		response.putNumber("minbuy",this.getMinbuy());
		response.putNumber("maxbuy",this.getMaxbuy());

		response.put("chname",this.getShowname());
		response.put("roomName",this.getName());
		response.putNumber("fpb",this.pokerGame.getPoolBet(1));
		response.putNumber("spb",this.pokerGame.getPoolBet(2));
		response.putNumber("tpb",this.pokerGame.getPoolBet(3));
		response.putNumber("fopb",this.pokerGame.getPoolBet(4));

		if(this.isGame())
		{
			response.put("fpk",this.pokerGame.fiveSharePkToAsob());
		}

		ActionscriptObject as_plist = new ActionscriptObject();
		for(Map.Entry<Integer, Player> entry : this.playerMap.entrySet())
		{
			ActionscriptObject as_player = new ActionscriptObject();
			Player player = entry.getValue();

			ActionscriptObject dj_func = new ActionscriptObject();

			as_player.put("dj_func", dj_func);

			as_player.put("un",player.getName());
			as_player.putNumber("lev",Utils.retLevelAndExp(player.getExprience())[0]);
			as_player.putNumber("sid",entry.getKey());
			as_player.put("uid",player.getUid());

			as_player.putNumber("pkl",player.getPkLevel());
			as_player.put("pic",player.getPic());
			as_player.putBool("isp",player.isPlaying());
			as_player.putNumber("tb",player.getTempBet());
			as_player.putNumber("yt",player.getYourTurn());

			as_player.putNumber("cm",player.getRmoney());
			as_player.putNumber("tm",player.getAMoney());
			as_player.putNumber("gs",player.getGameState().value());
			as_player.putNumber("ps",player.getPlayerState().value());

			as_player.putNumber("frb", player.getFirstRoundBet());
			as_player.putNumber("srb", player.getSecondRoundBet());
			as_player.putNumber("trb", player.getThirdRoundBet());
			as_player.putNumber("ftrb", player.getFourthRoundBet());

			if(this.isGame())
			{
				Puke p = player.getPuke(5);
				if(p != null)
				{
					as_player.put("pk1",p.toAobj());
				}

				Puke p2 = player.getPuke(6);
				if(p2 != null)
				{
					as_player.put("pk2",p2.toAobj());
				}

			}
			as_plist.put("sid" + entry.getKey(),as_player);
		}

		response.putNumber("bsid",this.pokerGame.getBankerSeatId());
		response.put("plist",as_plist);

		return response;
	}
	
    public Integer getBbet() {
        return bbet;
    }
	
    public void setBbet(Integer bbet) {
        this.bbet = bbet;
    }

    public Integer getSbet() {
        return sbet;
    }

    public void setSbet(Integer sbet) {
        this.sbet = sbet;
    }

    public String getShowname() {
        return showname;
    }

    public void setShowname(String showname) {
        this.showname = showname;
    }

	private class RoomStateReady implements IRoomState
	{

		private HeartTimer timer = null;

		public RoomStateReady()
		{
			timer = new HeartTimer(5*1000);
		}

		public RoomStateReady(int secs)
		{
			timer = new HeartTimer( secs * 1000 );
		}

		@Override
		public void beatHear(long now)
		{
			if( this.timer != null && this.timer.Check(now))
			{
				if(playerMap.size() >= 2 && playerMap.size() >= 2)
				{
					this.timer = null;
					roomState = new RoomStateFight();
					try
					{
						pokerGame.gameStartHandle();
					}
					catch (Exception e)
					{
						timer = new HeartTimer(3*1000);
						this.timer.setNextTick();
						log.error("roomName: " +  getName() + " start game error",e);
					}
				}
				else
				{
					this.timer.setNextTick();
				}
			}
		}

		@Override
		public boolean isGame()
		{
			return false;
		}

	}


	
}

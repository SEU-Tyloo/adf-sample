package Tyloo.module.complex.self;

import java.util.*;

import Tyloo.module.algorithm.AStarPathPlanning;
import adf.agent.precompute.PrecomputeData;
import adf.debug.TestLogger;
import org.apache.log4j.Logger;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.agent.communication.standard.bundle.information.MessageFireBrigade;
import adf.agent.communication.standard.bundle.information.MessagePoliceForce;
import adf.agent.communication.standard.bundle.information.MessageRoad;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.communication.CommunicationMessage;
import adf.component.module.complex.RoadDetector;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;
import adf.agent.communication.standard.bundle.centralized.CommandPolice;
import rescuecore2.standard.entities.*;

import static rescuecore2.standard.entities.StandardEntityURN.*;

@SuppressWarnings("unchecked")
public class TestRoadDetector extends RoadDetector {

	int count=0;
	private Logger logger;

	private Set<EntityID> targetAreas; //目标道路，是所有堵塞的区域
	private Set<EntityID> priorityRoads; //道路优先级

	private AStarPathPlanning pathPlanning;

	private EntityID result;

	public TestRoadDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData)
	{
		super(ai, wi, si, moduleManager, developData);
		logger = TestLogger.getLogger(agentInfo.me());
		this.pathPlanning = moduleManager.getModule("TestRoadDetector.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
		registerModule(this.pathPlanning);
		this.result = null;
	}


	@Override
	public RoadDetector calc()
	{

		//在目标区域中，直接清除目标
		//如果不在目标区域中，先更新优先级道路
		//如果优先级道路不为空，优先清理优先级道路
		//如果优先级道路为空，则直接开始清除目标区域

		EntityID positionID = this.agentInfo.getPosition();//获取当前位置
		int MyHP=((Human)this.agentInfo.me()).getHP();//获取当前血量
		if(MyHP <= 2000)
		{
			for (StandardEntity e : this.worldInfo.getEntitiesOfType(REFUGE))
			{
				EntityID id=e.getID();
				Set<EntityID> RefugeLocation=new HashSet<>();
				RefugeLocation.add(id);
				this.pathPlanning.setFrom(positionID);
				this.pathPlanning.setDestination(RefugeLocation);
				List<EntityID> path = this.pathPlanning.calc().getResult();
				if (path != null && path.size() > 0)
				{
					this.result = path.get(path.size() - 1);
				}
				return this;
			}
		}//
		if(MyHP <= 5000 )
		{
			this.result=null;
			return this;
		}

		logger.debug("Thecount="+this.count);

		if(this.result!= null)
			this.count++;
		if(count == 7 ){//如果在这个地方停留7次，则强制更换工作地方
			this.count=0;
			targetAreas.remove(positionID);
			this.pathPlanning.setFrom(positionID);
			this.pathPlanning.setDestination(this.targetAreas);
			List<EntityID> path = this.pathPlanning.calc().getResult();
			if (path != null && path.size() > 0)
			{
				logger.debug("0.Select the path:"+path);
				this.result = path.get(path.size() - 1);
			}
			logger.debug("0.Selected Target: " + this.result);
			return this;
		}
		if (this.result == null)
		{
			this.count=0;
			logger.debug("NowPosition:"+positionID);

			if (this.targetAreas.contains(positionID))
			{
				this.result = positionID;
				logger.debug("1.Selected Target: " + this.result);
				return this;
			}//如果警察在目标区域内直接结束即可

			//将优先级队列中没有堵塞的道路清除
			List<EntityID> removeList = new ArrayList<>(this.priorityRoads.size()); //remove队列的长度等于优先级队列的长度
			for (EntityID id : this.priorityRoads)
			{
				if (!this.targetAreas.contains(id))
				{
					removeList.add(id);
				}
			}//如果该优先级的道路在目标区域中，加入到remove队列中

			this.priorityRoads.removeAll(removeList);//清除PriorityRoads列表中在removeList包含的全部元素
			//即删除在目标区域的道路，即代表该路上的路障已经被清除

			//如果优先级道路还存在

			logger.debug("***priorityRoads"+this.priorityRoads);
			logger.debug("***targetAreas"+this.targetAreas);



			if (this.priorityRoads.size() > 0)
			{
				//排序

				logger.debug("1.***priorityRoads"+this.priorityRoads);
				logger.debug("1.***targetAreas"+this.targetAreas);

				this.pathPlanning.setFrom(positionID);//当前位置
				this.pathPlanning.setDestination(this.priorityRoads);//目标区域

				List<EntityID> path = this.pathPlanning.calc().getResult();
				if (path != null && path.size() > 0)
				{
					logger.debug("2.1 Select the path:"+path);
					this.result = path.get(path.size() - 1);
				}
				else{
					this.pathPlanning.setFrom(positionID);
					this.pathPlanning.setDestination(this.targetAreas);
					path = this.pathPlanning.calc().getResult();
					if (path != null && path.size() > 0)
					{
						logger.debug("2.2 Select the path:"+path);
						this.result = path.get(path.size() - 1);
					}
				}
				logger.debug("2.Selected Target: " + this.result);
				return this;
			}//找路

			logger.debug("2.***priorityRoads"+this.priorityRoads);
			logger.debug("2.***targetAreas"+this.targetAreas);
			this.pathPlanning.setFrom(positionID);
			this.pathPlanning.setDestination(this.targetAreas);
			List<EntityID> path = this.pathPlanning.calc().getResult();
			if (path != null && path.size() > 0)
			{
				logger.debug("3.Select the path:"+path);
				this.result = path.get(path.size() - 1);
			}
		}
		logger.debug("3.Selected Target: " + this.result);
		return this;
	}

	@Override
	public EntityID getTarget()
	{
		return this.result;
	}

	@Override
	public RoadDetector precompute(PrecomputeData precomputeData)
	{
		super.precompute(precomputeData);
		if (this.getCountPrecompute() >= 2)
		{
			return this;
		}
		return this;
	}

	@Override
	public RoadDetector updateInfo(MessageManager messageManager) {

		this.targetAreas = new HashSet<>();
		for (StandardEntity e : this.worldInfo.getEntitiesOfType(REFUGE,GAS_STATION,BUILDING,HYDRANT)) {
			for (EntityID id : ((Building) e).getNeighbours()) {
				StandardEntity neighbour = this.worldInfo.getEntity(id);
				if (neighbour instanceof Road) {
					if (((Road) neighbour).isBlockadesDefined() && !((Road) neighbour).getBlockades().isEmpty())
						//如果该地区Block被定义，且该Block不为空，加入到目标区域
						this.targetAreas.add(id);
				}
			}
		}//目标队列
		this.priorityRoads = new HashSet<>();
		for (StandardEntity e : this.worldInfo.getEntitiesOfType(REFUGE,GAS_STATION)) {
			for (EntityID id : ((Building) e).getNeighbours()) {
				StandardEntity neighbour = this.worldInfo.getEntity(id);
				if (neighbour instanceof Road) {
					if (((Road) neighbour).isBlockadesDefined() && !((Road) neighbour).getBlockades().isEmpty())
						//如果该地区Block被定义，且该Block不为空，加入到优先级道路队列
						this.priorityRoads.add(id);
				}
			}
		}//优先级队列


		logger.debug("TheTime:"+agentInfo.getTime());
		super.updateInfo(messageManager);
		if (this.getCountUpdateInfo() >= 2)
		{
			return this;
		}
		if (this.result != null)
		{


			if (this.agentInfo.getPosition().equals(this.result))
			{
				logger.debug("我刚刚到达目的地！");
				StandardEntity entity = this.worldInfo.getEntity(this.result);
				if (entity instanceof Building)
				{
					this.result = null;
				}
				else if (entity instanceof Road)
				{
					Road road = (Road) entity;
					if (!road.isBlockadesDefined() || road.getBlockades().isEmpty())
					{
						this.targetAreas.remove(this.result);
						this.result = null;
					}//如果没有阻塞，将该区域从targetAreas中删除
				}
			}
			else
			{
				logger.debug("我还没到达目的地！");
			}
		} //到达目的地之后，重新处理targets




		Set<EntityID> changedEntities = this.worldInfo.getChanged().getChangedEntities();
		for (CommunicationMessage message : messageManager.getReceivedMessageList())//遍历获得的消息
		{
			Class<? extends CommunicationMessage> messageClass = message.getClass();
			if (messageClass == MessageAmbulanceTeam.class)
			{
				this.reflectMessage((MessageAmbulanceTeam) message);
				logger.debug("1.---priorityRoads"+priorityRoads);
				logger.debug("1.---targetAreas"+targetAreas);
			}//呼应救援队伍
			else if (messageClass == MessageFireBrigade.class)
			{
				this.reflectMessage((MessageFireBrigade) message);
				logger.debug("2.---priorityRoads"+priorityRoads);
				logger.debug("2.---targetAreas"+targetAreas);
			}//呼应救火队伍
			else if (messageClass == MessageRoad.class)
			{
				this.reflectMessage((MessageRoad) message, changedEntities);
				logger.debug("3.---priorityRoads"+priorityRoads);
				logger.debug("3.---targetAreas"+targetAreas);
			}//呼应道路信息
			else if (messageClass == MessagePoliceForce.class)
			{
				this.reflectMessage((MessagePoliceForce) message);
				logger.debug("4.---priorityRoads"+priorityRoads);
				logger.debug("4.---targetAreas"+targetAreas);
			}//呼应其他警察信息
			else if (messageClass == CommandPolice.class)
			{
				this.reflectMessage((CommandPolice) message);
				logger.debug("5.---priorityRoads"+priorityRoads);
				logger.debug("5.---targetAreas"+targetAreas);
			}
		}
//		for (EntityID id : this.worldInfo.getChanged().getChangedEntities())
//		{
//			StandardEntity entity = this.worldInfo.getEntity(id);
//			if (entity instanceof Road)
//			{
//				Road road = (Road) entity;
//				if (!road.isBlockadesDefined() || road.getBlockades().isEmpty())
//				{
//					this.targetAreas.remove(id);
//				}
//			}
//		}
		logger.debug("6.---priorityRoads"+priorityRoads);
		logger.debug("6.---targetAreas"+targetAreas);
		return this;
	}//主要更新已被清理过的区域块

	private void reflectMessage(MessageRoad messageRoad, Collection<EntityID> changedEntities)
	{
		if (messageRoad.isBlockadeDefined() && !changedEntities.contains(messageRoad.getBlockadeID()))
		{
			MessageUtil.reflectMessage(this.worldInfo, messageRoad);
		}
		if (messageRoad.isPassable())
		{
			this.targetAreas.remove(messageRoad.getRoadID());
		}//如果能通行。将该地区移除
	}

	private void reflectMessage(MessageAmbulanceTeam messageAmbulanceTeam)
	{
		if (messageAmbulanceTeam.getPosition() == null)
		{
			return;
		}
		if (messageAmbulanceTeam.getAction() == MessageAmbulanceTeam.ACTION_RESCUE)//救援中
		{
			StandardEntity position = this.worldInfo.getEntity(messageAmbulanceTeam.getPosition());//医生的位置
			if (position != null && position instanceof Building)
			{
				this.targetAreas.removeAll(((Building) position).getNeighbours());
			}
		}
		else if (messageAmbulanceTeam.getAction() == MessageAmbulanceTeam.ACTION_LOAD)
		{
			StandardEntity position = this.worldInfo.getEntity(messageAmbulanceTeam.getPosition());
			if (position != null && position instanceof Building)
			{
				this.targetAreas.removeAll(((Building) position).getNeighbours());
			}
		}
		else if (messageAmbulanceTeam.getAction() == MessageAmbulanceTeam.ACTION_MOVE)//移动
		{
			if (messageAmbulanceTeam.getTargetID() == null)
			{
				return;
			}
			StandardEntity target = this.worldInfo.getEntity(messageAmbulanceTeam.getTargetID());//获取医生的目标
			if (target instanceof Building)//如果医生的目标是建筑物
			{
				for (EntityID id : ((Building) target).getNeighbours())
				{
					StandardEntity neighbour = this.worldInfo.getEntity(id);
					if (neighbour instanceof Road)
					{
						if(((Road) neighbour).isBlockadesDefined() || !((Road) neighbour).getBlockades().isEmpty())
						{
							this.priorityRoads.add(id);
							this.targetAreas.add(id);
						}

					}//如果道路阻塞，或者道路阻塞不为空，加入到优先级队列中
				}
			}//帮助医生清理道路，将医生阻塞的道路加入到优先级队列中
			else if (target instanceof Human)
			{
				Human human = (Human) target;
				if (human.isPositionDefined())
				{
					StandardEntity position = this.worldInfo.getPosition(human);
					if (position instanceof Building)
					{
						for (EntityID id : ((Building) position).getNeighbours())
						{
							StandardEntity neighbour = this.worldInfo.getEntity(id);
							if (neighbour instanceof Road)
							{
								if(((Road) neighbour).isBlockadesDefined() || !((Road) neighbour).getBlockades().isEmpty())
								{
									this.priorityRoads.add(id);
									this.targetAreas.add(id);
								}
							}//如果被困人员道路被阻塞，清理道路
						}
					}
				}
			}
		}
	}

	private void reflectMessage(MessageFireBrigade messageFireBrigade)//消防队
	{
		if (messageFireBrigade.getTargetID() == null)
		{
			return;
		}
		if (messageFireBrigade.getAction() == MessageFireBrigade.ACTION_EXTINGUISH)//正在扑灭火
		{
			StandardEntity position = this.worldInfo.getEntity(messageFireBrigade.getPosition());//消防员的位置
			if (position != null && position instanceof Building)
			{
				this.targetAreas.removeAll(((Building) position).getNeighbours());
			}
		}
		if(messageFireBrigade.getAction() == MessageFireBrigade.ACTION_MOVE)
		{
			if (messageFireBrigade.getTargetID() == null)
			{
				return;
			}
			StandardEntity target = this.worldInfo.getEntity(messageFireBrigade.getTargetID());//获取消防队的目标
			logger.debug("FireBrige Target"+target);
			if (target instanceof Building)//如果消防队的目标是建筑物
			{
				for (EntityID id : ((Building) target).getNeighbours())
				{
					StandardEntity neighbour = this.worldInfo.getEntity(id);
					if (neighbour instanceof Road)
					{
						if(((Road) neighbour).isBlockadesDefined() || !((Road) neighbour).getBlockades().isEmpty())
						{
							this.priorityRoads.add(id);
							this.targetAreas.add(id);
						}

					}//如果道路阻塞，或者道路阻塞不为空，加入到优先级队列中
				}
			}//帮助消防员清理道路，将医生阻塞的道路加入到优先级队列中
		}
		if (messageFireBrigade.getAction() == MessageFireBrigade.ACTION_REFILL)//如果消防员在加水
		{
			StandardEntity target = this.worldInfo.getEntity(messageFireBrigade.getTargetID());
			if (target instanceof Building)
			{
				for (EntityID id : ((Building) target).getNeighbours())
				{
					StandardEntity neighbour = this.worldInfo.getEntity(id);
					if (neighbour instanceof Road)
					{
						if(((Road) neighbour).isBlockadesDefined() || !((Road) neighbour).getBlockades().isEmpty())
						{
							this.priorityRoads.add(id);
							this.targetAreas.add(id);
						}
					}
				}
			}//如果在避难所加水，清空道路
			else if (target.getStandardURN() == HYDRANT)//如果是消防栓
			{
				this.priorityRoads.add(target.getID());
				this.targetAreas.add(target.getID());
			}
		}
	}

	private void reflectMessage(MessagePoliceForce messagePoliceForce)
	{
		if (messagePoliceForce.getAction() == MessagePoliceForce.ACTION_CLEAR)//如果是在进行清理
		{
			if (messagePoliceForce.getAgentID().getValue() != this.agentInfo.getID().getValue())
			{//响应其他警察的消息
				if (messagePoliceForce.isTargetDefined())//目标如果定义
				{
					EntityID targetID = messagePoliceForce.getTargetID();//获取通信警察的目标消息
					if (targetID == null)
					{
						return;
					}
					StandardEntity entity = this.worldInfo.getEntity(targetID);
					if (entity == null)
					{
						return;
					}

					if (entity instanceof Area)
					{
						this.targetAreas.remove(targetID);//如果该地区为一个警察清理，那么他自己不再清理，确保两两警察之间的目标不一样
						if (this.result != null && this.result.getValue() == targetID.getValue())
						{
							if (this.agentInfo.getID().getValue() < messagePoliceForce.getAgentID().getValue())
							{
								this.result = null;
							}
						}
						logger.debug("1.该目标已经被分配出去！");
					}
					else if (entity.getStandardURN() == BLOCKADE)//如果封锁
					{
						EntityID position = ((Blockade) entity).getPosition();
						this.targetAreas.remove(position);
						if (this.result != null && this.result.getValue() == position.getValue())
						{
							if (this.agentInfo.getID().getValue() < messagePoliceForce.getAgentID().getValue())
							{
								this.result = null;
							}
						}
						logger.debug("2.该目标已经被分配出去！");
					}

				}
			}
		}
	}

	private void reflectMessage(CommandPolice commandPolice)
	{
		boolean flag = false;
		if (commandPolice.isToIDDefined() && this.agentInfo.getID().getValue() == commandPolice.getToID().getValue())
		{
			flag = true;
		}
		else if (commandPolice.isBroadcast())
		{
			flag = true;
		}
		if (flag && commandPolice.getAction() == CommandPolice.ACTION_CLEAR)
		{
			if (commandPolice.getTargetID() == null)
			{
				return;
			}
			StandardEntity target = this.worldInfo.getEntity(commandPolice.getTargetID());
			if (target instanceof Area)
			{
				this.priorityRoads.add(target.getID());
				this.targetAreas.add(target.getID());
			}
			else if (target.getStandardURN() == BLOCKADE)
			{
				Blockade blockade = (Blockade) target;
				if (blockade.isPositionDefined())
				{
					this.priorityRoads.add(blockade.getPosition());
					this.targetAreas.add(blockade.getPosition());
				}
			}
		}
	}
}

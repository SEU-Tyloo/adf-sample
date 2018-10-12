package Tyloo.module.complex.self;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.agent.communication.standard.bundle.information.MessageBuilding;
import adf.agent.communication.standard.bundle.information.MessageCivilian;
import adf.agent.communication.standard.bundle.information.MessageFireBrigade;
import adf.agent.communication.standard.bundle.information.MessagePoliceForce;
import adf.agent.communication.standard.bundle.information.MessageRoad;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.communication.CommunicationMessage;
import adf.component.module.algorithm.Clustering;
import adf.component.module.complex.HumanDetector;
import adf.debug.TestLogger;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.AMBULANCE_TEAM;
import static rescuecore2.standard.entities.StandardEntityURN.CIVILIAN;
import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

public class TestHumanDetector extends HumanDetector {
	private Clustering clustering;

	private EntityID result;

	private Logger logger;

	// 创建一个AToHuman，用来统计救护人员——>human的关系
	public static HashMap<EntityID,EntityID> AToHuman = new HashMap<>();
	// 创建一个limit，避免超多人救援一个人,human-->AT的数量
	public static HashMap<EntityID,List<EntityID>> limit = new HashMap<>();


	public TestHumanDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
		super(ai, wi, si, moduleManager, developData);
		logger = TestLogger.getLogger(agentInfo.me());
		this.clustering = moduleManager.getModule("TestHumanDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
		registerModule(this.clustering);
	}

	@Override
	public HumanDetector updateInfo(MessageManager messageManager) {
		logger.debug("Time:"+agentInfo.getTime());
		super.updateInfo(messageManager);
		this.reflectMessage(messageManager);
		return this;
	}

	@Override
	public HumanDetector calc() {
		logger.debug("\n");
		// 判断当前是否有搬运
		Human transportHuman = this.agentInfo.someoneOnBoard();
		if (transportHuman != null) {
			// 在搬运，直接结束
			logger.debug("someoneOnBoard:" + transportHuman);
			// 将这个人从AToHuman中清理出来
			if (AToHuman.containsKey(this.agentInfo.getID())){
				// 清理limit
				limit.remove(AToHuman.get(this.agentInfo.getID()));
				AToHuman.remove(this.agentInfo.getID());
			}
			this.result = transportHuman.getID();
			return this;
		}
		//有目标但是没在搬运
		else if (this.result != null) {
			Human target = (Human) this.worldInfo.getEntity(this.result);
			// 判断目标是否要放弃
			if (!isValidHuman(target)) {
				logger.debug("Invalid Human:" + target + " ==>reset target");
				this.result = null;
			}
		}
		if (this.result == null) {
			this.result = calcTarget();
		}
		// 经过上面的步骤后应该是有result的
		if (this.result != null){
			//避免空指针
			if (!limit.containsKey(this.result)){
				List<EntityID> list = new ArrayList<>();
				limit.put(this.result,list);
			}

			logger.debug("dang qian de limitRESULT"+limit.get(this.result));
			// 医生数量少于 8 且不包含自己
			if (limit.get(this.result).size() < 8 && !limit.get(this.result).contains(this.agentInfo.getID())){
				AToHuman.put(this.agentInfo.getID(),this.result);
				updateLimit(this.result);
				return this;
			}else if(limit.get(this.result).size() <= 8 && limit.get(this.result).contains(this.agentInfo.getID())){
				return this;
			}else{
				logger.debug("hen duo ren dou xuan ze le zhe ge mu biao ,fang qi "+limit.get(this.result));
				this.result = null;
				return this;
			}
		}
		return this;
	}
	// 当医生找到目标时候触发，更新limit的值，参数是human的id
	private void updateLimit(EntityID id){
		if(limit.containsKey(id)){
			limit.get(id).add(this.agentInfo.me().getID());
		}else {
			limit.put(id,new ArrayList<>());
		}

	}

	private EntityID calcTarget() {
		List<Human> rescueTargets = filterRescueTargets(this.worldInfo.getEntitiesOfType(CIVILIAN));
		List<Human> rescueTargetsInCluster = filterInCluster(rescueTargets);
		List<Human> targets = rescueTargetsInCluster;
		if (targets.isEmpty())
			targets = rescueTargets;


		logger.debug("Targets:"+targets);
		if (!targets.isEmpty()) {
			targets.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
			Human selected = targets.get(0);
			logger.debug("Selected:"+selected);
			return selected.getID();
		}

		return null;
	}

	@Override
	public EntityID getTarget() {
		return this.result;
	}

	private List<Human> filterRescueTargets(Collection<? extends StandardEntity> list) {
		List<Human> rescueTargets = new ArrayList<>();
		for (StandardEntity next : list) {
			if (!(next instanceof Human))
				continue;
			Human h = (Human) next;
			if (!isValidHuman(h))
				continue;
			if (h.getBuriedness() == 0)
				continue;
			rescueTargets.add(h);
		}
		return rescueTargets;
	}

	private List<Human> filterInCluster(Collection<? extends StandardEntity> entities) {
		int clusterIndex = clustering.getClusterIndex(this.agentInfo.getID());
		List<Human> filter = new ArrayList<>();
		HashSet<StandardEntity> inCluster = new HashSet<>(clustering.getClusterEntities(clusterIndex));
		for (StandardEntity next : entities) {
			if (!(next instanceof Human))
				continue;
			Human h = (Human) next;
			if (!h.isPositionDefined())
				continue;
			StandardEntity position = this.worldInfo.getPosition(h);
			if (position == null)
				continue;
			if (!inCluster.contains(position))
				continue;
			filter.add(h);
		}
		return filter;

	}

	private Civilian selectCivilian(Civilian civilian1, Civilian civilian2) {
		if (civilian1.getHP() > civilian2.getHP()) {
			return civilian1;
		} else if (civilian1.getHP() < civilian2.getHP()) {
			return civilian2;
		} else {
			if (civilian1.getBuriedness() > 0 && civilian2.getBuriedness() == 0) {
				return civilian1;
			} else if (civilian1.getBuriedness() == 0 && civilian2.getBuriedness() > 0) {
				return civilian2;
			} else {
				if (civilian1.getBuriedness() < civilian2.getBuriedness()) {
					return civilian1;
				} else if (civilian1.getBuriedness() > civilian2.getBuriedness()) {
					return civilian2;
				}
			}
			if (civilian1.getDamage() < civilian2.getDamage()) {
				return civilian1;
			} else if (civilian1.getDamage() > civilian2.getDamage()) {
				return civilian2;
			}
		}
		return civilian1;
	}

	private class DistanceSorter implements Comparator<StandardEntity> {
		private StandardEntity reference;
		private WorldInfo worldInfo;

		DistanceSorter(WorldInfo wi, StandardEntity reference) {
			this.reference = reference;
			this.worldInfo = wi;
		}

		public int compare(StandardEntity a, StandardEntity b) {
			int d1 = this.worldInfo.getDistance(this.reference, a);
			int d2 = this.worldInfo.getDistance(this.reference, b);
			return d1 - d2;
		}
	}

	private boolean isValidHuman(StandardEntity entity) {
		// 判断这个人是否需要帮助
		if (entity == null)
			return false;
		if (!(entity instanceof Human))
			return false;

		Human target = (Human) entity;
		logger.debug(" ?target " + target.getID() + "====hp" + target.getHP() + "====bury" + target.getBuriedness());
		if (!target.isHPDefined() || target.getHP() == 0){
			return false;
		}
		if (!target.isPositionDefined()){
			return false;
		}
		if (!target.isDamageDefined() || target.getDamage() == 0){
			// 受伤
			logger.debug("target NO HURT");
			return false;
		}
		if (!target.isBuriednessDefined()){
			logger.debug("target NO BURY");
			return false;
		}

		StandardEntity position = worldInfo.getPosition(target);
		if (position == null)
			return false;

		StandardEntityURN positionURN = position.getStandardURN();
		if (positionURN == REFUGE || positionURN == AMBULANCE_TEAM){
			logger.debug("target POSITION" + positionURN);
			return false;
		}

		return true;
	}
	private void reflectMessage(MessageManager messageManager)
	{
		Set<EntityID> changedEntities = this.worldInfo.getChanged().getChangedEntities();
		changedEntities.add(this.agentInfo.getID());
		int time = this.agentInfo.getTime();

		for(CommunicationMessage message : messageManager.getReceivedMessageList()) {
			Class<? extends CommunicationMessage> messageClass = message.getClass();
			if(messageClass == MessageBuilding.class) {
				MessageBuilding mb = (MessageBuilding)message;
				if(!changedEntities.contains(mb.getBuildingID())) {
					MessageUtil.reflectMessage(this.worldInfo, mb);
				}
			} else if(messageClass == MessageRoad.class) {
				MessageRoad mr = (MessageRoad)message;
				if(mr.isBlockadeDefined() && !changedEntities.contains(mr.getBlockadeID())) {
					MessageUtil.reflectMessage(this.worldInfo, mr);
				}

			} else if(messageClass == MessageCivilian.class) {
				MessageCivilian mc = (MessageCivilian) message;
				if(!changedEntities.contains(mc.getAgentID())){
					MessageUtil.reflectMessage(this.worldInfo, mc);
				}

			} else if(messageClass == MessageAmbulanceTeam.class) {
				MessageAmbulanceTeam mat = (MessageAmbulanceTeam)message;
				if(!changedEntities.contains(mat.getAgentID())) {
					MessageUtil.reflectMessage(this.worldInfo, mat);
				}

			} else if(messageClass == MessageFireBrigade.class) {
				MessageFireBrigade mfb = (MessageFireBrigade) message;
				if(!changedEntities.contains(mfb.getAgentID())) {
					MessageUtil.reflectMessage(this.worldInfo, mfb);
				}

			} else if(messageClass == MessagePoliceForce.class) {
				MessagePoliceForce mpf = (MessagePoliceForce) message;
				if(!changedEntities.contains(mpf.getAgentID())) {
					MessageUtil.reflectMessage(this.worldInfo, mpf);
				}

			}
		}
	}
}

package Tyloo.module.complex.self;

import static rescuecore2.standard.entities.StandardEntityURN.AMBULANCE_CENTRE;
import static rescuecore2.standard.entities.StandardEntityURN.AMBULANCE_TEAM;
import static rescuecore2.standard.entities.StandardEntityURN.BUILDING;
import static rescuecore2.standard.entities.StandardEntityURN.FIRE_BRIGADE;
import static rescuecore2.standard.entities.StandardEntityURN.FIRE_STATION;
import static rescuecore2.standard.entities.StandardEntityURN.GAS_STATION;
import static rescuecore2.standard.entities.StandardEntityURN.POLICE_FORCE;
import static rescuecore2.standard.entities.StandardEntityURN.POLICE_OFFICE;
import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import adf.debug.TestLogger;
import org.apache.log4j.Logger;

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
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.Search;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

public class TestSearchForFire extends Search {
	private PathPlanning pathPlanning;
	private Clustering clustering;

	private EntityID result;
	private Collection<EntityID> unsearchedBuildingIDs;
	private Logger logger;

	public TestSearchForFire(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
		super(ai, wi, si, moduleManager, developData);
		logger = TestLogger.getLogger(agentInfo.me());
		this.unsearchedBuildingIDs = new HashSet<>();

		StandardEntityURN agentURN = ai.me().getStandardURN();
		if (agentURN == AMBULANCE_TEAM) {
			this.pathPlanning = moduleManager.getModule("TestSearch.PathPlanning.Ambulance", "adf.sample.module.algorithm.SamplePathPlanning");
			this.clustering = moduleManager.getModule("TestSearch.Clustering.Ambulance", "adf.sample.module.algorithm.SampleKMeans");
		} else if (agentURN == FIRE_BRIGADE) {
			this.pathPlanning = moduleManager.getModule("TestSearch.PathPlanning.Fire", "adf.sample.module.algorithm.SamplePathPlanning");
			this.clustering = moduleManager.getModule("TestSearch.Clustering.Fire", "adf.sample.module.algorithm.SampleKMeans");
		} else if (agentURN == POLICE_FORCE) {
			this.pathPlanning = moduleManager.getModule("TestSearch.PathPlanning.Police", "adf.sample.module.algorithm.SamplePathPlanning");
			this.clustering = moduleManager.getModule("TestSearch.Clustering.Police", "adf.sample.module.algorithm.SampleKMeans");
		}
		registerModule(this.clustering);
		registerModule(this.pathPlanning);
	}

	@Override
	public Search updateInfo(MessageManager messageManager) {
		logger.debug("Time:" + agentInfo.getTime());
		super.updateInfo(messageManager);
		this.reflectMessage(messageManager);
		this.unsearchedBuildingIDs.removeAll(this.worldInfo.getChanged().getChangedEntities());
		if (this.unsearchedBuildingIDs.isEmpty()) {
			this.reset();
			this.unsearchedBuildingIDs.removeAll(this.worldInfo.getChanged().getChangedEntities());
		}
		return this;
	}

	@Override
	public Search calc() {
		this.result = null;
		if (unsearchedBuildingIDs.isEmpty())
			return this;

		logger.debug("unsearchedBuildingIDs:" + unsearchedBuildingIDs);
		this.pathPlanning.setFrom(this.agentInfo.getPosition());
		this.pathPlanning.setDestination(this.unsearchedBuildingIDs);
		List<EntityID> path = this.pathPlanning.calc().getResult();
		logger.debug("best path is:"+path);
		if (path != null && path.size() > 2) {
			this.result = path.get(path.size() - 3);
		}else if (path != null && path.size() > 0) {
			this.result = path.get(path.size() - 1);
		}
		logger.debug("choosed:"+result);
		return this;
	}

	private void reset() {
		this.unsearchedBuildingIDs.clear();
		int clusterIndex = this.clustering.getClusterIndex(this.agentInfo.getID());
		Collection<StandardEntity> clusterEntities = this.clustering.getClusterEntities(clusterIndex);
		if (clusterEntities != null && clusterEntities.size() > 0) {
			for (StandardEntity entity : clusterEntities) {
				if (entity instanceof Building && entity.getStandardURN() != REFUGE) {
					this.unsearchedBuildingIDs.add(entity.getID());
				}
			}
		} else {
			this.unsearchedBuildingIDs.addAll(this.worldInfo.getEntityIDsOfType(BUILDING, GAS_STATION, AMBULANCE_CENTRE, FIRE_STATION, POLICE_OFFICE));
		}
	}

	@Override
	public EntityID getTarget() {
		return this.result;
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
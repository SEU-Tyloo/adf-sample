package Tyloo.module.complex.self;

import java.util.*;

import adf.agent.action.common.ActionMove;
import adf.component.module.algorithm.PathPlanning;
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
import adf.component.module.complex.BuildingDetector;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

public class TestBuildingDetector extends BuildingDetector {
	private EntityID result;
	private Clustering clustering;
	private Logger logger;


	// HOX
	public static HashMap<EntityID,EntityID> buildingToPeople = new HashMap<>();
	public static HashMap<Integer,List<EntityID>> clusterToPeople = new HashMap<>();
    public static ArrayList<Building> extroS = new ArrayList<>();

    private ArrayList<EntityID> historyPosition;
	// HOX

	public TestBuildingDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
		super(ai, wi, si, moduleManager, developData);
		logger = TestLogger.getLogger(agentInfo.me());
		this.clustering = moduleManager.getModule("TestBuildingDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
		registerModule(this.clustering);
        historyPosition = new ArrayList<>();
	}

	@Override
	public BuildingDetector updateInfo(MessageManager messageManager) {
		logger.debug("Time:" + agentInfo.getTime());
		super.updateInfo(messageManager);
		this.reflectMessage(messageManager);

		historyPosition.add(this.agentInfo.getPosition());
        try{
            resetStatic();
        }catch (Exception e){
            e.printStackTrace();
        }

		return this;
	}

    synchronized private void resetStatic(){
        // 修正clusterTo building to
        for (EntityID changed : worldInfo.getChanged().getChangedEntities()) {
            StandardEntity changedEntity = worldInfo.getEntity(changed);
            if (changedEntity.getStandardURN().equals(StandardEntityURN.BUILDING))
            {
                Building changedBuilding = (Building) changedEntity;
                if(! changedBuilding.isOnFire()
                        ||
                        (changedBuilding.isTemperatureDefined()
                                && changedBuilding.getTemperature() <60)  ){
                    EntityID entityID = buildingToPeople.get(changedBuilding.getID());
                    int cluster = clustering.getClusterIndex(changedBuilding.getID());
                    removeClusterToPeople(cluster,entityID);
                    buildingToPeople.put(changedBuilding.getID(),null);
                    if(getOnfile(cluster) == 0){
                        clusterToPeople.put(cluster,new ArrayList<>());
                    }
                }
            }
        }
	    if((agentInfo.isWaterDefined() && agentInfo.getWater() == 0) || isBlocked() ){
            for (EntityID buildingID : buildingToPeople.keySet()) {
                if(buildingToPeople.get(buildingID) == this.agentInfo.getID()){
                    int cluster = clustering.getClusterIndex(buildingID);
                    buildingToPeople.put(buildingID,null);
                    removeClusterToPeople(cluster,agentInfo.getID());
                }
            }
        }
    }

    private  boolean isBlocked(){
	    if(historyPosition.size() > 5){
            EntityID a = historyPosition.get(historyPosition.size()-1);
            EntityID b = historyPosition.get(historyPosition.size()-2);
            EntityID c = historyPosition.get(historyPosition.size()-3);
            return a == b && b == c;
        }
        return  false;
    }

    synchronized private  boolean hasBeenAlloc(){
	    return buildingToPeople.values().contains(this.agentInfo.getID());
    }

	@Override
	public BuildingDetector calc() {

	    if(!hasBeenAlloc()){
            this.result = this.calcTarget();
            return this;
        }
        logger.debug("I has been allocated!");
        return this;
	}

    synchronized private EntityID calcTarget() {
		Collection<StandardEntity> entities = this.worldInfo.getEntitiesOfType(
		        StandardEntityURN.BUILDING,
                StandardEntityURN.GAS_STATION,
                StandardEntityURN.AMBULANCE_CENTRE,
				StandardEntityURN.FIRE_STATION,
                StandardEntityURN.POLICE_OFFICE);



        int clusterIndex = clustering.getClusterIndex(this.agentInfo.getID());
		List<Building> fireyBuildings = filterFiery(entities); //获得着火的列表.
		List<Building> clusterBuildings = filterInCluster(fireyBuildings);//获得自己附近的火区列表
		List<StandardEntity> agents = new ArrayList<>(worldInfo.getEntitiesOfType(StandardEntityURN.FIRE_BRIGADE));

		if(fireyBuildings.isEmpty()) {
            return null;
        }

        logger.debug("====================================================================");
        logger.debug("Fire Building: " + fireyBuildings.toString());
        logger.debug("Cluster Fire Building: " + clusterBuildings.toString());

        if(!clusterBuildings.isEmpty()){  //自己附近就有着火点
		    //执行区域内部的逻辑

			int hasAllocate = getClusterPeople(clusterIndex);
            logger.debug("There are fire in my cluster!");

            boolean notJoin = (
                    ! inClusterFire(this.agentInfo.getID(),clusterIndex) // 自己不在
					&& ((hasAllocate > clusterBuildings.size()+1)));   //人还不少
			logger.debug("Has allocate:" + hasAllocate);
            logger.debug("So need me? :" + !notJoin);

            int distance = worldInfo.getDistance(this.agentInfo.getID(),clusterBuildings.get(0).getID());

            if(!notJoin) {
            	logger.debug("I am going to the cluster!");
                //值得去加入!
                // HOX  多余的两个人应该去火势最大的两个地方.
                // HOX
                for (StandardEntity entity : clusterBuildings)
                {
                	logger.debug("I see the " + entity.getID());
                    //HOX
                    Building building = (Building) entity;
                    if (buildingToPeople.get(entity.getID()) != null
                            && buildingToPeople.get(entity.getID()) != agentInfo.getID())
                    {
						logger.debug("One people has been doing this.");
						extroS.add(building);
                    	continue;
                    }
                    logger.debug("No body doing this, I will doing this!");
					// HOX: 添加映射关系
					buildingToPeople.put(entity.getID(),this.agentInfo.getID());
					addClusterToPeople(clusterIndex,this.agentInfo.getID());
					// HOX
					return entity.getID();
                    //HOX
                }
			} //否则去执行世界逻辑
        }


		StandardEntity me = this.agentInfo.me();
		for (StandardEntity entity : fireyBuildings)
		{
		    Building building = (Building) entity;
			int remoteClusterId = clustering.getClusterIndex(building.getID());
		    if (agents.isEmpty())
			{
				break;
			}
			else if (agents.size() == 1)
			{
				if (agents.get(0).getID().getValue() == me.getID().getValue())
				{
                    buildingToPeople.put(entity.getID(),this.agentInfo.getID());
                    addClusterToPeople(clusterIndex,this.agentInfo.getID());
					return entity.getID();
				}
				break;
			}

			agents.sort(new DDistanceSorter(this.worldInfo,entity));

			if(agents.indexOf(worldInfo.getEntity(this.agentInfo.getID())) <= 3 )
			{// 距离上没问题
			    logger.debug("My cluster ok, but I am near a fire !");
			    if(getClusterPeople(remoteClusterId) <= getOnfile(remoteClusterId)){
                    logger.debug("They need some people!");
                    if(buildingToPeople.get(entity.getID()) != null
                            && buildingToPeople.get(entity.getID()) != agentInfo.getID()){
                        logger.debug("One people has been doing this.");
                        extroS.add(building);
                        continue; //有人了，考虑下一个建筑
                    }
                    buildingToPeople.put(entity.getID(),this.agentInfo.getID());
                    addClusterToPeople(clusterIndex,this.agentInfo.getID());
                    return entity.getID();
                }
                //全部continue了, 说明每个建筑都有人,算了.
			}
		}

		try{
            if(extroS.size() > 1){
                int extroId = clustering.getClusterIndex(extroS.get(0));
                Building extroBuilding = extroS.get(0);
                extroS.remove(0);
                if(getOnfile(extroId) > 5 && extroBuilding.getFieryness() < 4){ //　火区真的比较大了，迅速集结
                    buildingToPeople.put(extroBuilding.getID(),this.agentInfo.getID());
                    addClusterToPeople(clusterIndex,this.agentInfo.getID());
                    logger.debug("Fire is so big! I am going here!");
                    return extroBuilding.getID();
                }
            }
        }catch (IndexOutOfBoundsException e){
            return null;
        }
        return null;
        //SYSTEMW
	}

    synchronized private static void addClusterToPeople(int clusterIndex,EntityID people){ //在区域内添加一个人
        clusterToPeople.computeIfAbsent(clusterIndex, k -> new ArrayList<>());
        clusterToPeople.get(clusterIndex).add(people);
    }
    synchronized private static void removeClusterToPeople(int clusterIndex,EntityID people){ //在区域内减少
	    if(clusterToPeople.get(clusterIndex) != null){
            clusterToPeople.get(clusterIndex).remove(people);
        }else {
	        clusterToPeople.put(clusterIndex,new ArrayList<>());
        }
    }

    synchronized private static int getClusterPeople(int clusterId){ //计算区域内有多少人被分配来灭火
	    if(clusterToPeople.get(clusterId) == null){
            clusterToPeople.put(clusterId,new ArrayList<>());
	        return 0;
        }
	    else
	        return clusterToPeople.get(clusterId).size();
    }

    private int getOnfile(int clusterIndex){ //计算区域着火数量
	    int count = 0;
        Collection<EntityID> inCluster = this.clustering.getClusterEntityIDs(clusterIndex);
        for (EntityID entityId : inCluster) {
            StandardEntity entity = worldInfo.getEntity(entityId);
            if (entity instanceof Building && ((Building) entity).isOnFire()) {
                count += 1;
            }
        }
        return count;
    }

    synchronized private static boolean inClusterFire(EntityID people,int clusterId){ // 自己是否在 cluster的救火人员中
	    if(clusterToPeople.get(clusterId) == null){
            clusterToPeople.put(clusterId,new ArrayList<>());
            return false;
        }else {
	        try{
                return clusterToPeople.get(clusterId).contains(people);
            }catch (NullPointerException e){
	            return false;
            }
        }
    }


	private List<Building> filterFiery(Collection<? extends StandardEntity> input) {
		ArrayList<Building> fireBuildings = new ArrayList<>();
		for (StandardEntity entity : input) {
			if (entity instanceof Building && ((Building) entity).isOnFire()) {
				if(((Building) entity).isFloorsDefined() && ((Building) entity).getFieryness() == 8)
					continue;
				fireBuildings.add((Building) entity);
			}
		}
		return fireBuildings;
	}

	/**
	 * Cluster 不灵, 我将其修改为　附近算法
	 * @param targetAreas
	 * @return
	 */
	private List<Building> filterInCluster(Collection<Building> targetAreas) {
		int clusterIndex = clustering.getClusterIndex(this.agentInfo.getID());
		List<Building> clusterTargets = new ArrayList<>();
		Collection<EntityID> inCluster = this.clustering.getClusterEntityIDs(clusterIndex);
		for(Building building : targetAreas){
			if(inCluster.contains(building.getID())){
				clusterTargets.add(building);
			}
		}
		return clusterTargets;
	}

	@Override
	public EntityID getTarget() {
		return this.result;
	}

	private class DistanceSorter implements Comparator<Building> {
		private StandardEntity reference;
		private WorldInfo worldInfo;

		DistanceSorter(WorldInfo wi, StandardEntity reference) {
			this.reference = reference;
			this.worldInfo = wi;
		}

		public int compare(Building a, Building b) {
			if (a.getFieryness() == 3 && b.getFieryness() != 3) {
				return 1;
			}
			if (a.getFieryness() != 3 && b.getFieryness() == 3) {
				return -1;
			}
			int d1 = this.worldInfo.getDistance(this.reference, a);
			int d2 = this.worldInfo.getDistance(this.reference, b);
			return d1 - d2;

		}
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


	private class DDistanceSorter implements Comparator<StandardEntity>
	{
		private StandardEntity reference;
		private WorldInfo worldInfo;

		DDistanceSorter(WorldInfo wi, StandardEntity reference)
		{
			this.reference = reference;
			this.worldInfo = wi;
		}

		public int compare(StandardEntity a, StandardEntity b)
		{
			int d1 = this.worldInfo.getDistance(this.reference, a);
			int d2 = this.worldInfo.getDistance(this.reference, b);
			return d1 - d2;
		}
	}
}

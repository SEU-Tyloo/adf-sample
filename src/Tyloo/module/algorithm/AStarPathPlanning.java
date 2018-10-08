package Tyloo.module.algorithm;

import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.misc.collections.LazyMap;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

public class AStarPathPlanning extends PathPlanning {

   private Map<EntityID, Set<EntityID>> graph;

   private EntityID from;
   private Collection<EntityID> targets;
   private List<EntityID> result;

   public AStarPathPlanning(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
       super(ai, wi, si, moduleManager, developData);
       this.init();
   }

   private void init() {
       Map<EntityID, Set<EntityID>> neighbours = new LazyMap<EntityID, Set<EntityID>>() {
           @Override
           public Set<EntityID> createValue() {
               return new HashSet<>();
           }
       };
       for (Entity next : this.worldInfo) {
           if (next instanceof Area) {
               Collection<EntityID> areaNeighbours = ((Area) next).getNeighbours();
               neighbours.get(next.getID()).addAll(areaNeighbours);
           }
       }//可通过的area
       this.graph = neighbours;
   }

   @Override
   public List<EntityID> getResult() {
       return this.result;
   }

   @Override
   public PathPlanning setFrom(EntityID id) {
       this.from = id;
       return this;
   }

   @Override
   public PathPlanning setDestination(Collection<EntityID> targets) {
     this.targets = targets;
     return this;
   }

   @Override
   public PathPlanning precompute(PrecomputeData precomputeData) {
       super.precompute(precomputeData);
       return this;
   }

   @Override
   public PathPlanning resume(PrecomputeData precomputeData) {
       super.resume(precomputeData);
       return this;
   }

   @Override
   public PathPlanning preparate() {
       super.preparate();
       return this;
   }

   @Override
   public PathPlanning calc() {
       //采用广度优先遍历，找到最近的路径
       List<EntityID> isPass=new LinkedList<>();
       Map<EntityID,EntityID> ancestors = new HashMap<>();
       isPass.add(this.from);
       EntityID child;
       boolean found=false;
       ancestors.put(this.from,this.from);//前面是儿子，后面是父亲，父子关系图
       do {
           child = isPass.remove(0);

           //判断child是否在targets中,如果在目标区域内，则跳出循环
           if (isGoal(child, targets)) {
               found = true;
               break;
           }

           Collection<EntityID> neighbours = graph.get(child);
           if (neighbours.isEmpty()) {
               continue;
           }//如果child没有在targets中，开始遍历它的邻居，寻找其邻居是否在targets中

           for (EntityID neighbour : neighbours) {
               if (isGoal(neighbour, targets)) {
                   ancestors.put(neighbour, child);
                   child = neighbour;
                   found = true;
                   break;//如果其子孙在目标区域内，跳出循环
               } else {
                   if (!ancestors.containsKey(neighbour)) {
                       isPass.add(neighbour);
                       ancestors.put(neighbour, child);
                   }//如果其不在父子关系图中，将其放在遍历列表和父子关系图中
               }
           }
       }while(!found && !isPass.isEmpty());

       if(!found){
           this.result=null;
       }//没有找到路径，返回空

       EntityID current = child;
       LinkedList<EntityID> path = new LinkedList<>();
       do {
           path.add(0, current);
           current = ancestors.get(current);
           if (current == null) {
               throw new RuntimeException("Found a node with no ancestor! Something is broken.");
           }
       } while (current != this.from);//在父子关系图中找出来路径

       this.result = path;
       return this;
   }



    private boolean isGoal(EntityID e, Collection<EntityID> test) {
        return test.contains(e);
    }
}

from pathlib import Path
R=Path(__file__).resolve().parents[1]
old=(R/'work/ProbeLocomotionReentry.java').read_text();head=old[:old.index(' static JsonObject run(')].replace('class ProbeLocomotionReentry','class ProbeDirectFirstStep')
tail=r'''
 static JsonObject run(ModelDefinition d,net.minestom.server.instance.InstanceContainer instance,double phase,double speed,DragonActionController.Action action)throws Exception{
  int baseline=instance.getEntities().size();var a=new BossModelActor(d,instance,new Pos(0,0,0),3f,64d);var out=new JsonObject();
  try{var m=model(a);var c=new DragonActionController(m,handler(a),d.getId(),3f,a::move,a::getCurrentPosition);c.seedAlreadyWalkingPhase(phase);c.requestAction(action);var q=new Capture(a,c);
   q.toReady(speed);double firstReady=q.t;var atReady=a.getCurrentPosition();q.duration(.15,speed);if(a.getCurrentPosition().distance(atReady)>1e-10)throw new IllegalStateException("READY moved");
   c.requestWalk();double restartBegin=q.t;q.frames.add(frame(a,m,c,q.t,true));q.duration(DragonActionController.RESTART_SECONDS*c.getNominalSpeed()/speed,speed);
   if(c.getState()!=DragonActionController.State.WALK)throw new IllegalStateException("No walk after moving startup");
   if(Math.abs(a.getCurrentPosition().distance(atReady)-DragonActionController.RESTART_TRAVEL_BB*3/16)>1e-9)throw new IllegalStateException("Startup actor travel mismatch");
   if(Math.abs(c.snapshot().getSourceSeconds())>1e-8)throw new IllegalStateException("Wrong walk reentry phase");
   q.duration(c.getStrideBlocks()/speed,speed);c.requestStop();q.toReady(speed);double secondReady=q.t;
   c.requestWalk();q.frames.add(frame(a,m,c,q.t,true));q.duration(DragonActionController.RESTART_SECONDS*c.getNominalSpeed()/speed+1.2,speed);
   if(c.getState()!=DragonActionController.State.WALK)throw new IllegalStateException("Second startup failed");
   out.addProperty("requested_phase",phase);out.addProperty("requested_speed_blocks_sec",speed);out.addProperty("action",action.name());out.add("frames",q.frames);out.add("transitions",G.toJsonTree(c.getTransitions()));out.addProperty("first_ready_at",firstReady);out.addProperty("restart_begin_at",restartBegin);out.addProperty("second_ready_at",secondReady);out.addProperty("duration",q.t);out.addProperty("bones",m.getParts().size());
   out.addProperty("ItemDisplay_count",instance.getEntities().stream().filter(e->e.getEntityType()==EntityType.ITEM_DISPLAY).count());out.addProperty("ArmorStand_count",instance.getEntities().stream().filter(e->e.getEntityType()==EntityType.ARMOR_STAND).count());out.addProperty("total_entities",instance.getEntities().size()-baseline);out.addProperty("stationary_ready_only_startup_moves",true);out.addProperty("approved_walk_restarted_twice",true);
  }finally{a.close();a.close();}for(int i=0;i<10;i++)tick();if(instance.getEntities().size()!=baseline)throw new IllegalStateException("Entity leak");out.addProperty("closed_entities_zero",true);return out;
 }
 static JsonObject guards(ModelDefinition d,net.minestom.server.instance.InstanceContainer instance)throws Exception{
  int baseline=instance.getEntities().size();var a=new BossModelActor(d,instance,new Pos(0,0,0),3f,64d);var out=new JsonObject();var mode=new AtomicInteger(0);
  try{var m=model(a);var c=new DragonActionController(m,handler(a),d.getId(),3f,p->{if(mode.get()==0)a.move(p);else if(mode.get()==2){var old=a.getCurrentPosition();a.move(new Pos(old.x()+(p.x()-old.x())*.5,old.y(),old.z()+(p.z()-old.z())*.5,old.yaw(),old.pitch()));}},a::getCurrentPosition);
   c.requestWalk();var before=c.snapshot();for(int i=0;i<3;i++){tick();c.step(.05,0);}if(c.snapshot().getSourceSeconds()!=before.getSourceSeconds()||c.snapshot().getActor().distance(before.getActor())>1e-10)throw new IllegalStateException("Zero startup advanced");
   mode.set(1);for(int i=0;i<3;i++){tick();c.step(.05,1.5);}if(c.snapshot().getSourceSeconds()!=before.getSourceSeconds()||c.snapshot().getActor().distance(before.getActor())>1e-10)throw new IllegalStateException("Blocked startup advanced");
   mode.set(2);tick();c.step(.05,1.5);var partial=c.snapshot();if(!(partial.getSourceSeconds()>0&&partial.getSourceSeconds()<.05))throw new IllegalStateException("Partial startup clock not accepted-distance driven");
   double travel=8*partial.getSourceSeconds()*partial.getSourceSeconds()/(2*DragonActionController.RESTART_ACCEL_SECONDS)*3/16;
   if(Math.abs(travel-partial.getActor().distance(before.getActor()))>1e-10)throw new IllegalStateException("Partial startup profile mismatch");
   mode.set(0);var q=new Capture(a,c);int n=0;while(c.getState()!=DragonActionController.State.WALK){if(++n>250)throw new IllegalStateException("Startup guard timeout");q.step(.05,.75);}
   mode.set(1);before=c.snapshot();q.duration(.15,.75);if(c.snapshot().getWalkCycles()!=before.getWalkCycles()||c.snapshot().getActor().distance(before.getActor())>1e-10)throw new IllegalStateException("Blocked unchanged walk advanced");mode.set(0);q.duration(.3,.75);
   String snap=G.toJson(c.snapshot());boolean invalid=false;try{c.step(.05,-1);}catch(IllegalArgumentException ex){invalid=true;}if(!invalid||!snap.equals(G.toJson(c.snapshot())))throw new IllegalStateException("Invalid input mutated state");
   c.requestAction(DragonActionController.Action.CLAW);snap=G.toJson(c.snapshot());invalid=false;try{c.requestWalk();}catch(IllegalStateException ex){invalid=true;}if(!invalid||!snap.equals(G.toJson(c.snapshot())))throw new IllegalStateException("Committed stop interrupted");
   out.addProperty("zero_speed_restart_holds_source_and_root",true);out.addProperty("blocked_restart_holds_source_and_root",true);out.addProperty("partial_restart_clock_from_accepted_distance",true);out.addProperty("speed_change_during_restart_keeps_profile_distance",true);out.addProperty("blocked_unchanged_walk_holds_distance_phase",true);out.addProperty("invalid_input_and_committed_action_reject_without_mutation",true);out.add("partial_restart_snapshot",G.toJsonTree(partial));
  }finally{a.close();}for(int i=0;i<10;i++)tick();if(instance.getEntities().size()!=baseline)throw new IllegalStateException("Guard leak");out.addProperty("closed_entities_zero",true);return out;
 }
 public static void main(String[] args)throws Exception{
  var path=Path.of(args[0]).toAbsolutePath().normalize();ref=JsonParser.parseString(Files.readString(path.resolve("motion_reference.json"))).getAsJsonObject();MinecraftServer.init();process=MinecraftServer.process();noListener();
  try{var b=new ModelBundle(path);b.load();var d=b.definition(ref.get("model_id").getAsString());var instance=MinecraftServer.getInstanceManager().createInstanceContainer();for(int x=-1;x<=0;x++)for(int z=0;z<=2;z++)instance.loadChunk(x,z).join();process.dispatcher().start();
   try(var writer=new JsonWriter(Files.newBufferedWriter(Path.of(args[1])))){writer.beginObject();writer.name("cases").beginArray();int count=0;
    for(double phase:new double[]{.19,.4})for(double speed:new double[]{.75,1.5,2.25}){G.toJson(run(d,instance,phase,speed,DragonActionController.Action.CLAW),writer);System.out.println("FIRST_STEP_CASE_DONE "+(++count)+" phase="+phase+" speed="+speed);}
    G.toJson(run(d,instance,.4,1.5,DragonActionController.Action.BREATH),writer);writer.endArray();writer.name("guards");G.toJson(guards(d,instance),writer);
    writer.name("no_listening_socket").value(true);writer.name("online_players").value(0);writer.name("server_start_called").value(false);writer.name("actual_existing_actor_loader_WSEE").value(true);writer.name("client_AI_damage_FX_or_perf_tested").value(false);writer.name("production_repo_written").value(false);writer.endObject();}
   System.out.println("ALL_7_EXISTING_ACTOR_CASES_AND_STARTUP_GUARDS_DONE");
  }finally{process.dispatcher().shutdown();MinecraftServer.stopCleanly();}
 }
}
'''
(R/'work/ProbeDirectFirstStep.java').write_text(head+tail,encoding='utf8')
run=(R/'work/run_locomotion_reentry_runtime.ps1').read_text().replace('locomotion_reentry_runtime_classpath','direct_first_step_runtime_classpath').replace('compiled_locomotion_reentry_runtime','compiled_direct_first_step_runtime').replace('ProbeLocomotionReentry','ProbeDirectFirstStep').replace('outputs\\locomotion_reentry_isolation','outputs\\reentry_direct_first_step').replace('outputs/locomotion_reentry_isolation','outputs/reentry_direct_first_step').replace('ACTUAL_LOCOMOTION_REENTRY_TRACE','ACTUAL_DIRECT_FIRST_STEP_TRACE').replace('-Xmx384m','-Xmx256m')
(R/'work/run_direct_first_step_runtime.ps1').write_text(run,encoding='utf8')
print('BOUNDED7CASE_ACTUAL_ACTOR_PROBE_READY',flush=True)

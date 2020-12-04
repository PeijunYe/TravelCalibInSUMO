import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Random;

import de.tudresden.sumo.cmd.Simulation;
import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.ws.container.SumoStringList;
import it.polito.appeal.traci.SumoTraciConnection;

public class ConnectMain
{
	private OriginDestination odGenerator;
	private Random rand;

	public ConnectMain(OriginDestination odGenerator)
	{
		super();
		rand = new Random();
		this.odGenerator = odGenerator;
	}

	public void Simulation()
	{
		long startTime = System.currentTimeMillis();
		String sumo_bin = "D:\\SUMO\\bin\\sumo.exe";
		String config_file = "D:\\SUMO\\Chengdu\\AbstractSim.sumocfg";
		double step_length = 0.1;
		try
		{
			SumoTraciConnection conn = new SumoTraciConnection(sumo_bin, config_file);
			conn.addOption("step-length", step_length + "");
			conn.addOption("start", "true"); // start sumo immediately
			// start Traci Server
			conn.runServer();
			conn.setOrder(1);
			for (int i = 0; i < 900 * 10; i++)
			{
				conn.do_timestep();
				if (i % 900 == 0)
				{
					int timeInter = i / 900;
					MFCalibration(conn, timeInter);
				}
			}
			conn.close();
			long endTime = System.currentTimeMillis();
			long last = (long) (((float) (endTime - startTime)) / 1000);
			System.out.println("运行时间： " + last + " s");
		} catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	private void MFCalibration(SumoTraciConnection conn, int timeInter) throws Exception
	{
		SumoStringList vehIDs = (SumoStringList) conn.do_job_get(Vehicle.getIDList());
		if (vehIDs.isEmpty())
			return;
		LinkedHashMap<LinkedList<Integer>, Integer> pathVec = odGenerator.PathODEstByLinkFlow(timeInter, false, 1, 5000);
		for (String id : vehIDs)
		{
			SumoStringList route = (SumoStringList) conn.do_job_get(Vehicle.getRoute(id));
			String currRoadID = (String) conn.do_job_get(Vehicle.getRoadID(id));
			System.out.println("Vehicle.getRoadID: " + currRoadID);
			// get origin and destination nodes
			String origStr = currRoadID.substring(currRoadID.lastIndexOf("-") + 1);
			int origin = Integer.parseInt(origStr);
			String destStr = route.get(route.size() - 1).substring(route.get(route.size() - 1).lastIndexOf("-") + 1);
			int dest = Integer.parseInt(destStr);
			// compute alpha, beta, gamma selection prob. for top 3 paths
			LinkedHashMap<LinkedList<Integer>, Integer> topPath = new LinkedHashMap<LinkedList<Integer>, Integer>();
			int topPathNum = 3;
			int totalFlow = 0;
			for (int j = 0; j < topPathNum; j++)
			{
				LinkedList<Integer> maxPath = null;
				int maxFlow = Integer.MIN_VALUE;
				for (LinkedList<Integer> path : pathVec.keySet())
				{
					if (path.getFirst() != origin || path.getLast() != dest)
						continue;
					int flow = pathVec.get(path);
					if (maxFlow < flow)
					{
						maxFlow = flow;
						maxPath = path;
					}
				}
				if (maxPath != null)
				{
					topPath.put(maxPath, maxFlow);
					totalFlow += maxFlow;
					pathVec.put(maxPath, Integer.MIN_VALUE);
				}
			}
			// set new route
			float selectProb = rand.nextFloat();
			float basicProb = 0;
			int count = 0;
			for (LinkedList<Integer> path : topPath.keySet())
			{

				basicProb += ((float) topPath.get(path)) / totalFlow;
				if (selectProb < basicProb)
				{
					SumoStringList newRoute = new SumoStringList();
					newRoute.add(currRoadID);
					for (int i = 0; i < path.size() - 1; i++)
					{
						String edge = path.get(i) + "-" + path.get(i + 1);
						newRoute.add(edge);
					}
					conn.do_job_set(Vehicle.setRoute(id, newRoute));
					conn.do_job_set(Vehicle.setParameter(id, "depart", conn.do_job_get(Simulation.getTime()).toString()));
					break;
				} else
				{
					count++;
					if (count >= topPath.size() - 1)
					{
						SumoStringList newRoute = new SumoStringList();
						for (Iterator<Integer> iterator = path.iterator(); iterator.hasNext();)
						{
							String nodeStr = iterator.next().toString();
							newRoute.add(nodeStr);
						}
						conn.do_job_set(Vehicle.setRoute(id, newRoute));
						conn.do_job_set(Vehicle.setParameter(id, "depart", conn.do_job_get(Simulation.getTime()).toString()));
						break;
					}
				}
			}
		}
		System.out.println("VehicleIDs: " + vehIDs.size());
	}

	public static void main(String[] args)
	{
		RoadNetwork rn = new RoadNetwork("AbstractLinks.edg.xml", "FilterRoadName.txt");
		rn.Init();
		OriginDestination odPath = new OriginDestination(rn, "Route.txt", "LinkFlowStat.txt", null);
		odPath.InitInput();
		ConnectMain conMain = new ConnectMain(odPath);
		conMain.Simulation();
		Evaluation eval = new Evaluation("Route.txt", "LinkFlowStat.txt");
		eval.Eval();
	}
}

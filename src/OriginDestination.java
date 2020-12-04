import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedList;

public class OriginDestination
{
	private RoadNetwork rn;
	private LinkedHashMap<Integer, LinkedList<ODPathCollection>> pathSet;
	private String trajFileName;
	private String linkFlowFileName;
	private LinkedHashMap<Integer, LinkedHashMap<String, Integer>> intervalLinkFlow;
	private String pathFlowReadFile;
	private LinkedHashMap<Integer, LinkedHashMap<LinkedList<Integer>, Integer>> estPathFlow;

	public OriginDestination(RoadNetwork rn, String trajFileName, String linkFlowFileName, String pathFlowReadFile)
	{
		super();
		this.rn = rn;
		this.trajFileName = trajFileName;
		this.linkFlowFileName = linkFlowFileName;
		this.pathFlowReadFile = pathFlowReadFile;
		pathSet = new LinkedHashMap<Integer, LinkedList<ODPathCollection>>();
		intervalLinkFlow = new LinkedHashMap<Integer, LinkedHashMap<String, Integer>>();
		estPathFlow = new LinkedHashMap<Integer, LinkedHashMap<LinkedList<Integer>, Integer>>();
	}

	public void InitInput()
	{
		try
		{
			InitPathByFile("SearchPaths_top5ExLink.txt");
			SplitLinkFlow();
		} catch (IOException | NumberFormatException | ParseException e)
		{
			e.printStackTrace();
		}
	}

	private void SplitLinkFlow() throws NumberFormatException, IOException, ParseException
	{
		File obLinkFlowFileName = new File(linkFlowFileName);
		if (!obLinkFlowFileName.exists())
		{
			System.out.println("Link Flow File does not exist!");
			return;
		}
		Hashtable<String, Long> totalLinkFlows = new Hashtable<String, Long>();
		InputStreamReader reader = new InputStreamReader(new FileInputStream(obLinkFlowFileName));
		BufferedReader br = new BufferedReader(reader);
		String line = "";
		while ((line = br.readLine()) != null)
		{
			String[] items = line.split("-");
			String keyStr = items[0] + "-" + items[1];
			long flowVal = Long.parseLong(items[2].substring(items[2].indexOf(":") + 1));
			totalLinkFlows.put(keyStr, flowVal);
		}
		br.close();
		reader.close();
		LinkedHashMap<Integer, LinkedList<ODPathCollection>> taxiPathSet = PathCompute();
		if (taxiPathSet == null)
			return;
		for (String odPair : totalLinkFlows.keySet())
		{
			int origNode_1 = Integer.parseInt(odPair.substring(odPair.indexOf("(") + 1, odPair.indexOf(",")));
			int destNode_1 = Integer.parseInt(odPair.substring(odPair.indexOf(",") + 1, odPair.indexOf(")")));
			int origNode_2 = Integer.parseInt(odPair.substring(odPair.lastIndexOf("(") + 1, odPair.lastIndexOf(",")));
			int destNode_2 = Integer.parseInt(odPair.substring(odPair.lastIndexOf(",") + 1, odPair.lastIndexOf(")")));
			long totalFlow = totalLinkFlows.get(odPair);
			LinkedHashMap<Integer, Integer> interTravel = new LinkedHashMap<Integer, Integer>();
			int travelSum = 0;
			for (Integer timeInter : taxiPathSet.keySet())
			{
				int interTravelNum = 0;
				LinkedList<ODPathCollection> pathCollect = taxiPathSet.get(timeInter);
				for (ODPathCollection odPathCol : pathCollect)
				{
					for (LinkedList<Integer> path : odPathCol.pathTravelNum.keySet())
					{
						boolean hasLink = false;
						for (int i = 0; i < path.size() - 1; i++)
						{
							if (path.get(i) == origNode_1 && path.get(i + 1) == destNode_1)
							{
								hasLink = true;
								break;
							}
							if (path.get(i) == origNode_2 && path.get(i + 1) == destNode_2)
							{
								hasLink = true;
								break;
							}
						}
						if (hasLink)
						{
							interTravelNum += odPathCol.pathTravelNum.get(path);
						}
					}
				}
				interTravel.put(timeInter, interTravelNum);
				travelSum += interTravelNum;
			}
			for (Integer timeInter : interTravel.keySet())
			{
				int travelNum = (int) (((float) interTravel.get(timeInter)) / travelSum * totalFlow);
				if (intervalLinkFlow.containsKey(timeInter))
				{
					intervalLinkFlow.get(timeInter).put(odPair, travelNum);
				} else
				{
					LinkedHashMap<String, Integer> linkFlowPart = new LinkedHashMap<String, Integer>();
					linkFlowPart.put(odPair, travelNum);
					intervalLinkFlow.put(timeInter, linkFlowPart);
				}
			}
		}
	}

	public LinkedHashMap<LinkedList<Integer>, Integer> PathODEstByLinkFlow(int timeInter, boolean fromSolFile, int startIter, int endIter) throws IOException
	{
		LinkedHashMap<String, Integer> detectLinkFlow = new LinkedHashMap<String, Integer>();
		for (String linkPair : intervalLinkFlow.get(timeInter).keySet())
		{
			int linkFlow = intervalLinkFlow.get(timeInter).get(linkPair);
			detectLinkFlow.put(linkPair, linkFlow);
		}
		LinkedHashMap<LinkedList<Integer>, Integer> pathVec = new LinkedHashMap<LinkedList<Integer>, Integer>();
		for (LinkedList<ODPathCollection> pathCollect : pathSet.values())
		{
			for (ODPathCollection odPath : pathCollect)
			{
				for (LinkedList<Integer> path : odPath.pathTravelNum.keySet())
				{
					boolean hasThePath = false;
					for (LinkedList<Integer> pathCand : pathVec.keySet())
					{
						if (pathCand.size() != path.size())
							continue;
						boolean nodeMatch = true;
						for (int i = 0; i < pathCand.size(); i++)
						{
							if (pathCand.get(i) != path.get(i))
							{
								nodeMatch = false;
								break;
							}
						}
						if (nodeMatch)
						{
							int newTravNum = pathVec.get(pathCand) + odPath.pathTravelNum.get(path);
							pathVec.put(pathCand, newTravNum);
							hasThePath = true;
							break;
						}
					}
					if (!hasThePath)
					{
						pathVec.put(path, odPath.pathTravelNum.get(path));
					}
				}
			}
		}
		LinkedHashMap<String, LinkedList<Integer>> coeffs = new LinkedHashMap<String, LinkedList<Integer>>();
		for (String nodePair : detectLinkFlow.keySet())
		{
			int linkStart = Integer.parseInt(nodePair.substring(nodePair.indexOf("(") + 1, nodePair.indexOf(",")));
			int linkEnd = Integer.parseInt(nodePair.substring(nodePair.indexOf(",") + 1, nodePair.indexOf(")")));
			LinkedList<Integer> indexOfOnes = new LinkedList<Integer>();
			int colIndex = 1;
			for (LinkedList<Integer> path : pathVec.keySet())
			{
				boolean hasTheLink = false;
				for (int i = 0; i < path.size() - 1; i++)
				{
					if (path.get(i) == linkStart && path.get(i + 1) == linkEnd)
					{
						hasTheLink = true;
					}
					if (path.get(i) == linkEnd && path.get(i + 1) == linkStart)
					{
						hasTheLink = true;
					}
				}
				if (hasTheLink)
				{
					indexOfOnes.add(colIndex);
				}
				colIndex++;
			}
			coeffs.put(nodePair, indexOfOnes);
		}
		PathODEstByGD(pathVec, coeffs, detectLinkFlow, startIter, endIter);
		System.out.println(timeInter + " : relativeError = " + Evaluation(detectLinkFlow, pathVec));
		return pathVec;
	}

	private void PathODEstByGD(LinkedHashMap<LinkedList<Integer>, Integer> pathVec, LinkedHashMap<String, LinkedList<Integer>> coeffs,
					LinkedHashMap<String, Integer> detectLinkFlow, int startIter, int endIter)
	{
		DecimalFormat df = new DecimalFormat(",###");
		long error = ComputeODError(detectLinkFlow, pathVec, coeffs);
		System.out.println("Init error = " + df.format(error));
		for (int i = startIter; i <= endIter; i++)
		{
			LinkedHashMap<LinkedList<Integer>, Long> gradient = new LinkedHashMap<LinkedList<Integer>, Long>();
			for (String nodePair : detectLinkFlow.keySet())
			{
				long detectFlow = detectLinkFlow.get(nodePair);
				long rowDev = 0;
				LinkedList<Integer> coefRow = coeffs.get(nodePair);
				if (coefRow.size() == 0)
					continue;
				int index = 1;
				for (LinkedList<Integer> path : pathVec.keySet())
				{
					if (coefRow.contains(index))
					{
						rowDev += pathVec.get(path);
					}
					index++;
				}
				rowDev = rowDev - detectFlow;
				index = 1;
				for (LinkedList<Integer> path : pathVec.keySet())
				{
					long regItem = 0;
					if (coefRow.contains(index))
					{
						if (gradient.containsKey(path))
						{
							long newGradient = gradient.get(path) + rowDev + regItem;
							gradient.put(path, newGradient);
						} else
						{
							gradient.put(path, rowDev + regItem);
						}
					}
					index++;
				}
			}
			float dynStepLen = (float) 0.0001;
			if (2000 <= i && i < 2500)
			{
				dynStepLen = (float) 0.001;
			} else if (2500 <= i)
			{
				dynStepLen = (float) 0.002;
			}
			for (LinkedList<Integer> path : pathVec.keySet())
			{
				if (gradient.containsKey(path))
				{
					int newTraNum = Math.round(pathVec.get(path) - dynStepLen * gradient.get(path));
					if (newTraNum < 0)
						newTraNum = 0;
					pathVec.put(path, newTraNum);
				}
			}
			if (i % 100 == 0)
			{
				error = ComputeODError(detectLinkFlow, pathVec, coeffs);
				System.out.println("Iter: " + i + "; error = " + df.format(error) + "; stepLen = " + dynStepLen);
			}
		}
	}

	private float Evaluation(LinkedHashMap<String, Integer> detectLinkFlow, LinkedHashMap<LinkedList<Integer>, Integer> pathVec)
	{
		float relativeError = 0;
		for (String nodePair : detectLinkFlow.keySet())
		{
			int link_StartNode = Integer.parseInt(nodePair.substring(nodePair.indexOf("(") + 1, nodePair.indexOf(",")));
			int link_EndNode = Integer.parseInt(nodePair.substring(nodePair.indexOf(",") + 1, nodePair.indexOf(")")));
			long realLinkFlow = detectLinkFlow.get(nodePair);
			long reconsLinkFlow = 0;
			for (LinkedList<Integer> path : pathVec.keySet())
			{
				for (int i = 0; i < path.size() - 1; i++)
				{
					if (path.get(i) == link_StartNode && path.get(i + 1) == link_EndNode)
					{
						reconsLinkFlow += pathVec.get(path);
					}
					if (path.get(i) == link_EndNode && path.get(i + 1) == link_StartNode)
					{
						reconsLinkFlow += pathVec.get(path);
					}
				}
			}
			if (realLinkFlow != 0)
			{
				relativeError += ((float) (Math.abs(reconsLinkFlow - realLinkFlow))) / realLinkFlow;
			}
		}
		return relativeError;
	}

	private long ComputeODError(LinkedHashMap<String, Integer> detectLinkFlow, LinkedHashMap<LinkedList<Integer>, Integer> pathVec,
					LinkedHashMap<String, LinkedList<Integer>> coeffs)
	{
		long error = 0;
		for (String nodePair : detectLinkFlow.keySet())
		{
			long detectFlow = detectLinkFlow.get(nodePair);
			long rowDev = 0;
			LinkedList<Integer> coefRow = coeffs.get(nodePair);
			int index = 1;
			for (LinkedList<Integer> path : pathVec.keySet())
			{
				if (coefRow.contains(index))
				{
					rowDev += pathVec.get(path);
				}
				index++;
			}
			if (coefRow.size() == 0)
				continue;
			rowDev = rowDev - detectFlow;
			error += Math.pow(rowDev, 2);
		}
		return Math.round(0.5 * error);
	}

	private LinkedHashMap<Integer, LinkedList<ODPathCollection>> PathCompute() throws IOException, ParseException
	{
		LinkedHashMap<Integer, LinkedList<ODPathCollection>> taxiPathSet = new LinkedHashMap<Integer, LinkedList<ODPathCollection>>();
		File traFileName = new File(trajFileName);
		if (!traFileName.exists())
		{
			System.out.println("Trajectory File does not exist!");
			return null;
		}
		SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		InputStreamReader reader = new InputStreamReader(new FileInputStream(traFileName));
		BufferedReader br = new BufferedReader(reader);
		String line = "";
		while ((line = br.readLine()) != null)
		{
			String[] items = line.split("-");
			Date startDate = format.parse(items[0]);
			float speed = Float.parseFloat(items[2]);
			int origNode = Integer.parseInt(items[3].substring(items[3].indexOf("(") + 1, items[3].indexOf(",")));
			int destNode = Integer.parseInt(items[items.length - 1].substring(items[items.length - 1].indexOf(",") + 1, items[items.length - 1].indexOf(")")));
			int hour = startDate.getHours();
			if (hour < 6 || hour >= 24)
				continue;
			int min = startDate.getMinutes();
			if (min >= 0 && min < 15)
			{
				min = 0;
			} else if (min >= 15 && min < 30)
			{
				min = 15;
			} else if (min >= 30 && min < 45)
			{
				min = 30;
			} else if (min >= 45 && min < 60)
			{
				min = 45;
			}
			Date timeInterStart = format.parse("2014/08/01 " + hour + ":" + min + ":00");
			int endMin = min + 15;
			int endHour = hour;
			if (endMin == 60)
			{
				endHour++;
				endMin = 0;
			}
			Date timeInterEnd = format.parse("2014/08/01 " + endHour + ":" + endMin + ":00");
			int timeInterval = (hour - 6) * 4 + min / 15;
			if (taxiPathSet.containsKey(timeInterval))
			{
				LinkedList<ODPathCollection> pathList = taxiPathSet.get(timeInterval);
				boolean hasODCollect = false;
				for (ODPathCollection odPath : pathList)
				{
					if (odPath.startDate.equals(timeInterStart) && odPath.endDate.equals(timeInterEnd) && odPath.origNode == origNode
									&& odPath.destNode == destNode)
					{
						boolean hasPath = false;
						for (LinkedList<Integer> parPath : odPath.pathTable.keySet())
						{
							if (parPath.size() != items.length - 3)
								continue;
							boolean nodesMatch = true;
							for (int i = 0; i < parPath.size() - 1; i++)
							{
								int nodeID = Integer.parseInt(items[i + 3].substring(items[i + 3].indexOf("(") + 1, items[i + 3].indexOf(",")));
								if (parPath.get(i) != nodeID)
								{
									nodesMatch = false;
									break;
								}
							}
							if (parPath.get(parPath.size() - 1) != Integer.parseInt(items[items.length - 1]
											.substring(items[items.length - 1].lastIndexOf(",") + 1, items[items.length - 1].lastIndexOf(")"))))
							{
								nodesMatch = false;
							}
							if (nodesMatch)
							{
								float newAverSpeed = (float) ((odPath.pathTable.get(parPath) + speed) * 0.5);
								odPath.pathTable.put(parPath, newAverSpeed);
								int newTravelNum = odPath.pathTravelNum.get(parPath) + 1;
								odPath.pathTravelNum.put(parPath, newTravelNum);
								odPath.odDemand++;
								hasPath = true;
								break;
							}
						}
						if (!hasPath)
						{
							LinkedList<Integer> newPath = new LinkedList<Integer>();
							for (int i = 3; i < items.length; i++)
							{
								int nodeID = Integer.parseInt(items[i].substring(items[i].indexOf("(") + 1, items[i].indexOf(",")));
								newPath.add(nodeID);
							}
							newPath.add(Integer.parseInt(items[items.length - 1].substring(items[items.length - 1].lastIndexOf(",") + 1,
											items[items.length - 1].lastIndexOf(")"))));
							odPath.pathTable.put(newPath, speed);
							odPath.pathTravelNum.put(newPath, 1);
							odPath.odDemand++;
						}
						hasODCollect = true;
						break;
					}
				}
				if (!hasODCollect)
				{
					ODPathCollection newODCollect = new ODPathCollection(origNode, destNode, timeInterStart, timeInterEnd);
					LinkedList<Integer> newPath = new LinkedList<Integer>();
					for (int i = 3; i < items.length; i++)
					{
						int nodeID = Integer.parseInt(items[i].substring(items[i].indexOf("(") + 1, items[i].indexOf(",")));
						newPath.add(nodeID);
					}
					newPath.add(Integer.parseInt(
									items[items.length - 1].substring(items[items.length - 1].lastIndexOf(",") + 1, items[items.length - 1].lastIndexOf(")"))));
					newODCollect.pathTable.put(newPath, speed);
					newODCollect.pathTravelNum.put(newPath, 1);
					newODCollect.odDemand++;
					pathList.add(newODCollect);
				}
			} else
			{
				ODPathCollection newODCollect = new ODPathCollection(origNode, destNode, timeInterStart, timeInterEnd);
				LinkedList<Integer> newPath = new LinkedList<Integer>();
				for (int i = 3; i < items.length; i++)
				{
					int nodeID = Integer.parseInt(items[i].substring(items[i].indexOf("(") + 1, items[i].indexOf(",")));
					newPath.add(nodeID);
				}
				newPath.add(Integer.parseInt(
								items[items.length - 1].substring(items[items.length - 1].lastIndexOf(",") + 1, items[items.length - 1].lastIndexOf(")"))));
				newODCollect.pathTable.put(newPath, speed);
				newODCollect.pathTravelNum.put(newPath, 1);
				newODCollect.odDemand++;
				LinkedList<ODPathCollection> pathList = new LinkedList<ODPathCollection>();
				pathList.add(newODCollect);
				taxiPathSet.put(timeInterval, pathList);
			}
		}
		br.close();
		reader.close();
		return taxiPathSet;
	}

	private void InitPathByFile(String fileName) throws IOException
	{
		File file = new File(fileName);
		if (!file.exists())
		{
			System.out.println(fileName + " does not exit!!!");
			return;
		}
		InputStreamReader reader = new InputStreamReader(new FileInputStream(fileName));
		BufferedReader br = new BufferedReader(reader);
		String line = "";
		while ((line = br.readLine()) != null)
		{
			String[] items = line.split("-");
			String origNodeStr = items[3].substring(items[3].indexOf("(") + 1, items[3].indexOf(","));
			int origNode = Integer.parseInt(origNodeStr);
			String destNodeStr = items[items.length - 1].substring(items[items.length - 1].indexOf(",") + 1, items[items.length - 1].indexOf(")"));
			int destNode = Integer.parseInt(destNodeStr);
			LinkedList<Integer> parPath = new LinkedList<Integer>();
			for (int i = 3; i < items.length; i++)
			{
				String nodeStr = items[i].substring(items[i].indexOf("(") + 1, items[i].indexOf(","));
				int nodeID = Integer.parseInt(nodeStr);
				parPath.add(nodeID);
			}
			parPath.add(destNode);
			for (int timeInter = 0; timeInter < 72; timeInter++)
			{
				if (pathSet.containsKey(timeInter))
				{
					LinkedList<ODPathCollection> odPathCollect = pathSet.get(timeInter);
					ODPathCollection odPath = null;
					for (ODPathCollection parOD : odPathCollect)
					{
						if (parOD.origNode == origNode && parOD.destNode == destNode)
						{
							odPath = parOD;
							break;
						}
					}
					if (odPath == null)
					{
						int hour = timeInter / 4 + 6;
						int min = (timeInter % 4) * 15;
						Date startDate = new Date(2014, 8, 1, hour, min, 0);
						min += 15;
						if (min == 60)
						{
							hour++;
							min = 0;
						}
						Date endDate = new Date(2014, 8, 1, hour, min, 0);
						odPath = new ODPathCollection(origNode, destNode, startDate, endDate);
						odPath.pathTravelNum.put(parPath, 100);
						odPathCollect.add(odPath);
					} else
					{
						odPath.pathTravelNum.put(parPath, 100);
					}
				} else
				{
					LinkedList<ODPathCollection> odPathCollect = new LinkedList<ODPathCollection>();
					int hour = timeInter / 4 + 6;
					int min = (timeInter % 4) * 15;
					Date startDate = new Date(2014, 8, 1, hour, min, 0);
					min += 15;
					if (min == 60)
					{
						hour++;
						min = 0;
					}
					Date endDate = new Date(2014, 8, 1, hour, min, 0);
					ODPathCollection odPath = new ODPathCollection(origNode, destNode, startDate, endDate);
					odPath.pathTravelNum.put(parPath, 100);
					odPathCollect.add(odPath);
					pathSet.put(timeInter, odPathCollect);
				}
			}
		}
		br.close();
		reader.close();
	}
}

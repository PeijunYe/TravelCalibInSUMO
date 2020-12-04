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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

public class Evaluation
{
	private LinkedHashMap<Integer, LinkedHashMap<String, Integer>> intervalLinkFlow;
	private String trajFileName;
	private String linkFlowFileName;
	private DecimalFormat df;

	public Evaluation(String trajFileName, String linkFlowFileName)
	{
		super();
		intervalLinkFlow = new LinkedHashMap<Integer, LinkedHashMap<String, Integer>>();
		this.trajFileName = trajFileName;
		this.linkFlowFileName = linkFlowFileName;
		df = new DecimalFormat("0.0000");
	}

	public void Eval()
	{
		try
		{
			SplitLinkFlow();
			LinkedHashMap<Integer, LinkedHashMap<String, Integer>> detectedLinkFlow = new LinkedHashMap<Integer, LinkedHashMap<String, Integer>>();
			SAXReader reader = new SAXReader();
			Document document = reader.read(new File("DetectedLinkFlow.xml"));
			Element rootElement = document.getRootElement();
			Iterator<Element> iterator = rootElement.elementIterator();
			while (iterator.hasNext())
			{
				Element stu = (Element) iterator.next();
				List<Attribute> attributes = stu.attributes();
				String detectorName = "";
				int vehNum = -1;
				int startTime = -1;
				int endTime = -1;
				for (Attribute attribute : attributes)
				{
					String attrName = attribute.getName();
					if (attrName == "id")
						detectorName = attribute.getStringValue();
					if (attrName == "begin")
						startTime = (int) (Float.parseFloat(attribute.getStringValue()));
					if (attrName == "end")
						endTime = (int) (Float.parseFloat(attribute.getStringValue()));
					if (attrName == "nVehEntered")
						vehNum = Integer.parseInt(attribute.getStringValue());
				}
				if (endTime - startTime != 900)
				{
					System.out.println("endTime - startTime = " + (endTime - startTime));
				}
				int timeInter = startTime / 900;
				if (detectedLinkFlow.containsKey(timeInter))
				{
					LinkedHashMap<String, Integer> linkSet = detectedLinkFlow.get(timeInter);
					String[] item = detectorName.split("_");
					int origNode = Integer.parseInt(item[1].split("-")[0]);
					int endNode = Integer.parseInt(item[1].split("-")[1]);
					boolean hasLink = false;
					for (String odPair : linkSet.keySet())
					{
						if (origNode == Integer.parseInt(odPair.split("-")[0]) && endNode == Integer.parseInt(odPair.split("-")[1]))
						{
							int flow = linkSet.get(odPair);
							int newFlow = flow + vehNum;
							linkSet.put(odPair, newFlow);
							hasLink = true;
							break;
						}
					}
					if (!hasLink)
					{
						linkSet.put(item[1], vehNum);
					}
				} else
				{
					LinkedHashMap<String, Integer> linkSet = new LinkedHashMap<String, Integer>();
					String[] item = detectorName.split("_");
					linkSet.put(item[1], vehNum);
					detectedLinkFlow.put(timeInter, linkSet);
				}
			}
			for (int i = 0; i < 72; i++)
			{
				LinkedHashMap<String, Integer> realLinkSet = intervalLinkFlow.get(i);
				for (String odPair : realLinkSet.keySet())
				{
					String partODPair = odPair.split("-")[0];
					String origNodeStr = partODPair.substring(partODPair.indexOf("(") + 1, partODPair.indexOf(","));
					int origNode = Integer.parseInt(origNodeStr);
					String endNodeStr = partODPair.substring(partODPair.indexOf(",") + 1, partODPair.indexOf(")"));
					int endNode = Integer.parseInt(endNodeStr);
					int realFlow = realLinkSet.get(odPair);
					int synFlow = 0;
					LinkedHashMap<String, Integer> synLinkSet = detectedLinkFlow.get(i);
					for (String synODPair : synLinkSet.keySet())
					{
						int synOrigNode = Integer.parseInt(synODPair.split("-")[0]);
						int synEndNode = Integer.parseInt(synODPair.split("-")[1]);
						if (origNode == synOrigNode && endNode == synEndNode)
						{
							synFlow += synLinkSet.get(synODPair);
						}
						if (endNode == synOrigNode && origNode == synEndNode)
						{
							synFlow += synLinkSet.get(synODPair);
						}
					}
					if (realFlow > 0)
					{
						synFlow = synFlow * 10000;
						float error = (float) Math.abs(realFlow - synFlow) / realFlow;
						System.out.println(i + "," + realFlow + "," + synFlow + "," + df.format(error));
					} else
					{
						System.out.println(i + "," + realFlow + "," + synFlow + "," + df.format(0.0000));
					}
				}
			}
		} catch (NumberFormatException | IOException | ParseException | DocumentException e)
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
}

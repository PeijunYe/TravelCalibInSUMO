import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.LinkedList;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class DemandGenerator implements Runnable
{
	private int timeInter;
	private int scaleFactor;
	private boolean fromFlag;
	private LinkedHashMap<LinkedList<Integer>, Integer> pathVec;
	private LinkedHashMap<Integer, LinkedHashMap<String, Integer>> intervalLinkFlow;
	private LinkedHashMap<Integer, LinkedList<ODPathCollection>> pathSet;

	public DemandGenerator(int timeInter, int scaleFactor, boolean fromFlag, LinkedHashMap<Integer, LinkedHashMap<String, Integer>> intervalLinkFlow,
					LinkedHashMap<Integer, LinkedList<ODPathCollection>> pathSet)
	{
		super();
		this.timeInter = timeInter;
		this.scaleFactor = scaleFactor;
		this.fromFlag = fromFlag;
		this.intervalLinkFlow = intervalLinkFlow;
		this.pathSet = pathSet;
	}

	@Override
	public void run()
	{
		DecimalFormat df = new DecimalFormat("0.00");
		try
		{
			if (!fromFlag)
			{
				this.pathVec = PathODEstByLinkFlow(timeInter, 1, 5000);
			} else
			{
				this.pathVec = ReadPathFlowFromFile();
			}
			SAXTransformerFactory tff = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
			TransformerHandler handler;
			handler = tff.newTransformerHandler();
			Transformer tr = handler.getTransformer();
			tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			tr.setOutputProperty(OutputKeys.INDENT, "yes");
			File f = new File("ODDemand/AbCDBy" + scaleFactor + "_" + timeInter + ".rou.xml");
			if (!f.exists())
				f.createNewFile();
			Result result = new StreamResult(new FileOutputStream(f));
			handler.setResult(result);
			handler.startDocument();
			AttributesImpl attr = new AttributesImpl();
			handler.startElement("", "", "routes", attr);
			attr.clear();
			long vehID = 1;
			int totalDemand = 0;
			for (Integer demand : pathVec.values())
			{
				totalDemand += demand;
			}
			int index = 0;
			totalDemand = (int) (((float) totalDemand) / scaleFactor);
			int interval = totalDemand / (10 * 60);
			if (interval <= 0)
				interval = 1;
			for (LinkedList<Integer> parPath : pathVec.keySet())
			{
				int trafficDemand = pathVec.get(parPath);
				if (trafficDemand <= 0)
					continue;
				trafficDemand = (int) (((float) trafficDemand) / scaleFactor);
				for (int i = 0; i < trafficDemand; i++)
				{
					int offset = index / interval + 1;
					int departTime = timeInter * 15 * 60 + offset;
					LinkedList<Integer> filteredPath = new LinkedList<Integer>();
					for (int j = 0; j < parPath.size(); j++)
					{
						if (parPath.get(j) != 52)
							filteredPath.add(parPath.get(j));
					}
					if (filteredPath.size() <= 2)
						continue;
					if (filteredPath.get(0) == filteredPath.get(1))
					{
						filteredPath.removeFirst();
					}
					if (filteredPath.size() <= 2)
						continue;
					String pathStr = filteredPath.get(0) + "-" + filteredPath.get(1);
					for (int j = 1; j < filteredPath.size() - 1; j++)
					{
						pathStr += " " + filteredPath.get(j) + "-" + filteredPath.get(j + 1);
					}
					if (pathStr.contains("27-33 33-27"))
					{
						pathStr = pathStr.replace("27-33 33-27", "27-33 33-40");
					}
					if (pathStr.contains("40-33 33-40"))
					{
						pathStr = pathStr.replace("40-33 33-40", "40-33 33-27");
					}
					if (pathStr.contains("27-26 26-27"))
					{
						pathStr = pathStr.replace("27-26 26-27", "27-26 26-25");
					}
					if (pathStr.contains("25-26 26-25"))
					{
						pathStr = pathStr.replace("25-26 26-25", "25-26 26-27");
					}
					if (pathStr.startsWith("56-57 57-56"))
					{
						pathStr = pathStr.replace("56-57 57-56", "57-56");
					}
					attr.clear();
					attr.addAttribute("", "", "id", "", String.valueOf(vehID));
					attr.addAttribute("", "", "depart", "", df.format(departTime));
					handler.startElement("", "", "vehicle", attr);
					vehID++;
					attr.clear();
					attr.addAttribute("", "", "edges", "", pathStr);
					handler.startElement("", "", "route", attr);
					handler.endElement("", "", "route");
					handler.endElement("", "", "vehicle");
					index++;
				}
			}
			handler.endElement("", "", "routes");
			handler.endDocument();
			System.out.println("timeInter = " + timeInter + " : " + totalDemand * scaleFactor + " / " + scaleFactor + " complete...");
		} catch (TransformerConfigurationException | SAXException | IOException e)
		{
			e.printStackTrace();
		}
	}

	private LinkedHashMap<LinkedList<Integer>, Integer> ReadPathFlowFromFile() throws NumberFormatException, IOException
	{
		String fileName = "AggrDemand/PathDemand_" + timeInter + ".txt";
		File file = new File(fileName);
		if (!file.exists())
		{
			System.out.println(fileName + " does not exit!!!");
			return null;
		}
		LinkedHashMap<LinkedList<Integer>, Integer> pathFlowSet = new LinkedHashMap<LinkedList<Integer>, Integer>();
		InputStreamReader reader = new InputStreamReader(new FileInputStream(fileName));
		BufferedReader br = new BufferedReader(reader);
		String line = "";
		while ((line = br.readLine()) != null)
		{
			String[] pathFlow = line.split(":");
			String[] items = pathFlow[0].split("-");
			LinkedList<Integer> path = new LinkedList<Integer>();
			int flowNum = Integer.parseInt(pathFlow[1]);
			for (int j = 0; j < items.length; j++)
			{
				int nodeID = Integer.parseInt(items[j]);
				path.add(nodeID);
			}
			pathFlowSet.put(path, flowNum);
		}
		br.close();
		reader.close();
		return pathFlowSet;
	}

	public LinkedHashMap<LinkedList<Integer>, Integer> PathODEstByLinkFlow(int timeInter, int startIter, int endIter) throws IOException
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
					if (coefRow.contains(index))
					{
						if (gradient.containsKey(path))
						{
							long newGradient = gradient.get(path) + rowDev;
							gradient.put(path, newGradient);
						} else
						{
							gradient.put(path, rowDev);
						}
					}
					index++;
				}
			}
			float dynStepLen = (float) 0.0001;
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
			if (i % 1000 == 0)
			{
				error = ComputeODError(detectLinkFlow, pathVec, coeffs);
				System.out.println("Iter: " + i + "; error = " + df.format(error) + "; stepLen = " + dynStepLen);
			}
		}
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
}

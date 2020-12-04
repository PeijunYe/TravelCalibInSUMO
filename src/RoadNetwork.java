import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

public class RoadNetwork
{
	private String linkXMLFileName;
	private String linkNameFile;
	private int[][] rnAdjaMatrix;
	private LinkedList<Integer> nodeIDSequence;
	private Hashtable<String, ArrayList<String>> linkNames;

	public RoadNetwork(String linkXMLFileName, String linkNameFile)
	{
		super();
		this.linkXMLFileName = linkXMLFileName;
		this.linkNameFile = linkNameFile;
		nodeIDSequence = new LinkedList<Integer>();
		linkNames = new Hashtable<String, ArrayList<String>>();
	}

	public void Init()
	{
		File filename = new File(linkXMLFileName);
		if (!filename.exists())
		{
			System.out.println("Link XML File does not exist!");
			return;
		}
		SAXReader reader = new SAXReader();
		Document document;
		Hashtable<String, Integer> edges = new Hashtable<String, Integer>();
		try
		{
			document = reader.read(linkXMLFileName);
			Element root = document.getRootElement();
			for (Iterator<Element> it = root.elementIterator("edge"); it.hasNext();)
			{
				Element edge = it.next();
				String edgeID = edge.attributeValue("id");
				int laneNum = Integer.parseInt(edge.attributeValue("numLanes"));
				float length = Float.parseFloat(edge.attributeValue("length"));
				int capacity = (int) (laneNum * length);
				edges.put(edgeID, capacity);
				int origID = Integer.parseInt(edge.attributeValue("from"));
				int destID = Integer.parseInt(edge.attributeValue("to"));
				if (!nodeIDSequence.contains(origID))
					nodeIDSequence.addLast(origID);
				if (!nodeIDSequence.contains(destID))
					nodeIDSequence.addLast(destID);
			}
		} catch (DocumentException e1)
		{
			e1.printStackTrace();
		}
		rnAdjaMatrix = new int[nodeIDSequence.size()][nodeIDSequence.size()];
		for (int i = 0; i < rnAdjaMatrix.length; i++)
		{
			for (int j = 0; j < rnAdjaMatrix.length; j++)
			{
				rnAdjaMatrix[i][j] = 0;
			}
		}
		for (String nodePair : edges.keySet())
		{
			int startNode = Integer.parseInt(nodePair.substring(0, nodePair.indexOf("-")));
			int endNode = Integer.parseInt(nodePair.substring(nodePair.indexOf("-") + 1));
			int startIndex = CheckRowColIndexByNodeID(startNode);
			int endIndex = CheckRowColIndexByNodeID(endNode);
			rnAdjaMatrix[startIndex][endIndex] = edges.get(nodePair);
		}
		InitPathByTrajecFile();
	}

	private void InitPathByTrajecFile()
	{
		File linkFile = new File(linkNameFile);
		if (!linkFile.exists())
		{
			System.out.println("Link Name File does not exist!");
			return;
		}
		try
		{
			InputStreamReader nameReader = new InputStreamReader(new FileInputStream(linkFile));
			BufferedReader br = new BufferedReader(nameReader);
			String line = "";
			while ((line = br.readLine()) != null)
			{
				String nodePair = line.substring(0, line.lastIndexOf(","));
				String linkNameSeg = line.substring(line.lastIndexOf(",") + 1);
				String[] names = linkNameSeg.split(" ");
				ArrayList<String> nameList = new ArrayList<String>();
				for (int i = 0; i < names.length; i++)
					nameList.add(names[i]);
				linkNames.put(nodePair, nameList);
			}
			br.close();
			nameReader.close();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	public int CheckRowColIndexByNodeID(int nodeID)
	{
		int index = -1;
		for (int i = 0; i < nodeIDSequence.size(); i++)
		{
			if (nodeIDSequence.get(i) == nodeID)
			{
				index = i;
				break;
			}
		}
		return index;
	}

	public HashMap<Integer, Integer> CheckLinkByName(String linkName)
	{
		HashMap<Integer, Integer> nodePairs = new HashMap<Integer, Integer>();
		for (String parNodePair : linkNames.keySet())
		{
			ArrayList<String> nameList = linkNames.get(parNodePair);
			for (String name : nameList)
			{
				if (name.equals(linkName))
				{
					int startNode = Integer.parseInt(parNodePair.substring(parNodePair.indexOf("(") + 1, parNodePair.indexOf(",")));
					int endNode = Integer.parseInt(parNodePair.substring(parNodePair.indexOf(",") + 1, parNodePair.indexOf(")")));
					nodePairs.put(startNode, endNode);
					break;
				}
			}
		}
		return nodePairs;
	}

	public int[][] getRnAdjaMatrix()
	{
		return rnAdjaMatrix;
	}

	public LinkedList<Integer> getNodeIDSequence()
	{
		return nodeIDSequence;
	}
}

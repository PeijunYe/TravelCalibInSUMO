
import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedList;

public class ODPathCollection
{
	public int origNode;
	public int destNode;
	public Date startDate;
	public Date endDate;
	public int odDemand;
	public Hashtable<LinkedList<Integer>, Float> pathTable;
	public Hashtable<LinkedList<Integer>, Integer> pathTravelNum;

	public ODPathCollection(int origNode, int destNode, Date startDate, Date endDate)
	{
		super();
		this.origNode = origNode;
		this.destNode = destNode;
		this.startDate = startDate;
		this.endDate = endDate;
		this.odDemand = 0;
		pathTable = new Hashtable<LinkedList<Integer>, Float>();
		pathTravelNum = new Hashtable<LinkedList<Integer>, Integer>();
	}
}

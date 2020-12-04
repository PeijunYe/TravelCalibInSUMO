import java.util.LinkedList;

public class Route
{
	public LinkedList<Integer> path;
	public int departTime;

	public Route(LinkedList<Integer> path, int departTime)
	{
		super( );
		this.path = path;
		this.departTime = departTime;
	}
}

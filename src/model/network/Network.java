package model.network;

import java.util.HashMap;

import model.graph.Edge;
import model.graph.Graph;
import model.graph.Key;

/**
 * The Network class implements a network.
 */
public class Network extends Graph {
	
	private HashMap<Integer, Key> vertexKeys;
	
	protected static Key DEMAND_KEY;
	protected static Key LOWER_BOUND_KEY;
	protected static Key CAPACITY_KEY;
	protected static Key COST_KEY;
	protected static Key FLOW_KEY;

	public Network() {
		super();
		setDirected(true);
		setName("Network");
		vertexKeys = new HashMap<Integer, Key>();
		if (DEMAND_KEY == null) {
			DEMAND_KEY      = addVertexData("Demand");
			LOWER_BOUND_KEY = addEdgeData("Lower bound of capacity");
			CAPACITY_KEY    = addEdgeData("Capacity");
			COST_KEY        = addEdgeData("Cost");
			FLOW_KEY        = addEdgeData("Flow");
		}
	}
	
	public void addVertex(NetworkVertex v, int id) {
		super.addVertex(v);
		vertexKeys.put(id, v.getKey());
	}
	
	public NetworkVertex getVertex(int id) {
		return (NetworkVertex) getVertex(vertexKeys.get(id));
	}
	
	public void addEdge(NetworkEdge e) {
		super.addEdge(e);
	}
	
	public long computeTotalCost() {
		long cost = 0;
		for (Edge edge : getEdges()) {
			NetworkEdge e = (NetworkEdge) edge;
			cost += e.getCost()*e.getFlow();
		}
		return cost;
	}
	
	public String toString() {
		String str = super.toString() + "\n";
		str += "Cost: " + computeTotalCost();
		return str;
	}
	
}
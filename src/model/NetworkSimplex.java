package model;

import java.util.Arrays;
import java.util.HashMap;

import model.graph.Data;
import model.graph.Edge;
import model.graph.Key;
import model.graph.Vertex;
import model.network.Network;
import model.network.NetworkEdge;
import model.network.NetworkReader;
import model.network.NetworkReaderException;
import model.network.NetworkSolutionWriter;
import model.network.NetworkVertex;

public class NetworkSimplex {
	
	private static Network network;
	
	private static HashMap<Key, NetworkEdge> lEdges;
	
	private static HashMap<Key, NetworkEdge> uEdges;
	
	private static NetworkEdge[] tree;
	
	/**
	 * Predecessor index in the tree
	 */
	private static int[] p;
	
	/**
	 * Depth index in the tree
	 */
	private static int[] d;
	
	/**
	 * Successor index in the tree based on depth first search traversion
	 */
	private static int[] s;
	
	private static Key vertexPriceDataKey;
	
	private static Key reducedCostDataKey;
	
	private static boolean inDebugMode;
	
	public static void findMinCostFlow(Network network) {
		
		inDebugMode = true;
		
		NetworkSimplex.network = network;
		int n = network.getNumberOfVertices();
		
		// Compute M
		long bigM = 1;
		long maxCost = 0;
		for (Edge edge : network.getEdges()) {
			NetworkEdge e = (NetworkEdge) edge;
			long cost = Math.abs(e.getCost());
			if (maxCost < cost) {
				maxCost = cost;
			}
		}
		bigM = 1 + Math.round(0.5*n*maxCost);
		
		// Add a new vertex k to the network
		NetworkVertex k = new NetworkVertex();
		k.setName("0");
		k.setDemand(0L);
		network.addVertex(k, 0); // we give k the id 0
		n = n+1;
		for (Vertex vertex : network.getVertices()) {
			NetworkVertex v = (NetworkVertex) vertex;
			if (v != k) {
				// Compute net demand (Nettobedarf: Mindestbedarf - Mindestlieferung)
				// b'(v) = b(v) + l(delta_p(v)) - l(delta_m(v))
				// delta_p(v) := {outgoing edges of v}
				// delta_m(v) := {ingoing edges of v}
				long b = v.getDemand();
				long nb = b;
				for (Edge edge : v.getOutgoingEdges()) {
					NetworkEdge e = (NetworkEdge) edge;
					nb += e.getLowerBound();
				}
				for (Edge edge : v.getIngoingEdges()) {
					NetworkEdge e = (NetworkEdge) edge;
					nb -= e.getLowerBound();
				}
				// Depends on the net demand,
				// add a new edge (v,k) or (k,v) to the network
				NetworkEdge e;
				if (nb < 0) {
					e = new NetworkEdge(v,k);
				} else {
					e = new NetworkEdge(k,v);
				}
				e.setLowerBound(0L);
				e.setCapacity(Long.MAX_VALUE);
				e.setCost(bigM);
				network.addEdge(e);
			}
		}
		
		// Set T, L, U, p, d, s
		lEdges = new HashMap<Key, NetworkEdge>();
		uEdges = new HashMap<Key, NetworkEdge>();
		tree = new NetworkEdge[n];
		p = new int[n];
		d = new int[n];
		/*
		 * depth first search traversal at first:
		 * 0-1-2-3-4-...-0
		 * for every pair -i-j- above we set s(i) := j
		 */
		s = new int[n];
		for (Edge edge : network.getEdges()) {
			NetworkEdge e = (NetworkEdge) edge;
			if (e.getTail() == k || e.getHead() == k) {
				// let v be the vertex not equals to k
				Vertex v = e.getTail();
				if (v == k) {
					v = e.getHead();					
				}
				int i = Integer.parseInt(v.getName());
				tree[i] = e; // e is the edge between i and p[i]
				p[i] = 0;
				d[i] = 1;
				s[i] = i+1;
			} else {
				lEdges.put(e.getKey(), e);
			}
		}
		s[0] = 1;
		s[n-1] = 0;
		if (inDebugMode) {
			printTreeAndLowerAndUpperEdges();
		}
		
		// Set the flow x
		for (NetworkEdge e : lEdges.values()) {
			e.setFlow(e.getLowerBound());
		}
		for (int i=1; i<tree.length; i++) {
			NetworkEdge e = tree[i];
			NetworkVertex v = (NetworkVertex) e.getTail();
			if (v != k) {
				long x = -v.getDemand();
				for (Edge edge : v.getIngoingEdges()) {
					if (edge != e) {
						NetworkEdge ingoingEdge = (NetworkEdge) edge;
						x += ingoingEdge.getFlow();
					}
				}
				for (Edge edge : v.getOutgoingEdges()) {
					if (edge != e) {
						NetworkEdge outgoingEdge = (NetworkEdge) edge;
						x -= outgoingEdge.getFlow();
					}
				}
				e.setFlow(x);
			} else {
				v = (NetworkVertex) e.getHead();
				long x = v.getDemand();
				for (Edge edge : v.getIngoingEdges()) {
					if (edge != e) {
						NetworkEdge ingoingEdge = (NetworkEdge) edge;
						x -= ingoingEdge.getFlow();
					}
				}
				for (Edge edge : v.getOutgoingEdges()) {
					if (edge != e) {
						NetworkEdge outgoingEdge = (NetworkEdge) edge;
						x += outgoingEdge.getFlow();
					}
				}
				e.setFlow(x);
			}
		}
		
		// Set the vertex prices
		vertexPriceDataKey = network.addVertexData("Vertex price");
		k.addData(new Data(0L), vertexPriceDataKey);
		for (Edge edge : k.getOutgoingEdges()) {
			NetworkEdge e = (NetworkEdge) edge;
			long cost = e.getCost();
			e.getHead().addData(new Data(cost), vertexPriceDataKey);
		}
		for (Edge edge : k.getIngoingEdges()) {
			NetworkEdge e = (NetworkEdge) edge;
			long cost = e.getCost();
			e.getTail().addData(new Data(-cost), vertexPriceDataKey);
		}
		
		// Set the reduced cost
		reducedCostDataKey = network.addEdgeData("Reduced cost");
		for (Edge edge : network.getEdges()) {
			NetworkEdge e = (NetworkEdge) edge;
			computeReducedCost(e);
		}
		
		// While entering edge exists
		int i=1;
		while (enteringEdgeExists() && i <= 1) {
			
			// Choose an entering edge e
			NetworkEdge enteringEdge = null;
			for (NetworkEdge e : lEdges.values()) {
				long rc = (Long) e.getData(reducedCostDataKey).getValue();
				if (rc < 0) {
					enteringEdge = e;
					break;
				}
			}
			if (enteringEdge == null) {
				for (NetworkEdge e : uEdges.values()) {
					long rc = (Long) e.getData(reducedCostDataKey).getValue();
					if (rc > 0) {
						enteringEdge = e;
						break;
					}
				}
			}
			if (inDebugMode) {
				System.out.println(network);
				System.out.println("Entering edge: " + enteringEdge);
			}

			// Find the cycle C in T + enteringEdge
			// Find the apex w of the cycle
			// And compute epsilon
			int u = ((NetworkVertex) enteringEdge.getTail()).getId();
			int v = ((NetworkVertex) enteringEdge.getHead()).getId();
			long eps = Long.MAX_VALUE;
			int numberOfEdgesInCircle = 1;
			int numberOfEdgesBetweenApexAndTailOfEnteringEdge = 0;
			while (u != v) {
				if (d[u] > d[v]) {
					NetworkEdge e = tree[u];
					long r;
					if (network.getVertex(u) == e.getHead()) {
						// e is forward edge
						r = e.getCapacity() - e.getFlow();
					} else {
						r = e.getFlow() - e.getLowerBound();
					}
					eps = Math.min(r, eps);
					u = p[u];
					numberOfEdgesBetweenApexAndTailOfEnteringEdge++;
				} else {
					NetworkEdge e = tree[v];
					long r;
					if (network.getVertex(v) == e.getTail()) {
						// e is forward edge
						r = e.getCapacity() - e.getFlow();
					} else {
						r = e.getFlow() - e.getLowerBound();
					}
					eps = Math.min(r, eps);
					v = p[v];
				}
				numberOfEdgesInCircle++;
			}
			int w = u;
			if (inDebugMode) {
				System.out.println("eps = " + eps);
				System.out.println("|C| = " + numberOfEdgesInCircle);
			}
			
			// Update the flow
			NetworkEdge[] circle = new NetworkEdge[numberOfEdgesInCircle];
			int j=numberOfEdgesBetweenApexAndTailOfEnteringEdge-1;
			u = ((NetworkVertex) enteringEdge.getTail()).getId();
			while (u != w) {
				NetworkEdge e = tree[u];
				if (network.getVertex(u) == e.getHead()) {
					// e is forward edge
					e.setFlow(e.getFlow()+eps);
				} else {
					e.setFlow(e.getFlow()-eps);
				}
				u = p[u];
				circle[j] = e;
				j--;
			}
			j = numberOfEdgesBetweenApexAndTailOfEnteringEdge;
			circle[j] = enteringEdge;
			j++;
			// check entering edge and remove it from L or U
			if (enteringEdge.getFlow() == enteringEdge.getLowerBound()) {
				lEdges.remove(enteringEdge.getKey());
				enteringEdge.setFlow(enteringEdge.getFlow()+eps);
			} else {
				uEdges.remove(enteringEdge.getKey());
				enteringEdge.setFlow(enteringEdge.getFlow()-eps);
			}
			v = ((NetworkVertex) enteringEdge.getHead()).getId();
			while (v != w) {
				NetworkEdge e = tree[v];
				if (network.getVertex(v) == e.getTail()) {
					// e is forward edge
					e.setFlow(e.getFlow()+eps);
				} else {
					e.setFlow(e.getFlow()-eps);
				}
				v = p[v];
				circle[j] = e;
				j++;
			}
			if (inDebugMode) {
				for (NetworkEdge e : circle) {
					System.out.println("Updated edge in circle: "+e);
				}
			}
			
			// Find leavingEdge
			NetworkEdge leavingEdge = null;
			for (NetworkEdge e : circle) {
				if (e.getFlow() == e.getLowerBound() || e.getFlow() == e.getCapacity()) {
					leavingEdge = e;
					break;
				}
			}
			if (inDebugMode) {
				System.out.println("Leaving edge: " + leavingEdge);
			}
			
			// Update vertex prices and reduced costs
			// and also update T, L and U
			HashMap<Key, NetworkVertex> subtree = new HashMap<Key, NetworkVertex>();
			// T decomposes into two subtrees, if we delete the leaving edge
			// Let T1 be the subtree containing the root k, T2 := T\T1
			// We save in variable subtree all vertices belonging to T2
			NetworkVertex z = (NetworkVertex) leavingEdge.getTail();
			NetworkVertex y = (NetworkVertex) leavingEdge.getHead();
			if (d[z.getId()] > d[y.getId()]) {
				// Let y be the node located deeper than z in the tree
				y = z;
			}
			subtree.put(y.getKey(), y);
			int m = s[y.getId()];
			while (d[y.getId()] < d[m]) {
				NetworkVertex next = network.getVertex(m);
				subtree.put(next.getKey(), next);
				m = s[m];
			}
			if (inDebugMode) {
				for (NetworkVertex vertex : subtree.values()) {
					System.out.println("Node in T2: " + vertex);
				}
			}
			long change = (Long) enteringEdge.getData(reducedCostDataKey).getValue();
			// Let e = (u,v). If u in T2
			if (subtree.containsKey(enteringEdge.getTail().getKey())) {
				change = -1 * change;
			}
			for (NetworkVertex vertex : subtree.values()) {
				long price = ((Long) vertex.getData(vertexPriceDataKey).getValue()) + change;
				vertex.addData(new Data(price), vertexPriceDataKey);
				for (Edge edge : vertex.getOutgoingEdges()) {
					NetworkEdge e = (NetworkEdge) edge;
					computeReducedCost(e);
				}
				for (Edge edge : vertex.getIngoingEdges()) {
					NetworkEdge e = (NetworkEdge) edge;
					computeReducedCost(e);
				}
			}
			u = ((NetworkVertex) enteringEdge.getTail()).getId();
			v = ((NetworkVertex) enteringEdge.getHead()).getId();
			// Let v be the node located in the subtree T2
			if (subtree.containsKey(enteringEdge.getTail().getKey())) {
				v = u;
				u = ((NetworkVertex) enteringEdge.getHead()).getId();
			}
			tree[v] = enteringEdge; // add entering edge to T
			// update array p
			int x = p[v];
			p[v] = u;
			// recall that y and z are adjacent to the leaving edge
			// and y the node located deeper than z in the tree
			while (y.getId() != v) {
				int tmp = p[x];
				p[x] = v;
				v = x;
				x = tmp;
			}
			if (leavingEdge.getFlow() == leavingEdge.getLowerBound()) {
				lEdges.put(leavingEdge.getKey(), leavingEdge);
			} else {
				uEdges.put(leavingEdge.getKey(), leavingEdge);
			}
			if (inDebugMode) {
				printTreeAndLowerAndUpperEdges();
			}
			
			
			
			
			i++;
		}
		
		// Test optimality
		
		
	}
	
	private static void printTreeAndLowerAndUpperEdges() {
		for (int i=1; i<tree.length; i++) {
			NetworkEdge e = tree[i];
			System.out.println("In T: " + e);
		}
		for (NetworkEdge e : lEdges.values()) {
			System.out.println("In L: " + e);
		}
		for (NetworkEdge e : uEdges.values()) {
			System.out.println("In U: " + e);
		}
		System.out.println("p: " + Arrays.toString(p));
		System.out.println("d: " + Arrays.toString(d));
		System.out.println("s: " + Arrays.toString(s));
	}
	
	private static void computeReducedCost(NetworkEdge e) {
		long yTail = (Long) e.getTail().getData(vertexPriceDataKey).getValue();
		long yHead = (Long) e.getHead().getData(vertexPriceDataKey).getValue();
		long cost = e.getCost();
		e.addData(new Data(cost + yTail - yHead), reducedCostDataKey);
	}
	
	private static void computeVertexPrices(Network network) {
		network.getVertex(0).addData(new Data(0L), vertexPriceDataKey);
		int j = s[0];
		while (j != 0) {
			int i = p[j];
			NetworkEdge e = tree[j];
			// if e = (i,j)
			if (network.getVertex(i) == e.getTail()) {
				long price = ((Long) network.getVertex(i).getData(vertexPriceDataKey).getValue()) + e.getCost();
				network.getVertex(j).addData(new Data(price), vertexPriceDataKey);
			} else { // e = (j,i)
				long price = ((Long) network.getVertex(i).getData(vertexPriceDataKey).getValue()) - e.getCost();
				network.getVertex(j).addData(new Data(price), vertexPriceDataKey);
			}
			System.out.println("i: " + i);
			System.out.println(e);
			j = s[j];
		}
	}
	
	/**
	 * Check if an entering edge exists.
	 * The entering edge is an edge in L with negative reduced cost
	 * or an edge in U with positive reduced cost.
	 * @return <code>true</code> if an entering edge exists, <code>false</code> otherwise.
	 */
	private static boolean enteringEdgeExists() {
		for (NetworkEdge e : lEdges.values()) {
			long rc = (Long) e.getData(reducedCostDataKey).getValue();
			if (rc < 0) {
				return true;
			}
		}
		for (NetworkEdge e : uEdges.values()) {
			long rc = (Long) e.getData(reducedCostDataKey).getValue();
			if (rc > 0) {
				return true;
			}
		}
		return false;
	}
	
	public static void main(String[] args) {
		if (args.length != 2) {
			System.out.println("Usage: java -jar netsimplex.jar <fileinput> <fileoutput>");
			return;
		}
		String fileInput = args[0];
		String fileOutput = args[1];
		Network network;
		try {
			network = NetworkReader.read(fileInput);
			System.out.println("Succesfully read " + fileInput);
			System.out.println("Created:");
			System.out.println(network);
			NetworkSimplex.findMinCostFlow(network);
			NetworkSolutionWriter.write(network, fileOutput);
		} catch (NetworkReaderException e) {
			e.printStackTrace();
		}
	}

}

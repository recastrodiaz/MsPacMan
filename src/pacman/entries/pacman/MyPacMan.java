package pacman.entries.pacman;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import pacman.controllers.Controller;
import pacman.game.Constants.DM;
import pacman.game.Constants.MOVE;
import pacman.game.Game;
import pacman.game.GameView;

/*
 * This is the class you need to modify for your entry. In particular, you need to
 * fill in the getAction() method. Any additional classes you write should either
 * be placed in this package or sub-packages (e.g., game.entries.pacman.mypackage).
 */
public class MyPacMan extends Controller<MOVE> {

	private final static int PILLS_MAX_SEPARATION = 10;
	private final static double ALFA = 1.0;

	private PillClusterManager clusterManager;
	private int currentLevel;

	public MOVE getMove(Game game, long timeDue) {

		int currentPacmanNodeIndex = game.getPacmanCurrentNodeIndex();
		
		if (clusterManager == null || game.getCurrentLevel() != currentLevel) {
			// Init once or when the level changes
			clusterManager = new PillClusterManager(game);
		} else if (game.getPillIndex(currentPacmanNodeIndex) != -1
				|| game.getPowerPillIndex(currentPacmanNodeIndex) != -1) {
			// Only if the node is a pill
			clusterManager.removeElement(game, currentPacmanNodeIndex);
		}

		List<NodeDistance> closestClusters = clusterManager
				.findClosestNodeIndexPerCluster(game, currentPacmanNodeIndex);

		double max = 0;
		NodeDistance biggestClusterNode = null;
		for (NodeDistance node : closestClusters) {
			double v = node.size / (ALFA * node.distance);
			if (max < v) {
				max = v;
				biggestClusterNode = node;
			}
		}

		// DEBUG draw clusters
		clusterManager.drawClusters(game);

		// return the next direction once the closest target has been identified
		return game.getNextMoveTowardsTarget(currentPacmanNodeIndex,
				biggestClusterNode.index, DM.PATH);
	}

	private static class NodeDistance {

		public final int index;
		public final double distance;
		public final int size;

		public NodeDistance(int index, double distance, int size) {
			this.index = index;
			this.distance = distance;
			this.size = size;
		}
	}

	private static class ConnectedComponents {

		private final SortedSet<Integer> elementsIndicesInCluster;

		public ConnectedComponents(Collection<Integer> pillsInCluster) {
			elementsIndicesInCluster = new TreeSet<Integer>(pillsInCluster);
		}

		/**
		 * 
		 * @param elementIndex
		 * @return null if elementIndex is non existent list of new clusters
		 */
		public List<ConnectedComponents> removeElement(Game game,
				int elementIndex) {
			boolean removed = elementsIndicesInCluster.remove(elementIndex);
			if (!removed) {
				return null;
			} else {
				List<ConnectedComponents> newClusters = new ArrayList<ConnectedComponents>();
				int[] neighbours = getNeighbouringPills(game, elementIndex);
				for (int neighbour : neighbours) {
					SortedSet<Integer> newCluster = new TreeSet<Integer>();
					newCluster = createCluster(newCluster, game, neighbour);
					if (!newCluster.isEmpty()) {
						newClusters.add(new ConnectedComponents(newCluster));
					}
				}
				return newClusters;
			}
		}

		private SortedSet<Integer> createCluster(SortedSet<Integer> newCluster,
				Game game, int elementIndex) {
			if (elementsIndicesInCluster.remove(elementIndex)) {
				newCluster.add(elementIndex);
				int[] neighbours = getNeighbouringPills(game, elementIndex);
				for (int neighbour : neighbours) {
					createCluster(newCluster, game, neighbour);
				}
			}
			return newCluster;
		}

		private int[] getNeighbouringPills(Game game, int nodeIndex) {
			int[] neighbours = game.getNeighbouringNodes(nodeIndex);

			int i = 0;
			int nbPillNeighbours = 0;
			for (int neighbourIndex : neighbours) {
				MOVE direction = game.getMoveToMakeToReachDirectNeighbour(
						nodeIndex, neighbourIndex);
				int count = 0;
				while (count < PILLS_MAX_SEPARATION && neighbourIndex != -1
						&& game.getPillIndex(neighbourIndex) == -1
						&& game.getPowerPillIndex(neighbourIndex) == -1) {
					count++;
					neighbourIndex = game.getNeighbour(neighbourIndex,
							direction);
				}
				if (count == PILLS_MAX_SEPARATION || neighbourIndex == -1) {
					neighbours[i++] = -1;
				} else {
					nbPillNeighbours++;
					neighbours[i++] = neighbourIndex;
				}
			}

			i = 0;
			int[] pillNeighbours = new int[nbPillNeighbours];
			for (int neighbourIndex : neighbours) {
				if (neighbourIndex != -1) {
					pillNeighbours[i++] = neighbourIndex;
				}
			}

			// DBEUG draw pill Neighbours
			// GameView.addPoints(game, Color.CYAN, pillNeighbours);

			return pillNeighbours;
		}

		public NodeDistance findClosestNodeIndex(Game game, int indexNodeOrigin) {
			double minDistance = Double.MAX_VALUE;
			int minNodeIndex = -1;
			for (Integer nodeIndex : elementsIndicesInCluster) {
				double distance = game.getDistance(indexNodeOrigin, nodeIndex,
						DM.PATH);
				if (distance < minDistance) {
					minDistance = distance;
					minNodeIndex = nodeIndex;
				}
			}
			if (minNodeIndex != -1) {
				return new NodeDistance(minNodeIndex, minDistance,
						elementsIndicesInCluster.size());
			} else {
				throw new RuntimeException(
						"Min not found: no elements in cluster");
			}
		}

		public void draw(Game game, Color color) {
			int[] ints = new int[elementsIndicesInCluster.size()];
			int i = 0;
			for (int value : elementsIndicesInCluster) {
				ints[i++] = value;
			}
			GameView.addPoints(game, color, ints);
		}
	}

	private static class PillClusterManager {

		private final List<ConnectedComponents> clusters;

		public PillClusterManager(Game game) {
			List<Integer> allActivePills = getAllActivePills(game);
			clusters = new LinkedList<ConnectedComponents>();
			clusters.add(new ConnectedComponents(allActivePills));
		}

		public void drawClusters(Game game) {
			int colorId = 0;
			for (ConnectedComponents cluster : clusters) {
				cluster.draw(game, Color.getHSBColor(
						(float) colorId / clusters.size(), 1, 1));
				colorId++;
			}
		}

		private List<Integer> getAllActivePills(Game game) {

			// get all active pills
			int[] activePills = game.getActivePillsIndices();

			// get all active power pills
			int[] activePowerPills = game.getActivePowerPillsIndices();

			// create a target array that includes all ACTIVE pills and power
			// pills
			List<Integer> targetNodeIndices = new ArrayList<Integer>(
					activePills.length + activePowerPills.length);

			for (int i = 0; i < activePills.length; i++)
				targetNodeIndices.add(activePills[i]);

			for (int i = 0; i < activePowerPills.length; i++)
				targetNodeIndices.add(activePowerPills[i]);

			return targetNodeIndices;
		}

		public void removeElement(Game game, int elementIndex) {
			List<ConnectedComponents> clustersToAdd = new LinkedList<ConnectedComponents>();
			Iterator<ConnectedComponents> it = clusters.iterator();
			while (it.hasNext()) {
				ConnectedComponents cluster = it.next();
				List<ConnectedComponents> newClusters = cluster.removeElement(
						game, elementIndex);
				if (newClusters != null) {
					it.remove();
					clustersToAdd.addAll(newClusters);
				}
			}
			clusters.addAll(clustersToAdd);
		}

		public List<NodeDistance> findClosestNodeIndexPerCluster(Game game,
				int indexNodeOrigin) {
			List<NodeDistance> closestNodes = new ArrayList<NodeDistance>(
					clusters.size());
			for (ConnectedComponents cluster : clusters) {
				NodeDistance closestNode = cluster.findClosestNodeIndex(game,
						indexNodeOrigin);
				closestNodes.add(closestNode);
			}
			return closestNodes;
		}
	}
}
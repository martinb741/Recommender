package i5.las2peer.services.recommender.graphs;

import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;

import i5.las2peer.services.recommender.librec.data.SparseMatrix;

public class GraphBuilder {
	private int k = 10;
	private int mu = 300;
	private SimilarityMeasure similarity = SimilarityMeasure.COSINE_SIMILARITY;
	private SparseMatrix ratingsMatrix;
	private SparseMatrix taggingsMatrix;
	int numUsers;
	int numItems;
	private SparseMatrix userAdjMatrix;
	private SparseMatrix itemAdjMatrix;
	private int graphConstrTime;
	
	public enum SimilarityMeasure{
		PEARSON_CORRELATION, COSINE_SIMILARITY
	}
	
	public void setK(int k){
		this.k = k;
	}
	
	public void setSimilarityMeasure(SimilarityMeasure sim){
		similarity = sim;
	}
	
	public void setRatingData(SparseMatrix ratingsMatrix){
		this.ratingsMatrix = ratingsMatrix;
		numUsers = ratingsMatrix.numRows();
		numItems = ratingsMatrix.numColumns();
	}
	
	public void setTaggingData(SparseMatrix taggingsMatrix){
		this.taggingsMatrix = taggingsMatrix;
		numUsers = ratingsMatrix.numRows();
		numItems = ratingsMatrix.numColumns();
	}
	
	public void buildGraphs(){
		Stopwatch sw = Stopwatch.createStarted();
		
		if(taggingsMatrix != null){
			buildGraphsFromTaggings();
		}
		else if (ratingsMatrix != null){
			buildGraphsFromRatings();
		}
		
		sw.stop();
		graphConstrTime = (int) sw.elapsed(TimeUnit.SECONDS);
	}
	
	public SparseMatrix getUserAdjacencyMatrix(){
		return userAdjMatrix;
	}
	
	public SparseMatrix getItemAdjacencyMatrix(){
		return itemAdjMatrix;
	}
	
	public int getComputationTime(){
		return graphConstrTime;
	}
	
	private void buildGraphsFromRatings() {
		// Construct GFSparseMatrix from ratings matrix and compute TF-IDF value for each element
		GFSparseMatrix userTfidfMatrix = GraphUtils.getTfidfMatrix(ratingsMatrix, true);
		GFSparseMatrix itemTfidfMatrix = GraphUtils.getTfidfMatrix(ratingsMatrix, false);
		
		// Initialize the double[][] arrays that will hold the graphs returned by the Greedy Filtering K-NN algorithm.
		// The structure of these arrays is as follows:
		// Dimensions are [numUsers][k*2] or [numItems][k*2], where e.g. userKnnGraph[u][m*2] and userKnnGraph[u][m*2+1] hold the m-th neighbor of user u
		// e.g. if userKnnGraph[5][8]=17 and kNNGraph[5][9]=0.35 then this means that user 5 has user 17 as neighbor number 4 with similarity 0.35
		// A value of -1 means that there is no neighbor, i.e. the user/item must have less than k neighbors
		double[][] userKnnGraph = new double[numUsers][];
		for (int i=0; i<numUsers; i++) {
			userKnnGraph[i] = new double[k*2];
			for (int j=0; j<k*2; j++) {
				userKnnGraph[i][j] = -1;
			}
		}
		double[][] itemKnnGraph = new double[numItems][];
		for (int i=0; i<numItems; i++) {
			itemKnnGraph[i] = new double[k*2];
			for (int j=0; j<k*2; j++) {
				itemKnnGraph[i][j] = -1;
			}
		}
		
		// Call the graph construction algorithm, storing the resulting graphs in userKnnGraph and itemKnnGraph
		GreedyFiltering.kNNGraphConstruction(userTfidfMatrix, userKnnGraph, k, mu);
		GreedyFiltering.kNNGraphConstruction(itemTfidfMatrix, itemKnnGraph, k, mu);
		
		// Convert userKnnGraph and itemKnnGraph into sparse adjacency matrices
		Table<Integer, Integer, Double> userAdjTable = HashBasedTable.create();
		Table<Integer, Integer, Double> itemAdjTable = HashBasedTable.create();
		Multimap<Integer, Integer> userAdjColMap = HashMultimap.create();
		Multimap<Integer, Integer> itemAdjColMap = HashMultimap.create();
		
		for (int u1 = 0; u1 < numUsers; u1++){
			for (int idx = 0; idx < k*2; idx += 2){
				int u2 = (int) userKnnGraph[u1][idx];
				if (u2 != -1){
					double sim = userKnnGraph[u1][idx+1];
					userAdjTable.put(u1, u2, sim);
					userAdjColMap.put(u2, u1);
				}
			}
		}
		for (int i1 = 0; i1 < numItems; i1++){
			for (int idx = 0; idx < k*2; idx += 2){
				int i2 = (int) itemKnnGraph[i1][idx];
				if (i2 != -1){
					double sim = itemKnnGraph[i1][idx+1];
					itemAdjTable.put(i1, i2, sim);
					itemAdjColMap.put(i2, i1);
				}
			}
		}
		
		userAdjMatrix = new SparseMatrix(numUsers, numUsers, userAdjTable, userAdjColMap);
		itemAdjMatrix = new SparseMatrix(numItems, numItems, itemAdjTable, itemAdjColMap);
		
	}
	
	private void buildGraphsFromTaggings() {
		// TODO Auto-generated method stub
	}

}

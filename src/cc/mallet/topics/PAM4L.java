package cc.mallet.topics;


/* Copyright (C) 2005 Univ. of Massachusetts Amherst, Computer Science Dept.
This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
http://www.cs.umass.edu/~mccallum/mallet
This software is provided under the terms of the Common Public License,
version 1.0, as published by http://www.opensource.org.  For further
information, see the file `LICENSE' included with this distribution. */

import cc.mallet.types.*;
import cc.mallet.util.Randoms;
import java.util.Arrays;
import java.io.*;
import java.text.NumberFormat;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Four Level Pachinko Allocation with MLE learning,
 *  based on Andrew's Latent Dirichlet Allocation.
 * @author David Mimno
 */

public class PAM4L {

    // Parameters
    int numSuperTopics; // Number of topics to be fit
    int numSubTopics;

    double[] alpha;  // Dirichlet(alpha,alpha,...) is the distribution over supertopics
    double alphaSum;
    double[][] subAlphas;
    double[] subAlphaSums;
    double beta;   // Prior on per-topic multinomial distribution over words
    double vBeta;

    // Data
    InstanceList ilist;  // the data field of the instances is expected to hold a FeatureSequence
    int numTypes;
    int numTokens;

    // Gibbs sampling state
    //  (these could be shorts, or we could encode both in one int)
    int[][] superTopics; // indexed by <document index, sequence index>
    int[][] subTopics; // indexed by <document index, sequence index>

    // Per-document state variables
    int[][] superSubCounts; // # of words per <super, sub>
    int[] superCounts; // # of words per <super>
    double[] superWeights; // the component of the Gibbs update that depends on super-topics
    double[] subWeights;   // the component of the Gibbs update that depends on sub-topics
    double[][] superSubWeights; // unnormalized sampling distribution
    double[] cumulativeSuperWeights; // a cache of the cumulative weight for each super-topic

    // Per-word type state variables
    int[][] typeSubTopicCounts; // indexed by <feature index, topic index>
    int[] tokensPerSubTopic; // indexed by <topic index>

    // [for debugging purposes]
    int[] tokensPerSuperTopic; // indexed by <topic index>
    int[][] tokensPerSuperSubTopic;

    // Histograms for MLE
    int[][] superTopicHistograms; // histogram of # of words per supertopic in documents
    //  eg, [17][4] is # of docs with 4 words in sT 17...
    int[][][] subTopicHistograms; // for each supertopic, histogram of # of words per subtopic

    Runtime runtime;
    NumberFormat formatter;

    // output files
    final File superTopicOutput;
    final File subTopicOutput;
    final File superSubWeightOutput;
    final File wordOutput;

    public PAM4L (int superTopics, int subTopics, File superTopicOutput, File subTopicOutput,
                  File superSubWeightOutput, File wordOutput) {
        this (superTopics, subTopics, 50.0, 0.001, superTopicOutput, subTopicOutput,
            superSubWeightOutput, wordOutput);
    }

    public PAM4L (int superTopics, int subTopics, double alphaSum, double beta, File superTopicOutput,
                  File subTopicOutput, File superSubWeightOutput, File wordOutput) {
        this.superTopicOutput = superTopicOutput;
        this.subTopicOutput = subTopicOutput;
        this.superSubWeightOutput = superSubWeightOutput;
        this.wordOutput = wordOutput;

        formatter = NumberFormat.getInstance();
        formatter.setMaximumFractionDigits(5);

        this.numSuperTopics = superTopics;
        this.numSubTopics = subTopics;

        this.alphaSum = alphaSum;
        this.alpha = new double[superTopics];
        Arrays.fill(alpha, alphaSum / numSuperTopics);

        subAlphas = new double[superTopics][subTopics];
        subAlphaSums = new double[superTopics];

        // Initialize the sub-topic alphas to a symmetric dirichlet.
        for (int superTopic = 0; superTopic < superTopics; superTopic++) {
            Arrays.fill(subAlphas[superTopic], 1.0);
        }
        Arrays.fill(subAlphaSums, subTopics);

        this.beta = beta; // We can't calculate vBeta until we know how many word types...

        runtime = Runtime.getRuntime();
    }

    public void estimate(InstanceList documents, int numIterations, int optimizeInterval, int outputModelInterval,
                         Randoms r, String[] genes) throws IOException {
        ilist = documents;
        numTypes = ilist.getDataAlphabet().size ();
        int numDocs = ilist.size();
        System.out.println("numdocs: " + numDocs);
        System.out.println("numwords: " + numTypes);
        Alphabet words = ilist.getAlphabet();

        superTopics = new int[numDocs][];
        subTopics = new int[numDocs][];

        //		Allocate several arrays for use within each document
        //		to cut down memory allocation and garbage collection time

        superSubCounts = new int[numSuperTopics][numSubTopics];
        superCounts = new int[numSuperTopics];
        superWeights = new double[numSuperTopics];
        subWeights = new double[numSubTopics];
        superSubWeights = new double[numSuperTopics][numSubTopics];
        cumulativeSuperWeights = new double[numSuperTopics];

        typeSubTopicCounts = new int[numTypes][numSubTopics];
        tokensPerSubTopic = new int[numSubTopics];
        tokensPerSuperTopic = new int[numSuperTopics];
        tokensPerSuperSubTopic = new int[numSuperTopics][numSubTopics];
        vBeta = beta * numTypes;

        long startTime = System.currentTimeMillis();

        int maxTokens = 0;

        //		Initialize with random assignments of tokens to topics
        //		and finish allocating this.topics and this.tokens

        int superTopic, subTopic, seqLen;

        for (int di = 0; di < numDocs; di++) {

            FeatureSequence fs = (FeatureSequence) ilist.get(di).getData();

            seqLen = fs.getLength();
            if (seqLen > maxTokens) {
                maxTokens = seqLen;
            }

            numTokens += seqLen;
            superTopics[di] = new int[seqLen];
            subTopics[di] = new int[seqLen];

            // Randomly assign tokens to topics
            for (int si = 0; si < seqLen; si++) {
                // Random super-topic
                superTopic = r.nextInt(numSuperTopics);
                superTopics[di][si] = superTopic;
                tokensPerSuperTopic[superTopic]++;

                // Random sub-topic
                subTopic = r.nextInt(numSubTopics);
                subTopics[di][si] = subTopic;

                // For the sub-topic, we also need to update the
                //  word type statistics
                typeSubTopicCounts[ fs.getIndexAtPosition(si) ][subTopic]++;
                tokensPerSubTopic[subTopic]++;

                tokensPerSuperSubTopic[superTopic][subTopic]++;
            }
        }

        System.out.println("max tokens: " + maxTokens);

        //		These will be initialized at the first call to
        //		clearHistograms() in the loop below.

        superTopicHistograms = new int[numSuperTopics][maxTokens + 1];
        subTopicHistograms = new int[numSuperTopics][numSubTopics][maxTokens + 1];

        //		Finally, start the sampler!

        for (int iterations = 0; iterations < numIterations; iterations++) {
            long iterationStart = System.currentTimeMillis();

            clearHistograms();
            sampleTopicsForAllDocs (r);

            // There are a few things we do on round-numbered iterations
            //  that don't make sense if this is the first iteration.

            if (iterations > 0) {
                if (outputModelInterval != 0 && iterations % outputModelInterval == 0) {
                    System.out.println("Writting output...");
                    printState();
                }
                if (optimizeInterval != 0 && iterations % optimizeInterval == 0) {
                    long optimizeTime = System.currentTimeMillis();
                    for (superTopic = 0; superTopic < numSuperTopics; superTopic++) {
                        learnParameters(subAlphas[superTopic],
                                subTopicHistograms[superTopic],
                                superTopicHistograms[superTopic]);
                        subAlphaSums[superTopic] = 0.0;
                        for (subTopic = 0; subTopic < numSubTopics; subTopic++) {
                            subAlphaSums[superTopic] += subAlphas[superTopic][subTopic];
                        }
                    }
	                System.out.println("[optimize:" + (System.currentTimeMillis() - optimizeTime) + "]");
                }
            }


	        System.out.println ("<" + iterations + "> ");
            System.out.flush();
        }

        long seconds = Math.round((System.currentTimeMillis() - startTime)/1000.0);
        long minutes = seconds / 60;	seconds %= 60;
        long hours = minutes / 60;	minutes %= 60;
        long days = hours / 24;	hours %= 24;
        System.out.print ("\nTotal time: ");
        if (days != 0) { System.out.print(days); System.out.print(" days "); }
        if (hours != 0) { System.out.print(hours); System.out.print(" hours "); }
        if (minutes != 0) { System.out.print(minutes); System.out.print(" minutes "); }
        System.out.print(seconds); System.out.println(" seconds");
        printState();

        //		124.5 seconds
        //		144.8 seconds after using FeatureSequence instead of tokens[][] array
        //		121.6 seconds after putting "final" on FeatureSequence.getIndexAtPosition()
        //		106.3 seconds after avoiding array lookup in inner loop with a temporary variable

    }

    private void clearHistograms() {
        for (int superTopic = 0; superTopic < numSuperTopics; superTopic++) {
            Arrays.fill(superTopicHistograms[superTopic], 0);
            for (int subTopic = 0; subTopic < numSubTopics; subTopic++) {
                Arrays.fill(subTopicHistograms[superTopic][subTopic], 0);
            }
        }
    }

    /** Use the fixed point iteration described by Tom Minka. */
    public void learnParameters(double[] parameters, int[][] observations, int[] observationLengths) throws FileNotFoundException {
        int i, k;

        double parametersSum = 0;

        //		Initialize the parameter sum

        for (k=0; k < parameters.length; k++) {
            parametersSum += parameters[k];
        }

        double oldParametersK;
        double currentDigamma;
        double denominator;

        int[] histogram;

        int nonZeroLimit;
        int[] nonZeroLimits = new int[observations.length];
        Arrays.fill(nonZeroLimits, -1);

        //		The histogram arrays go up to the size of the largest document,
        //		but the non-zero values will almost always cluster in the low end.
        //		We avoid looping over empty arrays by saving the index of the largest
        //		non-zero value.

        for (i=0; i<observations.length; i++) {
            histogram = observations[i];
            for (k = 0; k < histogram.length; k++) {
                if (histogram[k] > 0) {
                    nonZeroLimits[i] = k;
                }
            }
        }

        for (int iteration=0; iteration<200; iteration++) {

            // Calculate the denominator
            denominator = 0;
            currentDigamma = 0;

            // Iterate over the histogram:
            for (i=1; i<observationLengths.length; i++) {
                currentDigamma += 1 / (parametersSum + i - 1);
                denominator += observationLengths[i] * currentDigamma;
            }

            parametersSum = 0;

            for (k=0; k<parameters.length; k++) {

                // What's the largest non-zero element in the histogram?
                nonZeroLimit = nonZeroLimits[k];

                // If there are no tokens assigned to this super-sub pair
                //  anywhere in the corpus, bail.

                if (nonZeroLimit == -1) {
                    parameters[k] = 0.000001;
                    parametersSum += 0.000001;
                    continue;
                }

                oldParametersK = parameters[k];
                parameters[k] = 0;
                currentDigamma = 0;

                histogram = observations[k];

                for (i=1; i <= nonZeroLimit; i++) {
                    currentDigamma += 1 / (oldParametersK + i - 1);
                    parameters[k] += histogram[i] * currentDigamma;
                }

                parameters[k] *= oldParametersK / denominator;

                if (Double.isNaN(parameters[k])) {
                    System.out.println("parametersK *= " +
                            oldParametersK + " / " +
                            denominator);
                    for (i=1; i < histogram.length; i++) {
                        System.out.print(histogram[i] + " ");
                    }
                    System.out.println();
                }

                parametersSum += parameters[k];
            }
        }
    }

    /* One iteration of Gibbs sampling, across all documents. */
    private void sampleTopicsForAllDocs (Randoms r)
    {
//		Loop over every word in the corpus
        for (int di = 0; di < superTopics.length; di++) {

            sampleTopicsForOneDoc ((FeatureSequence)ilist.get(di).getData(),
                    superTopics[di], subTopics[di], r);
        }
    }

    private void sampleTopicsForOneDoc (FeatureSequence oneDocTokens,
                                        int[] superTopics, // indexed by seq position
                                        int[] subTopics,
                                        Randoms r) {

//		long startTime = System.currentTimeMillis();

        int[] currentTypeSubTopicCounts;
        int[] currentSuperSubCounts;
        double[] currentSuperSubWeights;
        double[] currentSubAlpha;

        int type, subTopic, superTopic;
        double currentSuperWeight, cumulativeWeight, sample;

        int docLen = oneDocTokens.getLength();

        for (int t = 0; t < numSuperTopics; t++) {
            Arrays.fill(superSubCounts[t], 0);
        }

        Arrays.fill(superCounts, 0);


//		populate topic counts
        for (int si = 0; si < docLen; si++) {
            superSubCounts[ superTopics[si] ][ subTopics[si] ]++;
            superCounts[ superTopics[si] ]++;
        }

//		Iterate over the positions (words) in the document

        for (int si = 0; si < docLen; si++) {

            type = oneDocTokens.getIndexAtPosition(si);
            superTopic = superTopics[si];
            subTopic = subTopics[si];

            // Remove this token from all counts
            superSubCounts[superTopic][subTopic]--;
            superCounts[superTopic]--;
            typeSubTopicCounts[type][subTopic]--;
            tokensPerSuperTopic[superTopic]--;
            tokensPerSubTopic[subTopic]--;
            tokensPerSuperSubTopic[superTopic][subTopic]--;

            // Build a distribution over super-sub topic pairs
            //   for this token

            // Clear the data structures
            for (int t = 0; t < numSuperTopics; t++) {
                Arrays.fill(superSubWeights[t], 0.0);
            }
            Arrays.fill(superWeights, 0.0);
            Arrays.fill(subWeights, 0.0);
            Arrays.fill(cumulativeSuperWeights, 0.0);

            // Avoid two layer (ie [][]) array accesses
            currentTypeSubTopicCounts = typeSubTopicCounts[type];

            // The conditional probability of each super-sub pair is proportional
            //  to an expression with three parts, one that depends only on the
            //  super-topic, one that depends only on the sub-topic and the word type,
            //  and one that depends on the super-sub pair.

            // Calculate each of the super-only factors first

            for (superTopic = 0; superTopic < numSuperTopics; superTopic++) {
                superWeights[superTopic] = ((double) superCounts[superTopic] + alpha[superTopic]) /
                        ((double) superCounts[superTopic] + subAlphaSums[superTopic]);
            }

            // Next calculate the sub-only factors

            for (subTopic = 0; subTopic < numSubTopics; subTopic++) {
                subWeights[subTopic] = ((double) currentTypeSubTopicCounts[subTopic] + beta) /
                        ((double) tokensPerSubTopic[subTopic] + vBeta);
            }

            // Finally, put them together

            cumulativeWeight = 0.0;

            for (superTopic = 0; superTopic < numSuperTopics; superTopic++) {
                currentSuperSubWeights = superSubWeights[superTopic];
                currentSuperSubCounts = superSubCounts[superTopic];
                currentSubAlpha = subAlphas[superTopic];
                currentSuperWeight = superWeights[superTopic];

                for (subTopic = 0; subTopic < numSubTopics; subTopic++) {
                    currentSuperSubWeights[subTopic] =
                            currentSuperWeight *
                                    subWeights[subTopic] *
                                    ((double) currentSuperSubCounts[subTopic] + currentSubAlpha[subTopic]);
                    cumulativeWeight += currentSuperSubWeights[subTopic];
                }

                // weight of a super topic is the sum of weights of its sub topics
                cumulativeSuperWeights[superTopic] = cumulativeWeight;
            }

            // Sample a topic assignment from this distribution
            sample = r.nextUniform() * cumulativeWeight;

            // Go over the row sums to find the super-topic...
            superTopic = 0;
            while (sample > cumulativeSuperWeights[superTopic]) {
                superTopic++;
            }

            // Now read across to find the sub-topic
            currentSuperSubWeights = superSubWeights[superTopic];
            cumulativeWeight = cumulativeSuperWeights[superTopic] -
                    currentSuperSubWeights[0];

            // Go over each sub-topic until the weight is LESS than
            //  the sample. Note that we're subtracting weights
            //  in the same order we added them...
            subTopic = 0;
            while (sample < cumulativeWeight) {
                subTopic++;
                cumulativeWeight -= currentSuperSubWeights[subTopic];
            }

            // Save the choice into the Gibbs state

            superTopics[si] = superTopic;
            subTopics[si] = subTopic;

            // Put the new super/sub topics into the counts

            superSubCounts[superTopic][subTopic]++;
            superCounts[superTopic]++;
            typeSubTopicCounts[type][subTopic]++;
            tokensPerSuperTopic[superTopic]++;
            tokensPerSubTopic[subTopic]++;
            tokensPerSuperSubTopic[superTopic][subTopic]++;
        }

        //		Update the topic count histograms
        //		for dirichlet estimation

        for (superTopic = 0; superTopic < numSuperTopics; superTopic++) {

            superTopicHistograms[superTopic][ superCounts[superTopic] ]++;
            currentSuperSubCounts = superSubCounts[superTopic];

            for (subTopic = 0; subTopic < numSubTopics; subTopic++) {
                subTopicHistograms[superTopic][subTopic][ currentSuperSubCounts[subTopic] ]++;
            }
        }
    }


    class IDSorter implements Comparable {
        int wi; double p;
        public IDSorter (int wi, double p) { this.wi = wi; this.p = p; }
        public final int compareTo (Object o2) {
            if (p > ((IDSorter) o2).p)
                return -1;
            else if (p == ((IDSorter) o2).p)
                return 0;
            else return 1;
        }
    }

    private int[][] getSuperTopics() {
        return superTopics;
    }

    private int[][] getSubTopics() {
        return subTopics;
    }

    private int getNumSuperTopics() {
        return numSuperTopics;
    }

    private int getNumSubTopics() {
        return numSubTopics;
    }

    private int getNumDocuments() {
        return this.ilist.size();
    }

	private double[][] getSuperSubWeights() {
		return superSubWeights;
	}

    public static double[] calculateDirichletDist(int[] assignmentsOfWordTopics, int numOfTopics) {
        int[] counts = new int[numOfTopics];
        int wordCount = assignmentsOfWordTopics.length;
        Arrays.fill(counts, 0);
        for (int i: assignmentsOfWordTopics) {
            counts[i]++;
        }

        return Arrays.stream(counts)
            .mapToDouble(i -> ((double) i) / wordCount)
            .toArray();
    }

    private String distToString(double[] dist) {
        return Arrays.stream(dist)
            .mapToObj(String::valueOf)
            .collect(Collectors.joining(","));
    }

    public static String distToString(int[] dist) {
        String[] distString = Arrays.stream(dist)
            .mapToObj(String::valueOf)
            .toArray(String[]::new);
        return String.join(",", distString);
    }


    private void printSuperSubWeights() throws FileNotFoundException {
        PrintWriter printWriter = new PrintWriter(this.superSubWeightOutput);
        double[][] superSubWeights = getSuperSubWeights();
        int numSuperTopics = getNumSuperTopics();

        for (int i = 0; i < numSuperTopics; i++) {
            printWriter.println(distToString(superSubWeights[i]));
        }

        printWriter.close();
    }

    private void printTopics(boolean isSuper) throws FileNotFoundException {
        int[][] counts = (isSuper) ? getSuperTopics() : getSubTopics();
        int numTopics = (isSuper) ? getNumSuperTopics() : getNumSubTopics();
        int numDocs = getNumDocuments();
        File file = (isSuper) ? this.superTopicOutput : this.subTopicOutput;
        PrintWriter printWriter = new PrintWriter(file);


        System.out.println("nDocs: " + numDocs);

        for (int i = 0; i < numDocs; i++) {
            double[] dist = calculateDirichletDist(counts[i], numTopics);
            printWriter.println(distToString(dist));
        }

        printWriter.close();
    }

    private void printWordMatrix() throws FileNotFoundException {
        PrintWriter printWriter = new PrintWriter(this.wordOutput);

        System.out.println("writing" + typeSubTopicCounts.length + "words");
        System.out.println(typeSubTopicCounts[0]);

        for (int i = 0; i < this.typeSubTopicCounts.length; i++) {
            String string = distToString(this.typeSubTopicCounts[i]);
            printWriter.println(string);
        }

        printWriter.close();

    }

    private void printState() throws FileNotFoundException {
        this.printTopics(true);
        this.printTopics(false);
        this.printSuperSubWeights();
        this.printWordMatrix();
    }



}

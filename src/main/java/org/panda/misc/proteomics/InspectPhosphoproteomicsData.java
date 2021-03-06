package org.panda.misc.proteomics;

import org.panda.resource.GO;
import org.panda.resource.tcga.ExpressionReader;
import org.panda.utility.ArrayUtil;
import org.panda.utility.Tuple;
import org.panda.utility.statistics.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

/**
 * @author Ozgun Babur
 */
public class InspectPhosphoproteomicsData
{
	static final String DIR = "/home/babur/Documents/RPPA/TCGA/PNNL/";
	static final String DATA_FILE = DIR + "PNLL-causality-formatted.txt";
	static final String GROUPS_FILE = DIR + "subtypes/Subtype-Mesenchymal/parameters.txt";
	static final String PLOT = DIR + "plot.txt";
	static String[] control;
	static String[] test;


	static Map<String, Map<String, Double>> data;

	public static void main(String[] args) throws IOException
	{
//		displayDistributionsForGene("/home/babur/Documents/Analyses/CPTACBreastCancer/CPTAC-TCGA-BRCA-data.txt", "BRAF");
//		displayCorrelationForPeptides("/home/babur/Documents/Analyses/CPTACBreastCancer/CPTAC-TCGA-BRCA-data.txt", "ATM-S1981", "BRCA1-S1524");
//		printSampleGroupsSeparatedByAPeptide("/home/babur/Documents/Analyses/CPTACBreastCancer/CPTAC-TCGA-BRCA-data.txt", "BRAF-S151");

//		printCorrelationsOfSpecificGenes("/home/babur/Documents/Analyses/CPTACBreastCancer77/CPTAC-TCGA-BRCA-data.txt", "DYRK1B", "/home/babur/Documents/Temp/BRCA");

//		loadData();
//		loadGroups();
//		writeDistribution(PLOT);

		printSubtypeSpecificExpression();
	}

	static void writeDistribution(String file) throws IOException
	{
		List<Double> pvals = new ArrayList<>();
		Map<String, Double> pMap = new HashMap<>();
		Map<String, Double> spMap = new HashMap<>();

		for (String id : data.keySet())
		{
			double[] c = getValues(id, control);
			double[] t = getValues(id, test);

//			double p = getTTestSignedPValue(c, t);
			double p = getNanImbalanceSignedPValue(c, t);

			if (!Double.isNaN(p))
			{
				spMap.put(id, p);
				pvals.add(Math.abs(p));
				pMap.put(id, Math.abs(p));
			}
		}

		UniformityChecker.plot(pvals, file);

//		Map<String, Double> qVals = FDR.getQVals(pMap, null);
//		qVals.keySet().stream().filter(s -> qVals.get(s) <= 0.5)
//			.sorted((s1, s2) -> qVals.get(s1).compareTo(qVals.get(s2))).forEach(id ->
//			System.out.println(id + "\t" + spMap.get(id) + "\t" + qVals.get(id)));
	}

	static double logTransform(double p)
	{
		int sign = (int) Math.signum(p);
		p = Math.abs(p);
		return -Math.log(p) * sign;
	}

	static double getTTestSignedPValue(double[] control, double[] test)
	{
		double[] c = ArrayUtil.trimNaNs(control);
		double[] t = ArrayUtil.trimNaNs(test);

//		if (c.length < 13 || t.length < 13) return Double.NaN;

		double p = TTest.getPValOfMeanDifference(c, t);
		if (!Double.isNaN(p))
		{
			double dif = Summary.mean(t) - Summary.mean(c);
			if (dif < 0) p = -p;
		}
		return p;
	}

	static double getNanRatioDiff(double[] control, double[] test)
	{
		int tN = ArrayUtil.countNaNs(test);
		int cN = ArrayUtil.countNaNs(control);

		return (cN / (double) control.length) - (tN / (double) test.length);
	}

	static double getNanImbalanceSignedPValue(double[] control, double[] test)
	{
		int size = control.length + test.length;
		int tN = ArrayUtil.countNaNs(test);
		int cN = ArrayUtil.countNaNs(control);
		int featured = tN + cN;
		double pC = ChiSquare.testDependence(size, featured, control.length, cN);
//		double pC = FishersExactTest.calcEnrichmentPval(size, featured, control.length, cN);
//		double pT = FishersExactTest.calcEnrichmentPval(size, featured, test.length, tN);
//		int sign = pC < pT ? -1 : 1;

//		double p = Math.min(pT, pC);
//		p *= /*2 **/ sign;
		return pC;
	}



	static double[] getValues(String id, String[] samples)
	{
		double[] v = new double[samples.length];
		for (int i = 0; i < v.length; i++)
		{
			v[i] = data.get(id).get(samples[i]);
		}
		return v;
	}

	static void loadGroups() throws IOException
	{
		Set<String> cSet = new HashSet<>();
		Set<String> tSet = new HashSet<>();
		Files.lines(Paths.get(GROUPS_FILE)).map(l -> l.split(" = ")).forEach(t ->
		{
			if (t[0].equals("control-value-column")) cSet.add(t[1]);
			if (t[0].equals("test-value-column")) tSet.add(t[1]);
		});

		Set<String> samples = getSamplesWithData();
		cSet.retainAll(samples);
		tSet.retainAll(samples);

		control = new ArrayList<>(cSet).toArray(new String[cSet.size()]);
		test = new ArrayList<>(tSet).toArray(new String[tSet.size()]);
	}

	static void loadData() throws IOException
	{
		data = new HashMap<>();
		Set<String> samples = getSamplesWithData();

		assert samples.contains("TCGA-13-1484");
		assert !samples.contains("TCGA-13-1483");
		assert samples.size() == 69;

		String[] header = readHeader();

		Files.lines(Paths.get(DATA_FILE)).skip(1).map(l -> l.split("\t")).filter(t -> !t[2].isEmpty()).forEach(t ->
		{
			String id = t[0];
			data.put(id, new HashMap<>());
			Map<String, Double> map = data.get(id);

			for (int i = 4; i < t.length; i++)
			{
				if (samples.contains(header[i]))
				{
					map.put(header[i], Double.valueOf(t[i]));
				}
			}
		});
	}

	static void randomizeData()
	{
		for (String id : data.keySet())
		{
			Map<String, Double> map = data.get(id);
			List<Double> list = map.values().stream().collect(Collectors.toList());
			Collections.shuffle(list);
			int i = 0;
			for (String sample : new HashSet<>(map.keySet()))
			{
				map.put(sample, list.get(i++));
			}
		}
	}

	static Set<String> getSamplesWithData() throws IOException
	{
		String[] header = readHeader();
		int[] cnt = new int[header.length];

		Files.lines(Paths.get(DATA_FILE)).skip(1).map(l -> l.split("\t")).filter(t -> !t[2].isEmpty()).forEach(t ->
		{
			for (int i = 4; i < cnt.length; i++)
			{
				if (t[i].equals("NaN")) cnt[i]++;
			}
		});

		int max = cnt[4];
		
		Set<String> samples = new HashSet<>();

		for (int i = 5; i < cnt.length; i++)
		{
			if (cnt[i] < max) samples.add(header[i]);
		}
		return samples;
	}

	private static String[] readHeader() throws IOException
	{
		return Files.lines(Paths.get(DATA_FILE)).findFirst().get().split("\t");
	}

	static void displayDistributionsForGene(String protFile, String gene) throws IOException
	{
		Files.lines(Paths.get(protFile)).skip(1).filter(l -> l.startsWith(gene)).map(l -> l.split("\t"))
			.filter(t -> t.length > 4 && t[1].equals(gene)).forEach(t ->
		{
			List<Double> list = new ArrayList<>();
			for (int i = 4; i < t.length; i++)
			{
				Double d = Double.valueOf(t[i]);
				if (!d.isNaN()) list.add(d);
			}
			double[] vals = ArrayUtil.convertToBasicDoubleArray(list);

			KernelDensityPlot.plot(t[0], vals);
		});
	}
	static void displayCorrelationForPeptides(String protFile, String pep1, String pep2) throws IOException
	{
		Map<String, double[]> map = new HashMap<>();

		Files.lines(Paths.get(protFile)).skip(1).filter(l -> l.startsWith(pep1) || l.startsWith(pep2)).map(l -> l.split("\t"))
			.forEach(t ->
		{
			List<Double> list = new ArrayList<>();
			for (int i = 4; i < t.length; i++)
			{
				Double d = Double.valueOf(t[i]);
				list.add(d);
			}
			double[] vals = ArrayUtil.convertToBasicDoubleArray(list);

			map.put(t[0], vals);
		});

		double[][] vv = ArrayUtil.trimNaNs(map.get(pep1), map.get(pep2));

		// print the numbers to plot
//		for (int i = 0; i < vv[0].length; i++)
//		{
//			System.out.println(vv[0][i] + "\t" + vv[1][i]);
//		}

		System.out.println("correlation = " + Correlation.pearson(vv[0], vv[1]));
	}

	static void printSampleGroupsSeparatedByAPeptide(String protFile, String pep) throws IOException
	{
		String[] header = Files.lines(Paths.get(protFile)).findFirst().get().split("\t");
		String[] tok = Files.lines(Paths.get(protFile)).skip(1).filter(l -> l.startsWith(pep)).map(l -> l.split("\t"))
			.filter(t -> t[0].equals(pep)).findFirst().get();

		List<Double> list = new ArrayList<>();
		for (int i = 4; i < tok.length; i++)
		{
			Double d = Double.valueOf(tok[i]);
			if (!d.isNaN()) list.add(d);
		}

		Collections.sort(list);

		double b1 = list.get(list.size() / 3);
		double b2 = list.get((2 * list.size()) / 3);

		for (int i = 4; i < header.length; i++)
		{
			Double d = Double.valueOf(tok[i]);
			if (!d.isNaN())
			{
				if (d <= b1) System.out.println("control-value-column = " + header[i]);
				else if (d >= b2) System.out.println("test-value-column = " + header[i]);
			}
		}
	}

	public static void printCorrelationsOfSpecificGenes(String protFile, String gene, String outDir) throws IOException
	{
		// get IDs of the rows that belong to this gene
		Set<String> ids = Files.lines(Paths.get(protFile)).skip(1)
			.filter(l -> l.substring(0, l.indexOf("\t")).contains(gene))
			.map(l -> l.split("\t"))
			.filter(t -> Arrays.asList(t[1].split(" ")).contains(gene)).map(t -> t[0])
			.collect(Collectors.toSet());

		Map<String, double[]> map = new HashMap<>();
		Files.lines(Paths.get(protFile)).skip(1).map(l -> l.split("\t")).forEach(t ->
		{
			List<Double> list = new ArrayList<>();
			for (int i = 4; i < t.length; i++)
			{
				Double d = Double.valueOf(t[i]);
				list.add(d);
			}
			double[] vals = ArrayUtil.convertToBasicDoubleArray(list);

			map.put(t[0], vals);
		});

		for (String id1 : ids)
		{
			Map<Tuple, Double> corMap = new HashMap<>();
			Map<Tuple, String> tupToID = new HashMap<>();

			BufferedWriter writer = Files.newBufferedWriter(Paths.get(outDir + "/" + id1 + "-correlations.txt"));
			writer.write("Pep1\tPep2\tCorrelation\tP-value");

			for (String id2 : map.keySet())
			{
				if (id1.equals(id2)) continue;

				double[][] vv = ArrayUtil.trimNaNs(map.get(id1), map.get(id2));
				Tuple cor = Correlation.pearson(vv[0], vv[1]);
				if (!cor.isNaN())
				{
					corMap.put(cor, cor.p);
					tupToID.put(cor, id2);
				}
			}
			List<Tuple> select = FDR.select(corMap, null, 0.01);
			for (Tuple tup : select)
			{
				writer.write("\n" + id1 + "\t" + tupToID.get(tup) + "\t" + tup.v + "\t" + tup.p);
			}
			writer.close();
		}
	}

	public static void printSubtypeSpecificExpression() throws IOException
	{
		String protFile = "/home/ozgun/Analyses/CausalPath-paper/CPTAC-BRCA/CPTAC-TCGA-BRCA-data-77.txt";

		Map<String, String> sampleToSubtype = Files.lines(
			Paths.get("/home/ozgun/Analyses/CausalPath-paper/CPTAC-BRCA/CPTAC_BC_SupplementaryTable01.csv"))
			.skip(1).map(l -> l.split("\t")).collect(Collectors.toMap(t -> t[1], t-> t[5]));

		String focus = "Positive";
		Set<String> genes = GO.get().getGenes("GO:0038023");

		String[] header = Files.lines(Paths.get(protFile)).findFirst().get().split("\t");

		Map<String, Double> pvals = new HashMap<>();
		Map<String, Double> diffs = new HashMap<>();

		Files.lines(Paths.get(protFile)).skip(1).map(l -> l.split("\t")).filter(t -> genes.contains(t[1])).forEach(t ->
		{
			String id = t[0];
			List<Double> ctrlList = new ArrayList<>();
			List<Double> testList = new ArrayList<>();
			for (int i = 4; i < t.length; i++)
			{
				if (t[i].equals("NaN")) continue;

				List<Double> list = sampleToSubtype.get(header[i]).equals(focus) ? testList : ctrlList;
				list.add(Double.valueOf(t[i]));
			}

			double[] ctrl = ArrayUtil.convertToBasicDoubleArray(ctrlList);
			double[] test = ArrayUtil.convertToBasicDoubleArray(testList);

			if (ctrl.length < 3 || test.length < 3) return;

			double diff = Summary.mean(test) - Summary.mean(ctrl);
			double pval = TTest.getPValOfMeanDifference(ctrl, test);

			pvals.put(id, pval);
			diffs.put(id, diff);
		});

		List<String> select = FDR.select(pvals, null, 0.1);

		for (String id : select)
		{
			System.out.println(id + "\t" + pvals.get(id) + "\t" + diffs.get(id));
		}

		// check if similar changes happen in RNAseq

		pvals.clear();
		diffs.clear();

		Set<String> selGenes = select.stream().filter(g -> !g.contains("-")).collect(Collectors.toSet());

		ExpressionReader er = new ExpressionReader("/home/ozgun/Data/TCGA/BRCA/expression.txt", selGenes, 12);
		Set<String> expSamples = er.getSamples();
		expSamples.retainAll(sampleToSubtype.keySet());

		String[] ctrlSamples = sampleToSubtype.keySet().stream().filter(s -> !sampleToSubtype.get(s).equals(focus)).toArray(String[]::new);
		String[] testSamples = sampleToSubtype.keySet().stream().filter(s ->  sampleToSubtype.get(s).equals(focus)).toArray(String[]::new);

		selGenes.forEach(gene ->
		{
			double[] ctrl = er.getGeneAlterationArray(gene, ctrlSamples);
			double[] test = er.getGeneAlterationArray(gene, testSamples);

			if (ctrl.length < 3 || test.length < 3) return;

			double diff = Summary.mean(test) - Summary.mean(ctrl);
			double pval = TTest.getPValOfMeanDifference(ctrl, test);

			pvals.put(gene, pval);
			diffs.put(gene, diff);
		});

		select = FDR.select(pvals, null, 0.1);

		System.out.println("\n");
		for (String id : select)
		{
			System.out.println(id + "\t" + pvals.get(id) + "\t" + diffs.get(id));
		}
	}
}
